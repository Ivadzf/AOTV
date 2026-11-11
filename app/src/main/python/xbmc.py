"""Módulo xbmc do Kodi — AOTV v1.1 (Fase 1).

Clean-room: reimplementação mínima a partir dos nomes/assinaturas públicos
da API Python do Kodi (docs.kodi.tv). Só o suficiente para os plugins de
vídeo rodarem; cada chamada registra (log) e devolve um valor neutro.
Não é código do Kodi (GPLv2).

Decisões aplicadas:
- getCondVisibility('System.HasAddon(inputstream.ffmpegdirect)') -> False
  (não existe no AOTV) para o addon cair no fallback do player Kodi.
- getCondVisibility('true') -> True (o init do .so do Vulgata exige).
- translatePath resolve special:// via aotv_kodi (lazy import).
- executebuiltin: subset Container.Refresh / ActivateWindow / RunPlugin /
  RunScript (RunPlugin despacha de verdade; RunScript é Fase 3, só registra).
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


def getInfoLabel(cLine):
    _log("xbmc.getInfoLabel", cLine)
    # O .so do Vulgata lê "System.BuildVersion" e verifica a versão do
    # Kodi; "" faria o check em C recusar (spike6).
    if cLine == "System.BuildVersion":
        return "21.0-Omega"
    if cLine == "System.BuildVersionCode":
        return "21.0"
    if cLine == "System.AddonVersion":
        return ""
    # Sem vídeo tocando, as chaves de Player/VideoPlayer são vazias.
    return ""


def getCondVisibility(condition):
    # Num Kodi de verdade, a condição 'true' é sempre verdadeira — o init
    # do .so do Vulgata chama getCondVisibility('true') e recusa quando
    # devolve False (teste do spike6). 'false' devolve False.
    _log("xbmc.getCondVisibility", condition)
    cond = str(condition).strip() if condition is not None else ""
    low = cond.lower()
    if low == "true":
        return True
    if low == "false":
        return False
    # inputstream.ffmpegdirect NÃO existe no AOTV — o addon cai no
    # fallback do player Kodi (que é o ExoPlayer). Qualquer outro addon
    # instalado responde True (via aotv_kodi, se souber); senão False.
    if low.startswith("system.hasaddon(") and low.endswith(")"):
        addon_id = cond[len("System.HasAddon("):-1].strip()
        if addon_id.lower() == "inputstream.ffmpegdirect":
            return False
        bridge = _bridge()
        if bridge is not None:
            try:
                return bool(bridge.find_addon_dir(addon_id))
            except Exception:
                return False
        return False
    return False


def executebuiltin(function):
    _log("xbmc.executebuiltin", function)
    if not isinstance(function, str):
        return
    fn = function.strip()
    low = fn.lower()
    # Container.Refresh / ActivateWindow: sem UI de addon na Fase 1 — só
    # registra (o addon re-lê o diretório na próxima chamada).
    if low.startswith("container.refresh") or low.startswith("activatewindow"):
        return
    # InstallAddon(<id ou caminho>): o BrazucaPlay tenta instalar os
    # inputstreams. No AOTV não há instalação de inputstream binário —
    # só registra (o addon segue sem ele, via getCondVisibility False).
    if low.startswith("installaddon("):
        _log("xbmc.executebuiltin",
             "InstallAddon sem efeito no AOTV: %s" % fn)
        return
    # SendClick(<id>): clique num controle de diálogo — sem UI na Fase 1.
    if low.startswith("sendclick("):
        return
    # UpdateLibrary / CleanLibrary / UpdateLocalAddons: biblioteca do Kodi —
    # sem biblioteca na Fase 1, só registra.
    if low.startswith(("updatelibrary(", "cleanlibrary(", "updatelocaladdons")):
        return
    # RunPlugin(plugin://<id>/<path>?<query>) — despacha de verdade.
    if low.startswith("runplugin(") and fn.endswith(")"):
        inner = fn[len("RunPlugin("):-1].strip().strip("\"'")
        _run_plugin_url(inner)
        return
    # RunScript(...) — entrada script (BrazucaPlay); dispatch é Fase 3.
    if low.startswith("runscript("):
        _log("xbmc.executebuiltin", "RunScript é Fase 3 (só registrado): %s" % fn)
        return


def _run_plugin_url(url):
    """Despacha uma URL plugin:// via aotv_kodi (para o RunPlugin)."""
    bridge = _bridge()
    if bridge is None:
        return
    try:
        from urllib.parse import urlparse
        parsed = urlparse(url)
        if parsed.scheme != "plugin":
            return
        addon_id = parsed.netloc
        path = parsed.path or ""
        query = parsed.query or ""
        bridge.run_plugin(addon_id, path, query, handle=1)
    except Exception as e:
        _log("xbmc.executebuiltin", "RunPlugin falhou: %s" % e)


class Monitor:
    def abortRequested(self):
        return False

    def waitForAbort(self, timeout=0):
        import time
        if timeout:
            time.sleep(timeout)
        return False


class Player:
    """Player mínimo da Fase 1 (ação + estado neutro). A 2ª camada de
    legendas (a4kSubtitles, Fase 2+) retroalimenta este Player com o
    estado real do ExoPlayer (getVideoInfoTag, getAvailableSubtitleStreams,
    setSubtitleStream) — aqui ele só não mente: nada tocando."""

    def play(self, item=None, listitem=None, windowed=False, startpos=-1):
        _log("xbmc.Player.play", str(item))

    def stop(self):
        _log("xbmc.Player.stop", "")

    def isPlaying(self):
        return False

    def isPlayingVideo(self):
        return False

    def isPlayingAudio(self):
        return False

    def getPlayingFile(self):
        return ""

    def getTime(self):
        return 0.0

    def getTotalTime(self):
        return 0.0

    def seekTime(self, seekTime):
        pass

    def pause(self):
        pass


# Registra cada chamada xbmc* (nome, args, retorno) no log de
# chamadas (filesDir/kodi/xbmc_calls.log) — ver _aotv_kodi_log.record.
from _aotv_kodi_log import traced as _traced

for _fn in ("log", "sleep", "getInfoLabel", "getCondVisibility",
            "executebuiltin", "translatePath"):
    globals()[_fn] = _traced("xbmc." + _fn, globals()[_fn])
for _cls, _methods in (
    (Monitor, ("abortRequested", "waitForAbort")),
    (Player, ("play", "stop", "isPlaying", "isPlayingVideo",
              "isPlayingAudio", "getPlayingFile", "getTime",
              "getTotalTime", "seekTime", "pause")),
):
    for _m in _methods:
        setattr(_cls, _m, _traced(
            "xbmc.%s.%s" % (_cls.__name__, _m), getattr(_cls, _m)))
