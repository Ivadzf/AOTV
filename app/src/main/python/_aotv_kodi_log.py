"""Log helper dos stubs xbmc* do AOTV (v1.1, Fase 0 — spike).

Escreve no Logcat via android.util.Log quando disponível; senão, print
(o Chaquopy redireciona o stdout do Python para o Logcat).

Spike6: gravador de chamadas xbmc* — cada função dos stubs registra
(nome, args, retorno) em memória e num arquivo (filesDir/kodi/
xbmc_calls.log, truncado a cada teste, com teto de tamanho). O driver
(aotv_spike.py) liga o arquivo no início do teste e lê o resumo no fim.
O .so do Vulgata chama essas funções via C API; o resumo mostra o que o
init dele pediu (ex.: getInfoLabel('System.BuildVersion')) e o que cada
chamada devolveu. Nada aqui levanta exceção — o log nunca pode quebrar
o addon.
"""

import os

_MAX_CALLS = 500
_MAX_FILE_BYTES = 256 * 1024

_calls = []
_log_file = None


def log(tag, msg):
    text = "[aotv.kodi] %s: %s" % (tag, msg)
    try:
        from android.util import Log as _AndroidLog
        _AndroidLog.d("AOTV-Kodi", text)
    except Exception:
        print(text)


def _short(value, limit=200):
    try:
        text = value if isinstance(value, str) else repr(value)
    except Exception:
        text = "<repr falhou>"
    if len(text) > limit:
        text = text[:limit] + "…"
    return text


def set_log_file(path):
    """Liga o arquivo de log (trunca; corrida nova a cada teste)."""
    global _log_file
    _calls.clear()
    _log_file = None
    try:
        parent = os.path.dirname(path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write("# xbmc_calls.log — truncado a cada teste de carga\n")
        _log_file = path
    except Exception:
        _log_file = None


def record(name, args="", result=""):
    """Registra uma chamada (nome, args, retorno). Nunca levanta."""
    try:
        entry = (name, _short(args), _short(result))
        _calls.append(entry)
        if len(_calls) > _MAX_CALLS:
            del _calls[:len(_calls) - _MAX_CALLS]
        if _log_file is not None:
            try:
                if os.path.getsize(_log_file) > _MAX_FILE_BYTES:
                    with open(_log_file, "w", encoding="utf-8") as f:
                        f.write("# xbmc_calls.log — truncado (teto de tamanho)\n")
                with open(_log_file, "a", encoding="utf-8") as f:
                    f.write("%s(%s) -> %s\n" % (entry[0], entry[1], entry[2]))
            except Exception:
                pass
    except Exception:
        pass


def _fmt_args(args, kwargs):
    parts = []
    for a in args:
        try:
            parts.append(repr(a))
        except Exception:
            parts.append("<arg>")
    for k, v in kwargs.items():
        try:
            parts.append("%s=%r" % (k, v))
        except Exception:
            parts.append("%s=<arg>" % k)
    return ", ".join(parts)[:400]


def traced(name, fn):
    """Envolve uma função/método de stub registrando (args, retorno).

    O erro da função original é registrado e relançado — o log não
    esconde falhas.
    """
    import functools

    @functools.wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            result = fn(*args, **kwargs)
        except BaseException as e:
            record(name, _fmt_args(args, kwargs),
                   "RAISED %s: %s" % (type(e).__name__, e))
            raise
        record(name, _fmt_args(args, kwargs), result)
        return result

    return wrapper


def calls_summary():
    """Resumo: funções distintas (ordem de 1ª chamada) + últimas 10."""
    if not _calls:
        return "(nenhuma chamada xbmc* registrada)"
    seen = []
    for name, _a, _r in _calls:
        if name not in seen:
            seen.append(name)
    lines = ["; ".join(seen), "Últimas chamadas:"]
    for name, args, result in _calls[-10:]:
        lines.append("  %s(%s) -> %s" % (name, args, result))
    return "\n".join(lines)
