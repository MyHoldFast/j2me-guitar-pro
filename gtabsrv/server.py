import asyncio
import hashlib
import os
import struct
import sys
from concurrent.futures import ProcessPoolExecutor

from gpif2gp5 import convert
from search import Index
from store import Store
from tabmeta import detect

PAGE = 20
MAX_UPLOAD = 4 * 1024 * 1024
IDLE = 300


def utf(s):
    s = ''.join(c for c in s if c != '\0' and ord(c) < 0x10000)
    b = s.encode('utf-8')
    if len(b) > 65535:
        b = b[:65535].decode('utf-8', 'ignore').encode('utf-8')
    return struct.pack('>H', len(b)) + b


def err(msg):
    return b'\1' + utf(msg)


def blob(name, data):
    return b'\0' + utf(name) + struct.pack('>i', len(data)) + data


def as_gp5(data):
    fmt = detect(data[:64])
    if fmt in ('gp3', 'gp4', 'gp5'):
        return fmt, data
    if fmt in ('gpx', 'gp7'):
        return 'gp5', convert(data)
    raise ValueError('unsupported format')


class Server:
    def __init__(self, root, index, cache):
        self.store = Store(root)
        self.cache = cache
        self.ix = Index(index)
        self.pool = ProcessPoolExecutor(max_workers=2)
        os.makedirs(cache, exist_ok=True)

    async def converted(self, e):
        path = os.path.join(self.cache, hashlib.sha1(e.path.encode('utf-8')).hexdigest() + '.gp5')
        if os.path.exists(path):
            with open(path, 'rb') as f:
                return f.read()
        src = self.store.read(e.path)
        fmt, data = await asyncio.get_running_loop().run_in_executor(self.pool, as_gp5, src)
        with open(path + '.tmp', 'wb') as f:
            f.write(data)
        os.replace(path + '.tmp', path)
        return data

    async def handle(self, r, w):
        try:
            while True:
                cmd = await asyncio.wait_for(r.readexactly(1), IDLE)
                if cmd == b'S':
                    resp = await self.search(r)
                elif cmd == b'F':
                    resp = await self.fetch(r)
                elif cmd == b'C':
                    resp = await self.upload(r)
                else:
                    break
                w.write(resp)
                await w.drain()
        except (asyncio.IncompleteReadError, asyncio.TimeoutError, ConnectionError):
            pass
        finally:
            w.close()

    async def search(self, r):
        q = await rutf(r)
        off = max(await rint(r), 0)
        total, items = self.ix.search(q, off, PAGE)
        out = [b'\0', struct.pack('>ih', total, len(items))]
        for e in items:
            out.append(struct.pack('>i', e.id) + utf(e.fmt) + utf(e.artist) + utf(e.title))
        return b''.join(out)

    async def fetch(self, r):
        i = await rint(r)
        if i < 0 or i >= len(self.ix.items):
            return err('not found')
        e = self.ix.items[i]
        try:
            if e.fmt in ('gpx', 'gp7'):
                data = await self.converted(e)
            else:
                data = self.store.read(e.path)
        except Exception as ex:
            return err('conversion failed: %s' % type(ex).__name__)
        return blob(e.filename(), data)

    async def upload(self, r):
        name = await rutf(r)
        n = await rint(r)
        if n <= 0 or n > MAX_UPLOAD:
            return err('bad size')
        src = await r.readexactly(n)
        try:
            fmt, data = await asyncio.get_running_loop().run_in_executor(self.pool, as_gp5, src)
        except Exception as ex:
            return err('conversion failed: %s' % type(ex).__name__)
        base = os.path.splitext(os.path.basename(name.replace('\\', '/')))[0] or 'tab'
        return blob(base + '.' + fmt, data)


async def rint(r):
    return struct.unpack('>i', await r.readexactly(4))[0]


async def rutf(r):
    n = struct.unpack('>H', await r.readexactly(2))[0]
    return (await r.readexactly(n)).decode('utf-8', 'replace')


async def main(root, index, cache, host, port):
    srv = Server(root, index, cache)
    server = await asyncio.start_server(srv.handle, host, port)
    async with server:
        await server.serve_forever()


if __name__ == '__main__':
    root, index, cache = sys.argv[1], sys.argv[2], sys.argv[3]
    host = sys.argv[4] if len(sys.argv) > 4 else '0.0.0.0'
    port = int(sys.argv[5]) if len(sys.argv) > 5 else 7000
    asyncio.run(main(root, index, cache, host, port))
