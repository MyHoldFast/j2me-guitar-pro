import os
import zipfile


class Store:
    def __init__(self, path):
        self.zip = zipfile.ZipFile(path) if os.path.isfile(path) else None
        self.root = path

    def names(self):
        if self.zip is not None:
            return sorted(i.filename for i in self.zip.infolist() if not i.is_dir())
        out = []
        for d, dirs, files in os.walk(self.root):
            for f in files:
                out.append(os.path.relpath(os.path.join(d, f), self.root).replace(os.sep, '/'))
        return sorted(out)

    def size(self, name):
        if self.zip is not None:
            return self.zip.getinfo(name).file_size
        return os.path.getsize(os.path.join(self.root, name))

    def read(self, name):
        if self.zip is not None:
            return self.zip.read(name)
        with open(os.path.join(self.root, name), 'rb') as f:
            return f.read()
