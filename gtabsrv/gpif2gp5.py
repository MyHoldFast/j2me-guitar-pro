import io
import re
import zipfile
import xml.etree.ElementTree as ET

import guitarpro as gp

from tabmeta import gpif

VALUES = {'Whole': 1, 'Half': 2, 'Quarter': 4, 'Eighth': 8, '16th': 16, '32nd': 32, '64th': 64, '128th': 64}
DRUMS = {}
for midi, el, var in ((36, 0, 0), (37, 1, 2), (38, 1, 0), (41, 5, 0), (42, 10, 0), (43, 6, 0), (44, 11, 0), (45, 7, 0),
                      (46, 10, 2), (47, 8, 0), (48, 9, 0), (49, 12, 0), (51, 15, 0), (52, 16, 0), (53, 15, 2), (55, 14, 0),
                      (56, 3, 0), (57, 13, 0), (59, 15, 1)):
    DRUMS.setdefault((el, var), midi)
SLIDES = ((1, gp.SlideType.shiftSlideTo), (2, gp.SlideType.legatoSlideTo), (4, gp.SlideType.outDownwards),
          (8, gp.SlideType.outUpwards), (16, gp.SlideType.intoFromBelow), (32, gp.SlideType.intoFromAbove))
DYN = {'PPP': 15, 'PP': 31, 'P': 47, 'MP': 63, 'MF': 79, 'F': 95, 'FF': 111, 'FFF': 127}
TEMPO_UNIT = {1: 0.5, 2: 1.0, 3: 1.5, 4: 2.0, 5: 3.0}


def load(data):
    if data[:2] == b'PK':
        return zipfile.ZipFile(io.BytesIO(data)).read('Content/score.gpif')
    x = gpif(data)
    if x is None:
        raise ValueError('score.gpif not found')
    return x


def text(el, path, default=''):
    e = el.find(path) if el is not None else None
    return e.text.strip() if e is not None and e.text else default


def ints(s):
    return [int(v) for v in s.split()] if s else []


def props(el):
    r = {}
    p = el.find('Properties') if el is not None else None
    if p is not None:
        for q in p.findall('Property'):
            r[q.get('name')] = q
    return r


def pval(p, name, tag, default=None):
    q = p.get(name)
    if q is None:
        return default
    v = text(q, tag)
    return v if v != '' else default


def table(root, section, tag):
    s = root.find(section)
    return {int(e.get('id')): e for e in s.findall(tag)} if s is not None else {}


BAD_XML = re.compile(rb'[\x00-\x08\x0b\x0c\x0e-\x1f]')


class Conv:
    def __init__(self, xml):
        xml = BAD_XML.sub(b'', xml)
        try:
            self.root = ET.fromstring(xml)
        except ET.ParseError:
            self.root = ET.fromstring(re.sub(r'^<\?xml[^>]*\?>', '', xml.decode('cp1252', 'replace')).encode('utf-8'))
        r = self.root
        self.bars = table(r, 'Bars', 'Bar')
        self.voices = table(r, 'Voices', 'Voice')
        self.beats = table(r, 'Beats', 'Beat')
        self.notes = table(r, 'Notes', 'Note')
        self.rhythms = table(r, 'Rhythms', 'Rhythm')
        self.masters = r.find('MasterBars').findall('MasterBar')
        self.tracks = r.find('Tracks').findall('Track')

    def song(self):
        r = self.root
        song = gp.Song()
        song.title = text(r, 'Score/Title')
        song.subtitle = text(r, 'Score/SubTitle')
        song.artist = text(r, 'Score/Artist')
        song.album = text(r, 'Score/Album')
        song.words = text(r, 'Score/Words')
        song.music = text(r, 'Score/Music')
        song.copyright = text(r, 'Score/Copyright')
        song.tab = text(r, 'Score/Tabber')
        song.instructions = text(r, 'Score/Instructions')
        self.tempos = {}
        for a in r.findall('MasterTrack/Automations/Automation'):
            if text(a, 'Type') != 'Tempo':
                continue
            v = text(a, 'Value').split()
            bpm = float(v[0]) * TEMPO_UNIT.get(int(v[1]) if len(v) > 1 else 2, 1.0)
            self.tempos.setdefault(int(text(a, 'Bar', '0')), int(round(bpm)))
        song.tempo = self.tempos.get(0, 120)
        song.measureHeaders = []
        song.tracks = []
        ts = (4, 4)
        for i, mb in enumerate(self.masters):
            h = gp.MeasureHeader(number=i + 1)
            t = text(mb, 'Time')
            if t:
                n, d = t.split('/')
                ts = (int(n), int(d))
            h.timeSignature = gp.TimeSignature(numerator=ts[0], denominator=gp.Duration(value=ts[1]))
            rep = mb.find('Repeat')
            if rep is not None:
                h.isRepeatOpen = rep.get('start') == 'true'
                if rep.get('end') == 'true':
                    h.repeatClose = max(int(rep.get('count', '2')) - 1, 1)
            alt = 0
            for a in ints(text(mb, 'AlternateEndings')):
                if 1 <= a <= 8:
                    alt |= 1 << (a - 1)
            h.repeatAlternative = alt
            sec = text(mb, 'Section/Text')
            if sec:
                h.marker = gp.Marker(title=sec)
            if mb.find('DoubleBar') is not None:
                h.hasDoubleBar = True
            song.addMeasureHeader(h)
        for ti, tr in enumerate(self.tracks):
            song.tracks.append(self.track(song, ti, tr))
        return song

    def track(self, song, ti, tr):
        t = gp.Track(song, number=ti + 1, name=text(tr, 'Name') or 'Track %d' % (ti + 1))
        itype = text(tr, 'InstrumentSet/Type')
        ref = tr.find('Instrument')
        drum = itype == 'drumKit' or (ref is not None and 'drm' in (ref.get('ref') or ''))
        pitches = None
        capo = 0
        self.chords = {}
        for p in tr.iter('Property'):
            nm = p.get('name')
            if nm == 'Tuning' and pitches is None:
                pitches = ints(text(p, 'Pitches'))
            elif nm == 'CapoFret':
                capo = int(text(p, 'Fret', '0') or 0)
            elif nm in ('DiagramCollection', 'ChordCollection'):
                for it in p.iter('Item'):
                    if it.get('id') is not None and it.get('name'):
                        self.chords.setdefault(int(it.get('id')), it.get('name'))
        if drum:
            pitches = [0] * 6
        elif not pitches:
            pitches = [40, 45, 50, 55, 59, 64]
        pitches = pitches[:7]
        self.tune = list(reversed(pitches))
        self.arts = [int(text(a, 'OutputMidiNumber', '-1') or -1) for a in tr.findall('InstrumentSet/Elements/Element/Articulations/Articulation')]
        t.strings = [gp.GuitarString(number=i + 1, value=v) for i, v in enumerate(self.tune)]
        t.offset = capo
        t.isPercussionTrack = drum
        prog = text(tr, 'GeneralMidi/Program') or text(tr, 'Sounds/Sound/MIDI/Program') or '25'
        ch1 = text(tr, 'GeneralMidi/PrimaryChannel') or text(tr, 'MidiConnection/PrimaryChannel') or str(ti * 2 % 16)
        ch2 = text(tr, 'GeneralMidi/SecondaryChannel') or text(tr, 'MidiConnection/SecondaryChannel') or ch1
        t.channel = gp.MidiChannel(channel=9 if drum else int(ch1) % 16, effectChannel=9 if drum else int(ch2) % 16,
                                   instrument=0 if drum else int(prog) % 128)
        self.drum = drum
        t.measures = []
        for mi, mb in enumerate(self.masters):
            header = song.measureHeaders[mi]
            m = gp.Measure(t, header)
            bars = ints(text(mb, 'Bars'))
            bar = self.bars.get(bars[ti]) if ti < len(bars) else None
            vids = ints(text(bar, 'Voices')) if bar is not None else []
            for vi in range(2):
                if vi < len(vids) and vids[vi] >= 0 and vids[vi] in self.voices:
                    self.voice(m.voices[vi], ints(text(self.voices[vids[vi]], 'Beats')),
                               self.tempos.get(mi) if vi == 0 and ti == 0 and mi > 0 else None)
            t.measures.append(m)
        return t

    def voice(self, v, beat_ids, tempo):
        grace = None
        for bid in beat_ids:
            b = self.beats.get(bid)
            if b is None:
                continue
            if b.find('GraceNotes') is not None:
                grace = b
                continue
            beat = gp.Beat(v)
            beat.duration = self.duration(b)
            nids = ints(text(b, 'Notes'))
            beat.status = gp.BeatStatus.normal if nids else gp.BeatStatus.rest
            vel = DYN.get(text(b, 'Dynamic'), 95)
            bp = props(b)
            used = set()
            for nid in nids:
                n = self.notes.get(nid)
                if n is not None:
                    note = self.note(beat, n, vel, used)
                    if note is not None:
                        beat.notes.append(note)
            beat.notes.sort(key=lambda nt: nt.string)
            if not beat.notes:
                beat.status = gp.BeatStatus.rest
            if grace is not None and beat.notes:
                self.attach_grace(beat, grace)
            grace = None
            ft = text(b, 'FreeText')
            if ft:
                beat.text = ft
            cid = text(b, 'Chord')
            if cid.isdigit() and int(cid) in self.chords:
                beat.effect.chord = gp.Chord(length=len(self.tune), name=self.chords[int(cid)], newFormat=True, sharp=True,
                                             root=gp.PitchClass(0), type=gp.ChordType.major, extension=gp.ChordExtension.none,
                                             bass=gp.PitchClass(0), tonality=gp.ChordAlteration.perfect, add=False,
                                             fifth=gp.ChordAlteration.perfect, ninth=gp.ChordAlteration.perfect,
                                             eleventh=gp.ChordAlteration.perfect, firstFret=1, show=True)
                fr = [nt.value for nt in beat.notes if nt.value > 0]
                beat.effect.chord.firstFret = min(fr) if fr else 1
                for nt in beat.notes:
                    beat.effect.chord.strings[nt.string - 1] = nt.value
            brush = pval(bp, 'Brush', 'Direction')
            if brush:
                beat.effect.stroke = gp.BeatStroke(direction=gp.BeatStrokeDirection.up if brush == 'Up' else gp.BeatStrokeDirection.down, value=16)
            pick = pval(bp, 'PickStroke', 'Direction')
            if pick:
                beat.effect.pickStroke = gp.BeatStrokeDirection.up if pick == 'Up' else gp.BeatStrokeDirection.down
            if 'Slapped' in bp:
                beat.effect.slapEffect = gp.SlapEffect.slapping
            elif 'Popped' in bp:
                beat.effect.slapEffect = gp.SlapEffect.popping
            elif 'Tapped' in bp:
                beat.effect.slapEffect = gp.SlapEffect.tapping
            if text(b, 'Fadding') == 'FadeIn':
                beat.effect.fadeIn = True
            tr = text(b, 'Tremolo')
            if tr and beat.notes:
                d = {'1/2': 8, '1/4': 16, '1/8': 32}.get(tr, 16)
                for nt in beat.notes:
                    nt.effect.tremoloPicking = gp.TremoloPickingEffect(duration=gp.Duration(value=d))
            if tempo:
                beat.effect.mixTableChange = gp.MixTableChange(tempo=gp.MixTableItem(value=tempo, duration=0))
                tempo = None
            v.beats.append(beat)

    def duration(self, b):
        r = self.rhythms.get(int(b.find('Rhythm').get('ref'))) if b.find('Rhythm') is not None else None
        d = gp.Duration(value=VALUES.get(text(r, 'NoteValue'), 4))
        if r is not None:
            dot = r.find('AugmentationDot')
            if dot is not None and int(dot.get('count', '1')) > 0:
                d.isDotted = True
            tup = r.find('PrimaryTuplet')
            if tup is not None:
                en, tm = int(tup.get('num')), int(tup.get('den'))
                can = 2 if en == 3 else 4 if 5 <= en <= 7 else 8 if 9 <= en <= 13 else 0
                if can and tm > 0:
                    v = d.value * can
                    if v % tm == 0 and (v // tm) in (1, 2, 4, 8, 16, 32, 64):
                        d.value = v // tm
                        d.tuplet = gp.Tuplet(enters=en, times=can)
        return d

    def pick_string(self, midi, used):
        best = None
        for i, sv in enumerate(self.tune):
            s = i + 1
            if s in used:
                continue
            f = midi - sv
            if f < 0:
                continue
            if self.drum:
                return s, midi
            if best is None or f < best[1]:
                best = (s, f)
        return best

    def note(self, beat, n, vel, used):
        p = props(n)
        sidx = pval(p, 'String', 'String')
        fret = pval(p, 'Fret', 'Fret')
        if sidx is not None and fret is not None and not self.drum:
            s = len(self.tune) - int(sidx)
            f = int(fret)
            if s in used or s < 1 or s > len(self.tune):
                return None
        else:
            midi = pval(p, 'Midi', 'Number')
            if midi is None and self.drum:
                ia = text(n, 'InstrumentArticulation')
                if fret is not None:
                    midi = fret
                elif ia.isdigit() and int(ia) < len(self.arts) and self.arts[int(ia)] >= 0:
                    midi = self.arts[int(ia)]
            if midi is None:
                tone, octv = pval(p, 'Tone', 'Step'), pval(p, 'Octave', 'Number')
                if tone is not None and octv is not None:
                    midi = int(tone) + 12 * int(octv) - 12
                else:
                    el, var = pval(p, 'Element', 'Element'), pval(p, 'Variation', 'Variation')
                    midi = DRUMS.get((int(el), int(var or 0))) if el is not None else None
            if midi is None:
                return None
            sf = self.pick_string(int(midi), used)
            if sf is None:
                return None
            s, f = sf
        used.add(s)
        note = gp.Note(beat, value=f, velocity=vel, string=s, type=gp.NoteType.normal)
        tie = n.find('Tie')
        if tie is not None and tie.get('destination') == 'true':
            note.type = gp.NoteType.tie
        if 'Muted' in p:
            note.type = gp.NoteType.dead
        e = note.effect
        if n.find('Vibrato') is not None:
            e.vibrato = True
        if n.find('LetRing') is not None:
            e.letRing = True
        if n.find('AntiAccent') is not None:
            e.ghostNote = True
        acc = int(text(n, 'Accent', '0') or 0)
        e.staccato = bool(acc & 1)
        e.heavyAccentuatedNote = bool(acc & 4)
        e.accentuatedNote = bool(acc & 8)
        if 'PalmMuted' in p:
            e.palmMute = True
        if 'HopoOrigin' in p:
            e.hammer = True
        fl = pval(p, 'Slide', 'Flags')
        if fl:
            e.slides = [st for bit, st in SLIDES if int(fl) & bit]
        ht = pval(p, 'HarmonicType', 'HType')
        if ht:
            hf = pval(p, 'HarmonicFret', 'HFret')
            if ht == 'Natural':
                e.harmonic = gp.NaturalHarmonic()
            elif ht == 'Pinch':
                e.harmonic = gp.PinchHarmonic()
            elif ht == 'Tap':
                e.harmonic = gp.TappedHarmonic(fret=int(float(hf)) if hf else 12)
            elif ht == 'Semi':
                e.harmonic = gp.SemiHarmonic()
            else:
                e.harmonic = gp.ArtificialHarmonic()
        if 'Bended' in p:
            pts = []
            for key, off in (('BendOriginValue', 'BendOriginOffset'), ('BendMiddleValue', 'BendMiddleOffset1'),
                             ('BendMiddleValue', 'BendMiddleOffset2'), ('BendDestinationValue', 'BendDestinationOffset')):
                v = pval(p, key, 'Float')
                if v is None:
                    continue
                o = pval(p, off, 'Float')
                pos = float(o) if o is not None else (0.0 if key == 'BendOriginValue' else 100.0 if key == 'BendDestinationValue' else 50.0)
                pts.append(gp.BendPoint(position=int(round(pos * 12 / 100)), value=int(round(float(v) / 25))))
            pts.sort(key=lambda q: q.position)
            if pts:
                e.bend = gp.BendEffect(type=gp.BendType.bend, value=max(q.value for q in pts) * 25, points=pts)
        tv = text(n, 'Trill')
        if tv and s <= len(self.tune):
            e.trill = gp.TrillEffect(fret=max(int(tv) - self.tune[s - 1], 0), duration=gp.Duration(value=16))
        return note

    def attach_grace(self, beat, gb):
        on = text(gb, 'GraceNotes') == 'OnBeat'
        gnotes = [self.notes.get(i) for i in ints(text(gb, 'Notes'))]
        for gn in gnotes:
            if gn is None:
                continue
            p = props(gn)
            sidx, fret = pval(p, 'String', 'String'), pval(p, 'Fret', 'Fret')
            if sidx is None or fret is None:
                continue
            s = len(self.tune) - int(sidx)
            target = next((nt for nt in beat.notes if nt.string == s), beat.notes[0])
            target.effect.grace = gp.GraceEffect(fret=int(fret), duration=32, isDead='Muted' in p, isOnBeat=on)


def texts(song):
    for f in ('title', 'subtitle', 'artist', 'album', 'words', 'music', 'copyright', 'tab', 'instructions'):
        yield song, f
    for h in song.measureHeaders:
        if h.marker is not None:
            yield h.marker, 'title'
    for t in song.tracks:
        yield t, 'name'
        for m in t.measures:
            for v in m.voices:
                for b in v.beats:
                    if b.text:
                        yield b, 'text'
                    if b.effect.chord is not None:
                        yield b.effect.chord, 'name'


def convert(data):
    return write(Conv(load(data)).song())


def write(song):
    fields = list(texts(song))
    enc = 'cp1251' if any(re.search('[\u0400-\u04ff]', getattr(o, f) or '') for o, f in fields) else 'cp1252'
    for o, f in fields:
        v = getattr(o, f) or ''
        setattr(o, f, v.encode(enc, 'replace').decode(enc))
    out = io.BytesIO()
    gp.write(song, out, version=(5, 1, 0), encoding=enc)
    return out.getvalue()
