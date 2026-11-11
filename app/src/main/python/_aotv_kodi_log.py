"""Log helper dos stubs xbmc* do AOTV (v1.1, Fase 0 — spike).

Escreve no Logcat via android.util.Log quando disponível; senão, print
(o Chaquopy redireciona o stdout do Python para o Logcat).
"""


def log(tag, msg):
    text = "[aotv.kodi] %s: %s" % (tag, msg)
    try:
        from android.util import Log as _AndroidLog
        _AndroidLog.d("AOTV-Kodi", text)
    except Exception:
        print(text)
