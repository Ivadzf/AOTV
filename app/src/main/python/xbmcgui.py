"""Stub mínimo do módulo xbmcgui do Kodi — spike da v1.1 (Fase 0).

Clean-room (nomes/assinaturas públicos da API Python do Kodi). Cada chamada
só registra (log) e devolve um valor neutro.
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
    def __init__(self, label="", label2="", path=""):
        self._label = label
        self._label2 = label2
        self._path = path
        self._props = {}

    def setArt(self, art):
        _log("xbmcgui.ListItem.setArt", str(art)[:200])

    def setInfo(self, type, infoLabels):
        _log("xbmcgui.ListItem.setInfo", type)

    def setProperty(self, key, value):
        self._props[key] = value

    def getProperty(self, key):
        return self._props.get(key, "")

    def setLabel(self, label):
        self._label = label

    def setLabel2(self, label2):
        self._label2 = label2

    def setMimeType(self, mimetype):
        _log("xbmcgui.ListItem.setMimeType", mimetype)

    def addContextMenuItems(self, items, replaceItems=False):
        pass

    def isFolder(self):
        return False


class Dialog:
    def notification(self, heading, message, icon=None, time=5000, sound=True):
        _log("xbmcgui.Dialog.notification", "%s: %s" % (heading, message))

    def input(self, heading, default="", type=INPUT_ALPHANUM, option=0, autoclose=0):
        _log("xbmcgui.Dialog.input", heading)
        return default

    def select(self, heading, list, autoclose=0, preselect=-1, useDetails=False):
        _log("xbmcgui.Dialog.select", heading)
        return -1

    def ok(self, heading, line1="", line2="", line3=""):
        _log("xbmcgui.Dialog.ok", heading)
        return True


class DialogProgress:
    def create(self, heading, line1="", line2="", line3=""):
        _log("xbmcgui.DialogProgress.create", heading)

    def update(self, percent, line1="", line2="", line3=""):
        pass

    def iscanceled(self):
        return False

    def close(self):
        pass


class Window:
    def __init__(self, windowId=-1):
        self._props = {}

    def getProperty(self, key):
        return self._props.get(key, "")

    def setProperty(self, key, value):
        self._props[key] = value

    def clearProperty(self, key):
        self._props.pop(key, None)


class Control:
    pass
