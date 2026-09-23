import re
import struct

GP_VERSIONS = {
    b'FICHIER GUITAR PRO v3.00': 'gp3', b'FICHIER GUITAR PRO v4.00': 'gp4', b'FICHIER GUITAR PRO v4.06': 'gp4',
    b'FICHIER GUITAR PRO L4.06': 'gp4', b'FICHIER GUITAR PRO v5.00': 'gp5', b'FICHIER GUITAR PRO v5.10': 'gp5',
}


def detect(head):
    if head[:4] == b'BCFZ':
        return 'gpx'
    if head[:4] == b'BCFS':
        return 'gpx'
    if head[:2] == b'PK':
        return 'gp7'
    if len(head) > 1 and 0 < head[0] <= 30:
        v = head[1:1 + head[0]]
        if v in GP_VERSIONS:
            return GP_VERSIONS[v]
        if v.startswith(b'FICHIER GUITARE PRO v1') or v.startswith(b'FICHIER GUITAR PRO v1'):
            return 'gp1'
        if v.startswith(b'FICHIER GUITAR PRO v2'):
            return 'gp2'
    return None


def cyr_score(b):
    run = n = 0
    for c in b:
        if c >= 0xC0:
            run += 1
            if run == 3:
                n += 1
        else:
            run = 0
    return n


def decode(b):
    b = b.split(b'\0', 1)[0]
    return b.decode('cp1251' if cyr_score(b) else 'cp1252', 'replace').strip()


def gp_meta(data):
    try:
        o = 31
        out = []
        for _ in range(3):
            size = struct.unpack_from('<i', data, o)[0]
            ln = data[o + 4]
            out.append(data[o + 5:o + 5 + min(ln, max(size - 1, 0))])
            o += 4 + max(size, 1)
        return decode(out[0]), decode(out[2])
    except Exception:
        return '', ''


class BCFZ:
    def __init__(self, src):
        self.src = src
        self.expect = struct.unpack_from('<i', src, 4)[0]
        self.pos = 8 * 8
        self.out = bytearray()

    def bit(self):
        p = self.pos
        self.pos = p + 1
        return (self.src[p >> 3] >> (7 - (p & 7))) & 1

    def bits(self, n):
        v = 0
        for _ in range(n):
            v = (v << 1) | self.bit()
        return v

    def rbits(self, n):
        v = 0
        for i in range(n):
            v |= self.bit() << i
        return v

    def need(self, n):
        out, end = self.out, len(self.src) * 8
        try:
            while len(out) < n and self.pos < end and (self.pos >> 3) - 4 < self.expect:
                if self.bit():
                    w = self.bits(4)
                    offs = self.rbits(w)
                    size = self.rbits(w)
                    start = len(out) - offs
                    out += out[start:start + min(size, offs)]
                else:
                    for _ in range(self.rbits(2)):
                        out.append(self.bits(8))
        except IndexError:
            pass
        return len(out) >= n


def gpif(data, limit=None):
    if data[:4] == b'BCFZ':
        z = BCFZ(data)

        def get(n):
            z.need(n + 4)
            return bytes(z.out[4:])
    else:
        buf = data[4:]
        get = lambda n: buf
    sector = 0x1000
    off = sector
    while True:
        fs = get(off + sector)
        if off + 0x98 > len(fs):
            return None
        if struct.unpack_from('<i', fs, off)[0] == 2:
            name = bytes(fs[off + 4:off + 4 + 127]).split(b'\0', 1)[0]
            size = struct.unpack_from('<i', fs, off + 0x8C)[0]
            if name == b'score.gpif':
                blocks = []
                i = off + 0x94
                while True:
                    b = struct.unpack_from('<i', fs, i)[0]
                    if b == 0:
                        break
                    blocks.append(b)
                    i += 4
                want = size if limit is None else min(size, limit)
                res = bytearray()
                for b in blocks:
                    if len(res) >= want:
                        break
                    fs = get((b + 1) * sector)
                    res += bytes(fs[b * sector:(b + 1) * sector])
                return bytes(res[:want])
        off += sector


CDATA = re.compile(rb'<(Title|Artist)>\s*(?:<!\[CDATA\[(.*?)\]\]>|([^<]*))\s*</\1>', re.S)


def gpif_meta(x):
    got = {}
    for m in CDATA.finditer(x[:20000]):
        k = m.group(1)
        if k not in got:
            got[k] = (m.group(2) if m.group(2) is not None else m.group(3)).decode('utf-8', 'replace').strip()
    return got.get(b'Title', ''), got.get(b'Artist', '')


def meta(data):
    fmt = detect(data[:4096])
    if fmt in ('gp3', 'gp4', 'gp5'):
        t, a = gp_meta(data[:4096])
        return fmt, t, a
    if fmt == 'gpx':
        x = gpif(data, 20000)
        if x:
            t, a = gpif_meta(x)
            return fmt, t, a
    return fmt, '', ''
