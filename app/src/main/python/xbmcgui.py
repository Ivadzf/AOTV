"""Módulo xbmcgui do Kodi — AOTV v1.1 (Fase 1).

Clean-room: reimplementação a partir dos nomes/assinaturas públicos da
API Python do Kodi (docs.kodi.tv). Não é código do Kodi (GPLv2).

ListItem guarda tudo o que o addon seta (art/info/properties/mime/
contexto) e expõe getters — é assim que o aotv_kodi.py traduz o item
para o Kotlin. Window usa propriedades compartilhadas por windowId
(como no Kodi: Window(10000) é a janela home, visível por todo addon).
"""

from _aotv_kodi_log import log as _log

NOTIFICATION_INFO = 0
NOTIFICATION_WARNING = 1
NOTIFICATION_ERROR = 2
INPUT_ALPHANUM = 0
INPUT_NUMERIC = 1
INPUT_DATE = 2
INPUT_TIME = 3
INPUT_IPADDRESS = 4
INPUT_PASSWORD = 5


class ListItem:
    """Item de diretório/fonte. Guarda tudo; o aotv_kodi lê via getters."""

    def __init__(self, label="", label2="", path=""):
        self._label = label or ""
        self._label2 = label2 or ""
        self._path = path or ""
        self._art = {}
        self._info = {}
        self._props = {}
        self._mime_type = ""
        self._context_menu = []
        self._is_folder = False
        self._cast = []
        self._unique_ids = {}
        self._resume_point = None

    # -- setters (o addon chama) -------------------------------------------
    def setArt(self, art):
        if isinstance(art, dict):
            self._art.update(art)

    def setInfo(self, type, infoLabels):
        if isinstance(infoLabels, dict):
            # Kodi guarda por tipo; aqui achatamos (o aotv_kodi lê o dict).
            self._info.update(infoLabels)
            self._info["__type__"] = type

    def setProperty(self, key, value):
        self._props[key] = value

    def setLabel(self, label):
        self._label = label or ""

    def setLabel2(self, label2):
        self._label2 = label2 or ""

    def setMimeType(self, mimetype):
        self._mime_type = mimetype or ""

    def addContextMenuItems(self, items, replaceItems=False):
        if replaceItems:
            self._context_menu = list(items or [])
        else:
            self._context_menu.extend(list(items or []))

    def setCast(self, cast):
        if isinstance(cast, list):
            self._cast = cast

    def setUniqueIDs(self, uniqueIDs):
        if isinstance(uniqueIDs, dict):
            self._unique_ids.update(uniqueIDs)

    def setResumePoint(self, time):
        self._resume_point = time

    def setContentLookup(self, lookup):
        # Kodi: liga/desliga a busca de conteúdo (capa/sinopse) p/ o
        # item. O Vulgata chama na resolução do play; aqui só guarda.
        self._content_lookup = bool(lookup)

    # -- getters (o aotv_kodi.py lê) ----------------------------------------
    def getLabel(self):
        return self._label

    def getLabel2(self):
        return self._label2

    def getPath(self):
        return self._path

    def getArt(self):
        return dict(self._art)

    def getInfo(self):
        return dict(self._info)

    def getProperty(self, key):
        return self._props.get(key, "")

    def getProperties(self):
        return dict(self._props)

    def getMimeType(self):
        return self._mime_type

    def getContextMenuItems(self):
        return list(self._context_menu)

    def isFolder(self):
        return bool(self._is_folder)

    def setIsFolder(self, is_folder):
        self._is_folder = bool(is_folder)

    def getVideoInfoTag(self):
        """InfoTagVideo (Kodi >= 20) — vista sobre o dict de info do item.

        O BrazucaPlay usa (descoberto pelo tracing da Fase 1). setInfo e
        os setters do InfoTag escrevem no mesmo dict, então os dois lados
        se veem.
        """
        return InfoTagVideo(self._info)


class InfoTagVideo:
    """InfoTag de vídeo — getters/setters sobre um dict (o do ListItem)."""

    def __init__(self, info=None):
        self._info = info if isinstance(info, dict) else {}

    # -- getters ------------------------------------------------------------
    def getTitle(self):
        return self._info.get("title", "")

    def getOriginalTitle(self):
        return self._info.get("originaltitle", "")

    def getTVShowTitle(self):
        return self._info.get("tvshowtitle", "")

    def getPlot(self):
        return self._info.get("plot", "")

    def getPlotOutline(self):
        return self._info.get("plotoutline", "")

    def getYear(self):
        try:
            return int(self._info.get("year", 0) or 0)
        except Exception:
            return 0

    def getSeason(self):
        try:
            return int(self._info.get("season", -1) if self._info.get("season") is not None else -1)
        except Exception:
            return -1

    def getEpisode(self):
        try:
            return int(self._info.get("episode", -1) if self._info.get("episode") is not None else -1)
        except Exception:
            return -1

    def getRating(self):
        try:
            return float(self._info.get("rating", 0.0) or 0.0)
        except Exception:
            return 0.0

    def getVotes(self):
        return str(self._info.get("votes", ""))

    def getPremiered(self):
        return str(self._info.get("premiered", ""))

    def getGenre(self):
        return str(self._info.get("genre", ""))

    def getDirector(self):
        return str(self._info.get("director", ""))

    def getCast(self):
        return list(self._info.get("cast", []) or [])

    def getIMDBNumber(self):
        return str(self._info.get("imdbnumber", ""))

    def getMediaType(self):
        return str(self._info.get("mediatype", ""))

    # -- setters (escrevem no mesmo dict do ListItem) ------------------------
    def setTitle(self, v):
        self._info["title"] = v

    def setOriginalTitle(self, v):
        self._info["originaltitle"] = v

    def setTVShowTitle(self, v):
        self._info["tvshowtitle"] = v

    def setPlot(self, v):
        self._info["plot"] = v

    def setPlotOutline(self, v):
        self._info["plotoutline"] = v

    def setYear(self, v):
        self._info["year"] = v

    def setSeason(self, v):
        self._info["season"] = v

    def setEpisode(self, v):
        self._info["episode"] = v

    def setRating(self, v):
        self._info["rating"] = v

    def setPremiered(self, v):
        self._info["premiered"] = v

    def setGenre(self, v):
        self._info["genre"] = v

    def setDirector(self, v):
        self._info["director"] = v

    def setCast(self, v):
        self._info["cast"] = v

    def setIMDBNumber(self, v):
        self._info["imdbnumber"] = v

    def setMediaType(self, v):
        self._info["mediatype"] = v


class Dialog:
    def notification(self, heading, message, icon=None, time=5000, sound=True):
        _log("xbmcgui.Dialog.notification", "%s: %s" % (heading, message))

    def input(self, heading, default="", type=INPUT_ALPHANUM, option=0, autoclose=0):
        _log("xbmcgui.Dialog.input", heading)
        return default

    def select(self, heading, list, autoclose=0, preselect=-1, useDetails=False):
        _log("xbmcgui.Dialog.select", heading)
        return -1

    def multiselect(self, heading, options, autoclose=0, preselect=None, useDetails=False):
        _log("xbmcgui.Dialog.multiselect", heading)
        return []

    def ok(self, heading, line1="", line2="", line3=""):
        _log("xbmcgui.Dialog.ok", heading)
        return True

    def yesno(self, heading, line1="", line2="", line3="", nolabel="", yeslabel="", autoclose=0):
        _log("xbmcgui.Dialog.yesno", heading)
        return False

    def textviewer(self, heading, text):
        _log("xbmcgui.Dialog.textviewer", heading)

    def numeric(self, type, heading, default=""):
        _log("xbmcgui.Dialog.numeric", heading)
        return default


class DialogProgress:
    def create(self, heading, line1="", line2="", line3=""):
        _log("xbmcgui.DialogProgress.create", heading)

    def update(self, percent, line1="", line2="", line3=""):
        pass

    def iscanceled(self):
        return False

    # Kodi aceita as duas grafias; o BrazucaPlay usa iscanceled.
    def isCanceled(self):
        return self.iscanceled()

    def close(self):
        pass


# Propriedades de janela compartilhadas por windowId (como no Kodi).
_window_props = {}


class Window:
    def __init__(self, windowId=-1):
        self._id = int(windowId)

    def _store(self):
        return _window_props.setdefault(self._id, {})

    def getProperty(self, key):
        return self._store().get(key, "")

    def setProperty(self, key, value):
        self._store()[key] = value

    def clearProperty(self, key):
        self._store().pop(key, None)

    def clearProperties(self):
        _window_props.pop(self._id, None)


class Control:
    """Base mínima (WindowXMLDialog/DialogProgressBG/Control* são Fase 2+,
    só se o tracing confirmar uso)."""


# Registra cada chamada xbmcgui* (nome, args, retorno) no log de
# chamadas (filesDir/kodi/xbmc_calls.log) — ver _aotv_kodi_log.record.
from _aotv_kodi_log import traced as _traced

for _cls, _methods in (
    (ListItem, ("setArt", "setInfo", "setProperty", "getProperty",
                "setLabel", "setLabel2", "setMimeType",
                "addContextMenuItems", "isFolder", "setCast",
                "setUniqueIDs", "setResumePoint", "getVideoInfoTag")),
    (InfoTagVideo, ("getTitle", "getOriginalTitle", "getTVShowTitle",
                    "getPlot", "getYear", "getSeason", "getEpisode",
                    "getRating", "getPremiered", "getGenre",
                    "getDirector", "getIMDBNumber", "getMediaType",
                    "setTitle", "setYear", "setSeason", "setEpisode")),
    (Dialog, ("notification", "input", "select", "multiselect", "ok",
              "yesno", "textviewer", "numeric")),
    (DialogProgress, ("create", "update", "iscanceled", "isCanceled",
                      "close")),
    (Window, ("getProperty", "setProperty", "clearProperty",
              "clearProperties")),
):
    for _m in _methods:
        setattr(_cls, _m, _traced(
            "xbmcgui.%s.%s" % (_cls.__name__, _m), getattr(_cls, _m)))
