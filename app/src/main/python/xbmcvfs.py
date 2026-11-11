"""Módulo xbmcvfs do Kodi — AOTV v1.1 (Fase 1).

Clean-room (nomes/assinaturas públicos da API Python do Kodi). O File usa
o open() do Python; todos os caminhos passam por translatePath (special://
-> dirs do app, via aotv_kodi). listdir/copy/mkdirs são extensões do
a4kSubtitles (Fase 2+) — fora do mínimo da Fase 1.
"""

from _aotv_kodi_log import log as _log


def _bridge():
    """O aotv_kodi (lazy; sem ele, translatePath devolve como veio)."""
    try:
        import aotv_kodi
        return aotv_kodi
    except Exception:
        return None


def translatePath(path):
    bridge = _bridge()
    if bridge is not None:
        return bridge.translate_special(path)
    return path


def _real(path):
    return translatePath(path)


def exists(path):
    import os
    return os.path.exists(_real(path))


def mkdir(path):
    import os
    try:
        os.makedirs(_real(path), exist_ok=True)
        return True
    except Exception:
        return False


def mkdirs(path):
    return mkdir(path)


def delete(path):
    import os
    try:
        os.remove(_real(path))
        return True
    except Exception:
        return False


def rename(from_path, to_path):
    import os
    try:
        os.rename(_real(from_path), _real(to_path))
        return True
    except Exception:
        return False


def copy(src, dst):
    import shutil
    try:
        shutil.copyfile(_real(src), _real(dst))
        return True
    except Exception:
        return False


class File:
    def __init__(self, path, mode="r"):
        # Modo "w" binário do Kodi -> "wb" no Python; "r" -> "rb" se for
        # binário. O Kodi abre texto por padrão; mantemos o modo pedido.
        self._f = open(_real(path), mode)

    def read(self, bytes=-1):
        return self._f.read(bytes)

    def readBytes(self, numBytes=0):
        if numBytes and numBytes > 0:
            return self._f.read(numBytes)
        return self._f.read()

    def write(self, data):
        return self._f.write(data)

    def seek(self, seekBytes, iWhence=0):
        self._f.seek(seekBytes, iWhence)

    def tell(self):
        return self._f.tell()

    def size(self):
        import os
        pos = self._f.tell()
        self._f.seek(0, os.SEEK_END)
        size = self._f.tell()
        self._f.seek(pos)
        return size

    def close(self):
        self._f.close()


# Registra cada chamada xbmcvfs* (nome, args, retorno) no log de
# chamadas (filesDir/kodi/xbmc_calls.log) — ver _aotv_kodi_log.record.
from _aotv_kodi_log import traced as _traced

for _fn in ("translatePath", "exists", "mkdir", "mkdirs", "delete",
            "rename", "copy"):
    globals()[_fn] = _traced("xbmcvfs." + _fn, globals()[_fn])
for _m in ("read", "readBytes", "write", "seek", "tell", "size", "close"):
    setattr(File, _m, _traced("xbmcvfs.File." + _m, getattr(File, _m)))
