import os

from textkey import key, words


def pretty(slug):
    return ' '.join(w[:1].upper() + w[1:] for w in slug.replace('_', ' ').split())


class Entry:
    __slots__ = ('id', 'fmt', 'size', 'path', 'artist', 'title', 'aslug', 'tslug', 'sa', 'st', 'ma', 'mt')

    def filename(self):
        base = (self.aslug + '-' + self.tslug)[:56].strip('-_')
        return base + '.' + ('gp5' if self.fmt in ('gpx', 'gp7') else self.fmt)


class Index:
    def __init__(self, path):
        self.items = []
        with open(path, encoding='utf-8') as f:
            for line in f:
                fmt, size, rel, mt, ma = line.rstrip('\n').split('\t')
                if fmt not in ('gp3', 'gp4', 'gp5', 'gpx', 'gp7'):
                    continue
                parts = rel.replace('\\', '/').split('/')
                aslug = parts[-2] if len(parts) > 1 else ''
                tslug = os.path.splitext(parts[-1])[0]
                e = Entry()
                e.id = len(self.items)
                e.fmt, e.size, e.path, e.aslug, e.tslug = fmt, int(size), rel, aslug, tslug
                e.sa, e.st, e.ma, e.mt = ' ' + key(aslug) + ' ', ' ' + key(tslug) + ' ', ' ' + key(ma) + ' ', ' ' + key(mt) + ' '
                e.artist = ma if ma and e.ma == e.sa else pretty(aslug)
                e.title = mt if mt and e.mt == e.st else pretty(tslug)
                self.items.append(e)

    def search(self, q, offset=0, limit=20):
        ws = words(q)
        if not ws:
            return 0, []
        phrase = ' ' + ' '.join(ws)
        hits = []
        for i, e in enumerate(self.items):
            score = 0
            for w in ws:
                sw = ' ' + w
                if sw in e.sa:
                    score += 5
                elif sw in e.st:
                    score += 4
                elif sw in e.ma or sw in e.mt:
                    score += 2
                elif len(w) > 2 and (w in e.sa or w in e.st):
                    score += 1
                else:
                    score = -1
                    break
            if score < 0:
                continue
            if len(ws) > 1:
                if phrase in e.sa or phrase in e.st:
                    score += 8
                elif phrase in e.sa + e.st[1:]:
                    score += 6
            if e.sa.strip() == phrase.strip():
                score += 6
            if e.fmt in ('gp3', 'gp4', 'gp5'):
                score += 1
            hits.append((-score, e.sa, e.st, i))
        hits.sort()
        return len(hits), [self.items[h[3]] for h in hits[offset:offset + limit]]
