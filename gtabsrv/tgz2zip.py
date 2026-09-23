import sys
import tarfile
import zipfile

MARK = 'storage/tabs/'


def main(src, dst):
    stream = tarfile.open(fileobj=sys.stdin.buffer, mode='r|gz') if src == '-' else tarfile.open(src, mode='r|gz')
    n = 0
    with zipfile.ZipFile(dst + '.tmp', 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for m in stream:
            if not m.isfile():
                continue
            name = m.name
            i = name.find(MARK)
            if i >= 0:
                name = name[i + len(MARK):]
            z.writestr(zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0)), stream.extractfile(m).read(), zipfile.ZIP_DEFLATED)
            n += 1
    import os
    os.replace(dst + '.tmp', dst)
    print(n)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
