"""Stub mínimo do módulo xbmc do Kodi — spike da v1.1 (Fase 0).

Clean-room: reimplementação mínima a partir dos nomes/assinaturas públicos
da API Python do Kodi (docs.kodi.tv). Só o suficiente para o import e o
loader do Vulgata funcionarem; cada chamada só registra (log) e devolve um
valor neutro. Não é código do Kodi (GPLv2).
"""

from _aotv_kodi_log import log as _log

# Níveis de log (o Vulgata usa int direto; o a4kSubtitles usa as constantes).
LOGDEBUG = 0
LOGINFO = 1
LOGNOTICE = 2
LOGWARNING = 3
LOGERROR = 4
LOGFATAL = 5
LOGNONE = 6

ISO_639_1 = 0
ISO_639_2 = 1
ENGLISH_NAME = 2


def log(msg, level=LOGINFO):
    _log("xbmc.log", "%s (level=%s)" % (msg, level))


def sleep(milliseconds):
    import time
    time.sleep(milliseconds / 1000.0)


def getInfoLabel(cLine):
    _log("xbmc.getInfoLabel", cLine)
    return ""


def getCondVisibility(condition):
    # Spike: tudo False — inclusive System.HasAddon(inputstream.ffmpegdirect),
    # para o addon cair no fallback do player Kodi (ver plano §2.5).
    _log("xbmc.getCondVisibility", condition)
    return False


def executebuiltin(function):
    _log("xbmc.executebuiltin", function)


def translatePath(path):
    _log("xbmc.translatePath", path)
    return path


class Monitor:
    def abortRequested(self):
        return False

    def waitForAbort(self, timeout=0):
        import time
        if timeout:
            time.sleep(timeout)
        return False


class Player:
    def play(self, item=None, listitem=None, windowed=False, startpos=-1):
        _log("xbmc.Player.play", str(item))

    def stop(self):
        _log("xbmc.Player.stop", "")

    def isPlaying(self):
        return False

    def isPlayingVideo(self):
        return False

    def getPlayingFile(self):
        return ""
