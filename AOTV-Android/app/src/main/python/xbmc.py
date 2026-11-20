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
- Notification(...) despacha p/ xbmcgui.Dialog().notification.
- Monitor com evento de abort real (Fase 3): waitForAbort bloqueia de
  verdade até o abort (ou o timeout); request_abort/clear_abort são o
  controle do KodiServiceRunner (o abort é global, como no Kodi).
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
    # Chaves de playback (Fase 2): refletem o ExoPlayer via ponte; sem
    # nada tocando, são vazias (como num Kodi sem vídeo).
    if cLine in ("Player.Filenameandpath", "Player.FilenameAndPath"):
        return _playing_file()
    if cLine == "Player.Filename":
        f = _playing_file()
        return f.rsplit("/", 1)[-1] if f else ""
    if cLine in ("Player.Title", "VideoPlayer.Title"):
        return _playing_title()
    # Sem vídeo tocando, as demais chaves de Player/VideoPlayer são vazias.
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
    # RunScript(addon_id[, func_name]) — despacha de verdade (Fase 3).
    if low.startswith("runscript(") and fn.endswith(")"):
        inner = fn[len("RunScript("):-1].strip()
        args = _split_builtin_args(inner)
        if args:
            _run_script(args[0], args[1] if len(args) > 1 else "")
        return
    # Notification(heading, message[, time[, icon[, sound]]]) — a ordem
    # do builtin é heading, message, TIME, icon (diferente da do
    # Dialog.notification Python, que é heading, message, icon, time).
    if low.startswith("notification(") and fn.endswith(")"):
        inner = fn[len("Notification("):-1].strip()
        args = _split_builtin_args(inner)
        if len(args) >= 2:
            try:
                import xbmcgui
                time_ms = 5000
                if len(args) >= 3:
                    try:
                        time_ms = int(args[2])
                    except Exception:
                        time_ms = 5000
                icon = args[3] if len(args) >= 4 else None
                xbmcgui.Dialog().notification(
                    args[0], args[1], icon=icon, time=time_ms)
            except Exception as e:
                _log("xbmc.executebuiltin", "Notification falhou: %s" % e)
        return


def _split_builtin_args(inner):
    """Divide os args de um builtin por vírgula, respeitando aspas.

    O Kodi aceita args com ou sem aspas; o BrazucaPlay monta
    Notification sem aspas em volta dos valores (só o heading pode
    vir com aspas). Aspas simples/duplas em volta de um arg são
    removidas; vírgula dentro de aspas não separa.
    """
    args = []
    cur = []
    quote = None
    for ch in inner:
        if quote is not None:
            if ch == quote:
                quote = None
            else:
                cur.append(ch)
        elif ch in ("'", '"'):
            quote = ch
        elif ch == ",":
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    args.append("".join(cur).strip())
    return [a for a in args if a != ""]


def _run_script(addon_id, func_name):
    """Despacha um RunScript via aotv_kodi (para o executebuiltin)."""
    bridge = _bridge()
    if bridge is None:
        return
    try:
        bridge.run_script(addon_id, func_name)
    except Exception as e:
        _log("xbmc.executebuiltin", "RunScript falhou: %s" % e)


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


# Evento de abort do Monitor (Fase 3). No Kodi o abort é do aplicativo
# (global, não por addon): Monitor.abortRequested() lê este evento e
# waitForAbort() bloqueia nele. O KodiServiceRunner limpa o evento ao
# iniciar um serviço e o seta ao pará-lo; só um serviço roda por vez.
import threading as _threading

_abort_event = _threading.Event()


def request_abort():
    """Pede o abort de todo Monitor (o serviço sai do waitForAbort)."""
    _abort_event.set()


def clear_abort():
    """Limpa o pedido de abort (antes de iniciar um serviço novo)."""
    _abort_event.clear()


def abort_requested():
    return _abort_event.is_set()


class Monitor:
    def abortRequested(self):
        return _abort_event.is_set()

    def waitForAbort(self, timeout=0):
        """Bloqueia até o abort ou o timeout (segundos, como no Kodi).

        timeout <= 0: não bloqueia — devolve o estado atual (é a
        semântica do WaitMSec(0) do Kodi: poll instantâneo). timeout
        > 0: espera até timeout segundos; devolve True se abortou
        durante a espera, False se o tempo esgotou. Na Fase 1 este
        método só dormia o timeout e sempre devolvia False — o
        serviço do Vulgata nunca sairia do loop sem o abort real.
        """
        try:
            timeout = float(timeout or 0)
        except Exception:
            timeout = 0
        if timeout <= 0:
            return _abort_event.is_set()
        return _abort_event.wait(timeout)


class Player:
    """Player da Fase 2: reflete o ExoPlayer de verdade via KodiPlayerBridge.

    A ponte (Kotlin, `object` com métodos @JvmStatic) é importada do
    Python pelo Chaquopy; sem ela (teste local, sem Android), tudo
    devolve o valor neutro — o mesmo comportamento da Fase 1. O addon
    acompanha o playback (isPlaying/getTime/seek/pause) e o play/stop
    do addon operam no player do app. A 2ª camada de legendas
    (a4kSubtitles) ainda precisa de getVideoInfoTag/getAvailableSubtitleStreams/
    setSubtitleStream — fora da Fase 2.
    """

    def play(self, item=None, listitem=None, windowed=False, startpos=-1):
        _log("xbmc.Player.play", str(item))
        bridge = _player_bridge()
        if bridge is None:
            return
        try:
            path = ""
            if isinstance(item, str):
                path = item
            elif listitem is not None:
                try:
                    path = listitem.getPath()
                except Exception:
                    path = ""
            if path:
                bridge.update(path, "")
            bridge.play()
        except Exception:
            pass

    def stop(self):
        _log("xbmc.Player.stop", "")
        bridge = _player_bridge()
        if bridge is None:
            return
        try:
            bridge.stop()
        except Exception:
            pass

    def isPlaying(self):
        bridge = _player_bridge()
        if bridge is not None:
            try:
                return bool(bridge.isPlaying())
            except Exception:
                pass
        return False

    def isPlayingVideo(self):
        bridge = _player_bridge()
        if bridge is not None:
            try:
                return bool(bridge.isPlayingVideo())
            except Exception:
                pass
        return False

    def isPlayingAudio(self):
        return False

    def getPlayingFile(self):
        return _playing_file()

    def getTime(self):
        bridge = _player_bridge()
        if bridge is not None:
            try:
                return float(bridge.getTime())
            except Exception:
                pass
        return 0.0

    def getTotalTime(self):
        bridge = _player_bridge()
        if bridge is not None:
            try:
                return float(bridge.getTotalTime())
            except Exception:
                pass
        return 0.0

    def seekTime(self, seekTime):
        bridge = _player_bridge()
        if bridge is None:
            return
        try:
            bridge.seekTime(float(seekTime))
        except Exception:
            pass

    def pause(self):
        bridge = _player_bridge()
        if bridge is None:
            return
        try:
            bridge.pause()
        except Exception:
            pass


# ---------------------------------------------------------------------------
# Ponte de playback (KodiPlayerBridge, Kotlin) — memoizada; None sem Android.
# ---------------------------------------------------------------------------

_player_bridge_cache = {"tried": False, "bridge": None}


def _player_bridge():
    """A ponte Kotlin (None sem Chaquopy — teste local)."""
    if _player_bridge_cache["tried"]:
        return _player_bridge_cache["bridge"]
    _player_bridge_cache["tried"] = True
    try:
        from com.aotv.app.data.kodi import KodiPlayerBridge
        _player_bridge_cache["bridge"] = KodiPlayerBridge
    except Exception:
        _player_bridge_cache["bridge"] = None
    return _player_bridge_cache["bridge"]


def _playing_file():
    bridge = _player_bridge()
    if bridge is not None:
        try:
            return bridge.getPlayingFile() or ""
        except Exception:
            pass
    return ""


def _playing_title():
    bridge = _player_bridge()
    if bridge is not None:
        try:
            return bridge.getTitle() or ""
        except Exception:
            pass
    return ""


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
