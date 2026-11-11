"""Stub mínimo do módulo xbmcvfs do Kodi — spike da v1.1 (Fase 0).

Clean-room (nomes/assinaturas públicos da API Python do Kodi). O File usa
o open() do Python; o stub não emula o VFS special:// (isso é Fase 1).
"""

from _aotv_kodi_log import log as _log


def translatePath(path):
    _log("xbmcvfs.translatePath", path)
    return path


def exists(path):
    import os
    return os.path.exists(path)


def mkdir(path):
    import os
    try:
        os.makedirs(path, exist_ok=True)
        return True
    except Exception:
        return False


def delete(path):
    import os
    try:
        os.remove(path)
        return True
    except Exception:
        return False


def rename(from_path, to_path):
    import os
    try:
        os.rename(from_path, to_path)
        return True
    except Exception:
        return False


class File:
    def __init__(self, path, mode="r"):
        self._f = open(path, mode)

    def read(self, bytes=-1):
        return self._f.read(bytes)

    def write(self, data):
        return self._f.write(data)

    def size(self):
        import os
        pos = self._f.tell()
        self._f.seek(0, os.SEEK_END)
        size = self._f.tell()
        self._f.seek(pos)
        return size

    def close(self):
        self._f.close()

# Spike6: registra cada chamada xbmcvfs* (nome, args, retorno) no log de
# chamadas (filesDir/kodi/xbmc_calls.log) — ver _aotv_kodi_log.record.
from _aotv_kodi_log import traced as _traced

for _fn in ("translatePath", "exists", "mkdir", "delete", "rename"):
    globals()[_fn] = _traced("xbmcvfs." + _fn, globals()[_fn])
for _m in ("read", "write", "size", "close"):
    setattr(File, _m, _traced("xbmcvfs.File." + _m, getattr(File, _m)))
