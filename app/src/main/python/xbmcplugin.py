"""Stub mínimo do módulo xbmcplugin do Kodi — spike da v1.1 (Fase 0).

Clean-room (nomes/assinaturas públicos da API Python do Kodi). Cada chamada
só registra (log) e devolve um valor neutro; a coleta dos itens devolvidos
é Fase 2 (KodiPluginRunner).
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


def addDirectoryItem(handle, url, listitem, isFolder=False, totalItems=0):
    _log("xbmcplugin.addDirectoryItem", "handle=%s url=%s" % (handle, url))
    return True


def endOfDirectory(handle, succeeded=True, updateListing=False, cacheToDisc=True):
    _log("xbmcplugin.endOfDirectory", "handle=%s ok=%s" % (handle, succeeded))


def setContent(handle, content):
    _log("xbmcplugin.setContent", "handle=%s content=%s" % (handle, content))


def addSortMethod(handle, sortMethod):
    _log("xbmcplugin.addSortMethod", "handle=%s method=%s" % (handle, sortMethod))


def setResolvedUrl(handle, succeeded, listitem):
    _log("xbmcplugin.setResolvedUrl", "handle=%s ok=%s" % (handle, succeeded))


def setPluginCategory(handle, category):
    _log("xbmcplugin.setPluginCategory", "handle=%s cat=%s" % (handle, category))

# Spike6: registra cada chamada xbmcplugin* (nome, args, retorno) no log de
# chamadas (filesDir/kodi/xbmc_calls.log) — ver _aotv_kodi_log.record.
from _aotv_kodi_log import traced as _traced

for _fn in ("addDirectoryItem", "endOfDirectory", "setContent",
            "addSortMethod", "setResolvedUrl", "setPluginCategory"):
    globals()[_fn] = _traced("xbmcplugin." + _fn, globals()[_fn])
