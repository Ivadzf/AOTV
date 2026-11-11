"""Harness de tracing da Fase 1 (v1.1).

Roda um addon contra os stubs xbmc* com o log de chamadas ligado e
extrai a lista real de chamadas (módulo, função, args) — é assim que se
fecha a lacuna do default.py ofuscado do BrazucaPlay e se valida a
superfície do Vulgata extraída estaticamente (plano §2).

Uso (teste local / CI):
    from aotv_kodi_trace import trace_plugin, export_calls
    report = trace_plugin(addon_id, path="", query="", log_file="...")
    export_calls(report, "calls.txt")

O log é legível e exportável: uma chamada por linha, com a contagem de
cada função distinta no topo. Nada aqui levanta exceção — o tracing
nunca pode quebrar o addon.
"""

import os
import sys

import _aotv_kodi_log as _logmod


def start_trace(log_file):
    """Liga o log de chamadas (trunca o arquivo; corrida nova)."""
    _logmod.set_log_file(log_file)
    return log_file


def stop_trace():
    """Para o log e devolve o resumo (funções distintas + últimas 10)."""
    return _logmod.calls_summary()


def distinct_calls():
    """Lista de funções distintas chamadas, em ordem de 1ª chamada."""
    seen = []
    for name, _a, _r in _logmod._calls:
        if name not in seen:
            seen.append(name)
    return seen


def call_counts():
    """{função: nº de chamadas}, ordenado por contagem (maior primeiro)."""
    counts = {}
    for name, _a, _r in _logmod._calls:
        counts[name] = counts.get(name, 0) + 1
    return dict(sorted(counts.items(), key=lambda kv: -kv[1]))


def export_calls(path):
    """Escreve a lista exportável: contagens + todas as chamadas.

    Formato legível (uma chamada por linha):
        # AOTV Kodi trace — <n> chamadas, <m> funções distintas
        # <função>: <contagem>
        <função>(<args>) -> <retorno>
    Devolve o caminho escrito (ou "" se falhar).
    """
    try:
        counts = call_counts()
        total = len(_logmod._calls)
        with open(path, "w", encoding="utf-8") as f:
            f.write("# AOTV Kodi trace — %d chamadas, %d funções distintas\n"
                    % (total, len(counts)))
            for name, n in counts.items():
                f.write("# %s: %d\n" % (name, n))
            f.write("# ----\n")
            for name, args, result in _logmod._calls:
                f.write("%s(%s) -> %s\n" % (name, args, result))
        return path
    except Exception:
        return ""


def trace_plugin(addon_id, path="", query="", handle=1, log_file=None,
                 export_path=None):
    """Roda um plugin sob tracing e devolve o relatório.

    Retorna dict: {"addon_id", "items", "resolved_url", "succeeded",
    "error", "calls" (funções distintas), "counts", "summary",
    "log_file", "export_path"}.
    """
    import aotv_kodi
    if log_file:
        start_trace(log_file)
    result = aotv_kodi.run_plugin(addon_id, path, query, handle)
    report = {
        "addon_id": addon_id,
        "items": result.get("items", []),
        "resolved_url": result.get("resolved_url", ""),
        "succeeded": result.get("succeeded", False),
        "error": result.get("error", ""),
        "calls": distinct_calls(),
        "counts": call_counts(),
        "summary": stop_trace(),
        "log_file": log_file or "",
        "export_path": "",
    }
    if export_path:
        report["export_path"] = export_calls(export_path)
    return report


def trace_file(addon_dir, log_file=None, export_path=None):
    """Roda o main.py/default.py de um addon por dir (sem addon.xml).

    Útil no teste local: aponta o dir extraído do ZIP e roda a entrada
    do plugin (library do addon.xml, ou main.py/default.py)."""
    import aotv_kodi
    addon_id = os.path.basename(os.path.normpath(addon_dir))
    # Descobre o id real pelo addon.xml, se houver.
    try:
        import xml.etree.ElementTree as ET
        tree = ET.parse(os.path.join(addon_dir, "addon.xml"))
        addon_id = tree.getroot().get("id", addon_id)
    except Exception:
        pass
    # Registra o dir como addon instalado (para o aotv_kodi achar).
    base = aotv_kodi.get_addons_dir()
    if base and os.path.isdir(os.path.join(base, addon_id)):
        pass  # já instalado no dir de addons
    else:
        aotv_kodi.set_addons_dir(os.path.dirname(os.path.normpath(addon_dir)))
    return trace_plugin(addon_id, "", "", 1, log_file, export_path)
