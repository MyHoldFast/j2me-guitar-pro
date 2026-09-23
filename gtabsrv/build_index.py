import os
import sys

from store import Store
from tabmeta import meta


def clean(s):
    return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').strip()


def main(src, out):
    st = Store(src)
    n = 0
    with open(out + '.tmp', 'w', encoding='utf-8') as f:
        for rel in st.names():
            try:
                fmt, title, artist = meta(st.read(rel))
            except Exception:
                fmt, title, artist = None, '', ''
            if not fmt:
                continue
            f.write('\t'.join((fmt, str(st.size(rel)), rel, clean(title), clean(artist))) + '\n')
            n += 1
    os.replace(out + '.tmp', out)
    print(n)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
