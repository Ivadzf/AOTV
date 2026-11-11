"""Módulo xbmcplugin do Kodi — AOTV v1.1 (Fase 1).

Clean-room: reimplementação a partir dos nomes/assinaturas públicos da
API Python do Kodi (docs.kodi.tv). Não é código do Kodi (GPLv2).

Coleta: cada addDirectoryItem/setResolvedUrl é registrado num coletor
por handle (o aotv_kodi.py lê depois do dispatch). Na Fase 1 o coletor
é a forma de devolver os itens ao Kotlin; a execução completa
(KodiPluginRunner) é Fase 2.
"""

from _aotv_kodi_log import log as _log

SORT_METHOD_NONE = 0
SORT_METHOD_LABEL = 1
SORT_METHOD_LABEL_IGNORE_THE = 2
SORT_METHOD_DATE = 3
SORT_METHOD_SIZE = 4
SORT_METHOD_FILE = 5
SORT_METHOD_DRIVE_TYPE = 6
SORT_METHOD_TITLE = 7
SORT_METHOD_TITLE_IGNORE_THE = 8
SORT_METHOD_EPISODE = 9
SORT_METHOD_VIDEO_YEAR = 10
SORT_METHOD_VIDEO_RATING = 11
SORT_METHOD_VIDEO_RUNTIME = 12
SORT_METHOD_UNSORTED = 13
SORT_METHOD_VIDEO_TITLE = 14
SORT_METHOD_TRACKNUM = 15
SORT_METHOD_LABEL_IGNORE_FOLDERS = 16

# ---------------------------------------------------------------------------
# Coletor por handle (o aotv_kodi.py lê depois do dispatch).
# ---------------------------------------------------------------------------

_items = {}      # handle -> [(url, listitem, isFolder)]
_resolved = {}   # handle -> {"url": str, "succeeded": bool, "listitem": obj}
_content = {}    # handle -> content (str)
_category = {}   # handle -> category (str)
_sort_methods = {}  # handle -> [sortMethod]


def clear_handle(handle):
    """Limpa a coleta de um handle (antes de rodar o plugin)."""
    h = int(handle)
    _items.pop(h, None)
    _resolved.pop(h, None)
    _content.pop(h, None)
    _category.pop(h, None)
    _sort_methods.pop(h, None)


def get_items(handle):
    """Itens devolvidos por addDirectoryItem: [(url, listitem, isFolder)]."""
    return list(_items.get(int(handle), []))


def get_resolved(handle):
    """URL resolvida por setResolvedUrl: {"url", "succeeded", "listitem"}."""
    return dict(_resolved.get(int(handle), {}))


def get_content(handle):
    return _content.get(int(handle), "")


def get_category(handle):
    return _category.get(int(handle), "")


def get_sort_methods(handle):
    return list(_sort_methods.get(int(handle), []))


def addDirectoryItem(handle, url, listitem, isFolder=False, totalItems=0):
    _log("xbmcplugin.addDirectoryItem", "handle=%s url=%s" % (handle, url))
    _items.setdefault(int(handle), []).append((url, listitem, bool(isFolder)))
    return True


def addDirectoryItems(handle, items, totalItems=0):
    """Plural (Elementum) — fora do mínimo da Fase 1, mas trivial: delega."""
    ok = True
    for entry in items or []:
        try:
            url, li, is_folder = entry[0], entry[1], entry[2] if len(entry) > 2 else False
            if not addDirectoryItem(handle, url, li, is_folder):
                ok = False
        except Exception:
            ok = False
    return ok


def endOfDirectory(handle, succeeded=True, updateListing=False, cacheToDisc=True):
    _log("xbmcplugin.endOfDirectory", "handle=%s ok=%s" % (handle, succeeded))


def setContent(handle, content):
    _log("xbmcplugin.setContent", "handle=%s content=%s" % (handle, content))
    _content[int(handle)] = content


def addSortMethod(handle, sortMethod):
    _log("xbmcplugin.addSortMethod", "handle=%s method=%s" % (handle, sortMethod))
    _sort_methods.setdefault(int(handle), []).append(sortMethod)


def setResolvedUrl(handle, succeeded, listitem):
    _log("xbmcplugin.setResolvedUrl", "handle=%s ok=%s" % (handle, succeeded))
    url = ""
    try:
        url = listitem.getPath() if listitem is not None else ""
    except Exception:
        url = ""
    _resolved[int(handle)] = {
        "url": url, "succeeded": bool(succeeded), "listitem": listitem,
    }


def setPluginCategory(handle, category):
    _log("xbmcplugin.setPluginCategory", "handle=%s cat=%s" % (handle, category))
    _category[int(handle)] = category


# Registra cada chamada xbmcplugin* (nome, args, retorno) no log de
# chamadas (filesDir/kodi/xbmc_calls.log) — ver _aotv_kodi_log.record.
from _aotv_kodi_log import traced as _traced

for _fn in ("addDirectoryItem", "addDirectoryItems", "endOfDirectory",
            "setContent", "addSortMethod", "setResolvedUrl",
            "setPluginCategory"):
    globals()[_fn] = _traced("xbmcplugin." + _fn, globals()[_fn])
