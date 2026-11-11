"""Ponte do runtime Kodi do AOTV (v1.1, Fase 1).

Liga os stubs xbmc* ao app: resolve os caminhos special://, despacha
plugins (plugin://...), coleta os itens devolvidos e a URL resolvida,
traduz ListItem para dict (que o Kotlin lê via Chaquopy) e lê os
settings/addon.xml dos addons instalados.

Clean-room: reimplementação a partir dos contratos públicos da API
Python do Kodi (docs.kodi.tv) e do formato de addon.xml/settings.xml
do Kodi. Não é código do Kodi (GPLv2) nem dos addons.

Caminhos (configurados pelo Kotlin depois do Python.start):
    special://home    -> <filesDir>/kodi
    special://profile -> <filesDir>/kodi/userdata
    special://temp    -> <cacheDir>/kodi
    special://home/addons      -> <filesDir>/kodi/addons
    special://profile/addon_data -> <filesDir>/kodi/userdata/addon_data

Decisões do Davi (§9) aplicadas aqui:
    - Vulgata: analytics_enabled e automatic_updates sempre "false"
      (presença/analytics e self-update DESLIGADOS).
    - getCondVisibility('System.HasAddon(inputstream.ffmpegdirect)')
      responde False (em xbmc.py) para o addon cair no fallback do
      player Kodi (que é o ExoPlayer).
"""

import os
import sys
import threading
import time
import xml.etree.ElementTree as ET

from _aotv_kodi_log import log as _log

# Execução Python SERIALIZADA: um addon por vez. O dispatch muta
# sys.argv global e o coletor por handle — duas execuções simultâneas
# misturariam os dois. O lock garante que nunca haja concorrência,
# mesmo que o Kotlin chame de threads diferentes (o KodiRuntime da
# Fase 2 também serializa, mas a garantia vive aqui).
_run_lock = threading.Lock()

# ---------------------------------------------------------------------------
# special:// — raízes (configuradas pelo Kotlin; sem configure, devolve o
# caminho como veio, para o addon não quebrar em teste sem Android).
# ---------------------------------------------------------------------------

_roots = {
    "home": None,      # <filesDir>/kodi
    "profile": None,   # <filesDir>/kodi/userdata
    "temp": None,      # <cacheDir>/kodi
}
_addons_dir = None     # <filesDir>/kodi/addons
_current_addon = ""    # id do addon em execução (sys.argv / Addon() sem id)

# (addon_id, setting_id) -> valor forçado. Decisão do Davi (§9): no
# Vulgata, presença/analytics e self-update são DESLIGADOS — o addon lê
# "false" mesmo que o default dele seja "true".
_FORCED_SETTINGS = {
    ("plugin.video.vulgata", "analytics_enabled"): "false",
    ("plugin.video.vulgata", "automatic_updates"): "false",
}


# ---------------------------------------------------------------------------
# Sonda HTTP (v1.2.4) — o que o addon buscou na rede durante o dispatch.
# ---------------------------------------------------------------------------
# O diag da v1.2.3 provou que o roteamento começa (Carregando...) mas o
# fetch volta vazio sem erro e sem guarda, no Destaques E na TV (a TV dá
# 12 itens na VM). O addon usa requests (script.module.requests, com
# timeout (10, 25) e UA de Chrome — provado na VM) e ENGLOBE qualquer
# exceção do fetch: ConnectTimeout, SSLError, ConnectionError ou 403
# viram "0 itens, endDir=falso, sem erro" — idêntico ao sintoma do
# celular (probes /tmp/probe_http_fail.py e /tmp/probe_http_sel.py).
# O .so não deixa ver o erro por dentro; a única forma de distinguir
# as causas no celular é observar as chamadas HTTP por fora: o
# Chaquopy tem UM interpretador, então envolver
# requests.Session.request (e urllib.request.urlopen) em configure()
# intercepta também as chamadas do addon. A sonda registra método,
# URL, status ou tipo da exceção, tamanho e tempo — e nunca levanta
# (um erro na sonda não pode quebrar o fetch do addon).
#
# A mesma sonda aplica um piso de timeout nos fetches de conteúdo:
# as páginas têm 330–600 KB e levam 1,1–1,8 s num link rápido; na
# internet lenta do Davi, o (10, 25) do addon pode estourar por
# stalls (o read timeout é por leitura, não total — basta uma pausa
# > 25 s entre pacotes). O piso (30, 60) só vale p/ timeouts já
# "longos" (o fetch de conteúdo); o check rápido (1,5, 2) do gist
# continua rápido. O Kotlin não tem timeout na chamada Python
# (KodiPluginRunner usa withContext sem withTimeout), então o piso
# não é cortado pelo app.
# ---------------------------------------------------------------------------

_http_calls = []          # (método, url, resultado, ms) — últimas 30
_HTTP_MAX_CALLS = 30
_HTTP_TIMEOUT_FLOOR = (30, 60)   # (connect, read) p/ fetches de conteúdo
_HTTP_MIN_CONNECT = 5            # a partir daqui é "fetch de conteúdo"
_HTTP_MIN_READ = 10
_http_probe_state = {"requests": "?", "urllib": "?"}


def _http_record(method, url, outcome, ms):
    """Guarda uma chamada HTTP (nunca levanta)."""
    try:
        _http_calls.append((method, url, outcome, int(ms)))
        if len(_http_calls) > _HTTP_MAX_CALLS:
            del _http_calls[:len(_http_calls) - _HTTP_MAX_CALLS]
    except Exception:
        pass


def _http_floor_timeout(timeout):
    """Piso de timeout p/ fetches de conteúdo. Devolve o timeout a usar.

    Só mexe em tupla (connect, read) já "longa" (connect >= 5 ou
    read >= 10 — o (10, 25) do conteúdo); timeout curto (o (1,5, 2)
    do check de update), número único ou None passam intactos.
    Nunca levanta: qualquer dúvida, devolve o timeout original.
    """
    try:
        if isinstance(timeout, (tuple, list)) and len(timeout) == 2:
            c, r = timeout
            if (c is not None and c >= _HTTP_MIN_CONNECT) or \
               (r is not None and r >= _HTTP_MIN_READ):
                nc = max(c, _HTTP_TIMEOUT_FLOOR[0]) if c is not None else c
                nr = max(r, _HTTP_TIMEOUT_FLOOR[1]) if r is not None else r
                return (nc, nr)
    except Exception:
        pass
    return timeout


def _install_http_probe():
    """Instala a sonda HTTP (idempotente, com marcador no wrapper).

    Envolve requests.Session.request e urllib.request.urlopen. Se o
    requests não estiver instalado, registra o estado e continua só
    com o urllib (o diag mostra "requests ausente").

    Cada wrapper é criado por uma fábrica própria: sem ela, os dois
    fechariam sobre a MESMA variável `orig` do escopo desta função
    (rebinding tardio — o wrapper do requests passaria a chamar o
    urlopen).
    """
    import functools

    def _wrap_session_request(orig):
        @functools.wraps(orig)
        def _probed_request(self, method, url, **kwargs):
            t0 = time.monotonic()
            try:
                kwargs["timeout"] = _http_floor_timeout(
                    kwargs.get("timeout", None))
            except Exception:
                pass
            try:
                resp = orig(self, method, url, **kwargs)
                ms = (time.monotonic() - t0) * 1000
                try:
                    # _content já está em memória p/ respostas
                    # não-streaming (é o caso do addon); False
                    # significa streaming — não dispara download.
                    body = getattr(resp, "_content", None)
                    if body is None or body is False:
                        kb = "?"
                    else:
                        n = len(body)
                        kb = ("%dKB" % (n // 1024)) if n >= 1024 \
                            else ("%dB" % n)
                except Exception:
                    kb = "?"
                _http_record(method, url,
                             "%s %s" % (resp.status_code, kb), ms)
                return resp
            except BaseException as e:
                ms = (time.monotonic() - t0) * 1000
                _http_record(method, url,
                             "ERRO %s" % type(e).__name__, ms)
                raise

        _probed_request._aotv_http_probe = True
        return _probed_request

    def _wrap_urlopen(orig):
        @functools.wraps(orig)
        def _probed_urlopen(url, *args, **kwargs):
            t0 = time.monotonic()
            u = url if isinstance(url, str) else getattr(
                url, "full_url", str(url))
            try:
                r = orig(url, *args, **kwargs)
                ms = (time.monotonic() - t0) * 1000
                try:
                    st = getattr(r, "status", "?")
                except Exception:
                    st = "?"
                _http_record("urllib", u, "%s" % st, ms)
                return r
            except BaseException as e:
                ms = (time.monotonic() - t0) * 1000
                _http_record("urllib", u,
                             "ERRO %s" % type(e).__name__, ms)
                raise

        _probed_urlopen._aotv_http_probe = True
        return _probed_urlopen

    # --- requests (o Vulgata e o BrazucaPlay usam) ---
    try:
        import requests.sessions
        cur = requests.sessions.Session.request
        if not getattr(cur, "_aotv_http_probe", False):
            requests.sessions.Session.request = \
                _wrap_session_request(cur)
            _http_probe_state["requests"] = "ok"
        else:
            _http_probe_state["requests"] = "ok"
    except Exception as e:
        _http_probe_state["requests"] = "ausente: %s" % type(e).__name__
    # --- urllib (rede de segurança; ex.: BrazucaPlay) ---
    try:
        import urllib.request
        cur = urllib.request.urlopen
        if not getattr(cur, "_aotv_http_probe", False):
            urllib.request.urlopen = _wrap_urlopen(cur)
            _http_probe_state["urllib"] = "ok"
        else:
            _http_probe_state["urllib"] = "ok"
    except Exception as e:
        _http_probe_state["urllib"] = "ausente: %s" % type(e).__name__


def _http_summary(n0):
    """Resumo das chamadas HTTP durante um dispatch (p/ o diag).

    Por chamada: "GET host/path STATUS TAM MS" ou
    "GET host/path ERRO Tipo MS". Sem chamadas: "nenhuma" (+ aviso
    se o requests estiver ausente — aí o fetch nem aconteceu por
    falta da biblioteca). Nunca levanta.
    """
    try:
        calls = _http_calls[n0:] if len(_http_calls) >= n0 \
            else list(_http_calls)
    except Exception:
        return "http: ?"
    if not calls:
        extra = ""
        try:
            if _http_probe_state.get("requests", "?") != "ok":
                extra = " (requests %s)" % _http_probe_state["requests"]
        except Exception:
            pass
        return "http: nenhuma%s" % extra
    parts = []
    for method, url, outcome, ms in calls:
        try:
            from urllib.parse import urlparse
            p = urlparse(url)
            short = (p.netloc + p.path)[:64]
        except Exception:
            short = (url or "")[:64]
        parts.append("%s %s %s %dms" % (method, short, outcome, ms))
    return "http: " + "; ".join(parts)


def configure(files_dir, cache_dir):
    """Define as raízes special:// a partir dos dirs do app (Kotlin)."""
    global _addons_dir
    home = os.path.join(files_dir, "kodi")
    _roots["home"] = home
    _roots["profile"] = os.path.join(home, "userdata")
    _roots["temp"] = os.path.join(cache_dir, "kodi")
    _addons_dir = os.path.join(home, "addons")
    for d in (home, _roots["profile"], _roots["temp"], _addons_dir,
              os.path.join(_roots["profile"], "addon_data")):
        try:
            os.makedirs(d, exist_ok=True)
        except Exception:
            pass
    # Sonda HTTP (v1.2.4): intercepta os fetches dos addons p/ o diag
    # (e aplica o piso de timeout nos fetches de conteúdo). O Kotlin
    # chama configure() logo após o Python.start, antes de qualquer
    # dispatch — o addon importa o requests depois, já sondado.
    _install_http_probe()
    _log("aotv_kodi.configure", "home=%s temp=%s" % (home, _roots["temp"]))


def set_addons_dir(path):
    """Sobrescreve o dir de addons (só para teste local sem Android)."""
    global _addons_dir
    _addons_dir = path


def get_addons_dir():
    return _addons_dir or ""


def translate_special(path):
    """Resolve special://... para caminho real. Sem raiz, devolve como veio."""
    if not isinstance(path, str) or not path.startswith("special://"):
        return path
    rest = path[len("special://"):]
    # special://home/addons/x -> raiz "home" + resto "addons/x".
    parts = rest.split("/", 1)
    scheme = parts[0].lower()
    tail = parts[1] if len(parts) > 1 else ""
    # Atalhos conhecidos do Kodi.
    if scheme == "home":
        root = _roots.get("home")
    elif scheme == "profile":
        root = _roots.get("profile")
    elif scheme == "temp":
        root = _roots.get("temp")
    elif scheme == "masterprofile":
        root = _roots.get("profile")
    elif scheme == "userdata":
        root = _roots.get("profile")
    elif scheme == "xbmcbin":
        # Não existe no AOTV (o Elementum cai para o próximo candidato).
        return path
    else:
        return path
    if root is None:
        return path
    return os.path.join(root, tail) if tail else root


def addon_data_dir(addon_id):
    """Dir de dados de um addon (settings/cookies/cache)."""
    profile = _roots.get("profile")
    base = os.path.join(profile, "addon_data") if profile else None
    if base is None:
        return ""
    d = os.path.join(base, addon_id)
    try:
        os.makedirs(d, exist_ok=True)
    except Exception:
        pass
    return d


# ---------------------------------------------------------------------------
# Contexto do addon em execução.
# ---------------------------------------------------------------------------

def set_current_addon(addon_id):
    global _current_addon
    _current_addon = addon_id or ""


def get_current_addon():
    return _current_addon


# ---------------------------------------------------------------------------
# addon.xml — leitura (id, nome, versão, extension points, requires).
# ---------------------------------------------------------------------------

def find_addon_dir(addon_id):
    """Acha o dir de um addon instalado (ou "" se não instalado)."""
    if not addon_id:
        return ""
    base = _addons_dir
    if base:
        d = os.path.join(base, addon_id)
        if os.path.isfile(os.path.join(d, "addon.xml")):
            return d
    return ""


def _parse_addon_xml(addon_dir):
    """Devolve um dict com os campos do addon.xml (ou {} se falhar)."""
    info = {}
    try:
        tree = ET.parse(os.path.join(addon_dir, "addon.xml"))
        root = tree.getroot()
    except Exception:
        return info
    if root.tag != "addon":
        return info
    info["id"] = root.get("id", "")
    info["name"] = root.get("name", "")
    info["version"] = root.get("version", "")
    info["provider-name"] = root.get("provider-name", "")
    # Extension points: [(point, library)].
    exts = []
    for ext in root.findall("extension"):
        exts.append((ext.get("point", ""), ext.get("library", "")))
    info["extensions"] = exts
    # Requires: [(addon, version, optional)].
    reqs = []
    req_el = root.find("requires")
    if req_el is not None:
        for imp in req_el.findall("import"):
            reqs.append((imp.get("addon", ""), imp.get("version", ""),
                         imp.get("optional", "false") == "true"))
    info["requires"] = reqs
    # Metadata: assets (icon/fanart) e summary.
    meta = root.find("extension[@point='xbmc.addon.metadata']")
    if meta is not None:
        assets = meta.find("assets")
        if assets is not None:
            icon = assets.find("icon")
            fanart = assets.find("fanart")
            if icon is not None and icon.text:
                info["icon"] = icon.text.strip()
            if fanart is not None and fanart.text:
                info["fanart"] = fanart.text.strip()
        summary = meta.find("summary")
        if summary is not None and summary.text:
            info["summary"] = summary.text.strip()
    return info


def get_addon_info(addon_id, info_id):
    """xbmcaddon.Addon.getAddonInfo — lê do addon.xml (string)."""
    addon_id = addon_id or _current_addon
    if not addon_id:
        return ""
    d = find_addon_dir(addon_id)
    if not d:
        return ""
    info = _parse_addon_xml(d)
    key = str(info_id or "").lower()
    if key == "id":
        return info.get("id", addon_id)
    if key == "name":
        return info.get("name", addon_id)
    if key == "version":
        return info.get("version", "")
    if key == "author":
        return info.get("provider-name", "")
    if key == "path":
        return d
    if key == "profile":
        return addon_data_dir(addon_id)
    if key == "icon":
        icon = info.get("icon", "")
        return os.path.join(d, icon) if icon else ""
    if key == "fanart":
        fanart = info.get("fanart", "")
        return os.path.join(d, fanart) if fanart else ""
    if key == "summary":
        return info.get("summary", "")
    if key == "type":
        return "xbmc.python.pluginsource"
    return ""


def get_plugin_library(addon_id):
    """Biblioteca do extension point xbmc.python.pluginsource (main.py)."""
    d = find_addon_dir(addon_id)
    if not d:
        return ""
    info = _parse_addon_xml(d)
    for point, library in info.get("extensions", []):
        if point == "xbmc.python.pluginsource" and library:
            return os.path.join(d, library)
    return ""


# ---------------------------------------------------------------------------
# settings.xml — definições + valores persistidos (formato do Kodi).
# ---------------------------------------------------------------------------

def _parse_settings_xml(addon_dir):
    """Devolve {setting_id: (type, default, values)} de resources/settings.xml."""
    defs = {}
    path = os.path.join(addon_dir, "resources", "settings.xml")
    if not os.path.isfile(path):
        return defs
    try:
        tree = ET.parse(path)
        root = tree.getroot()
    except Exception:
        return defs
    for el in root.iter("setting"):
        sid = el.get("id")
        if not sid:
            continue
        defs[sid] = (el.get("type", "text"), el.get("default", ""),
                     el.get("values", ""))
    return defs


def _read_user_settings(addon_id):
    """Valores persistidos em addon_data/<id>/settings.xml (formato Kodi)."""
    values = {}
    d = addon_data_dir(addon_id)
    if not d:
        return values
    path = os.path.join(d, "settings.xml")
    if not os.path.isfile(path):
        return values
    try:
        tree = ET.parse(path)
        root = tree.getroot()
    except Exception:
        return values
    for el in root.iter("setting"):
        sid = el.get("id")
        if sid:
            values[sid] = el.get("value", "")
    return values


def get_setting(addon_id, setting_id):
    """xbmcaddon.Addon.getSetting — sempre string (formato do Kodi).

    Ordem: valor forçado (decisão do Davi) > valor persistido >
    default do settings.xml > "".
    """
    addon_id = addon_id or _current_addon
    if not addon_id or not setting_id:
        return ""
    forced = _FORCED_SETTINGS.get((addon_id, str(setting_id)))
    if forced is not None:
        return forced
    user = _read_user_settings(addon_id)
    if str(setting_id) in user:
        return user[str(setting_id)]
    d = find_addon_dir(addon_id)
    if d:
        defs = _parse_settings_xml(d)
        entry = defs.get(str(setting_id))
        if entry is not None:
            return entry[1]
    return ""


def set_setting(addon_id, setting_id, value):
    """Persiste um setting em addon_data/<id>/settings.xml (formato Kodi)."""
    addon_id = addon_id or _current_addon
    if not addon_id or not setting_id:
        return False
    d = addon_data_dir(addon_id)
    if not d:
        return False
    path = os.path.join(d, "settings.xml")
    values = _read_user_settings(addon_id)
    values[str(setting_id)] = str(value)
    try:
        root = ET.Element("settings")
        for sid in sorted(values):
            el = ET.SubElement(root, "setting")
            el.set("id", sid)
            el.set("value", values[sid])
        tree = ET.ElementTree(root)
        tree.write(path, encoding="utf-8", xml_declaration=True)
        return True
    except Exception:
        return False


# ---------------------------------------------------------------------------
# Dispatch de plugin + coleta dos itens devolvidos.
# ---------------------------------------------------------------------------

def _ensure_simplejson():
    """simplejson não tem wheel Android p/ o Chaquopy (--only-binary).
    Se o pacote real não estiver disponível, instala um shim em
    sys.modules que mapeia simplejson -> json (stdlib). A API usada
    pelos addons (loads/dumps/load/dump) é compatível."""
    try:
        import simplejson  # noqa: F401
        return
    except Exception:
        pass
    try:
        import json
        sys.modules["simplejson"] = json
        _log("aotv_kodi", "simplejson -> shim json (stdlib)")
    except Exception:
        pass


def _collector():
    """O coletor do xbmcplugin (itens por handle + URL resolvida)."""
    try:
        import xbmcplugin
        return xbmcplugin
    except Exception:
        return None


def run_plugin(addon_id, path="", query="", handle=1):
    """Executa um plugin como o Kodi faria e devolve o resultado.

    Serializado (_run_lock): um addon por vez, nunca em paralelo.
    sys.argv = ["plugin://<id>/<path>", "<handle>", "<query>"].
    Retorna dict: {"items": [dict...], "resolved_url": str,
    "succeeded": bool, "error": str, "handle": int}.
    Os itens são a tradução de cada ListItem (ver listitem_to_dict).
    """
    with _run_lock:
        return _run_plugin_inner(addon_id, path, query, handle)


def _run_arity(run):
    """Confere se run aceita 1 arg posicional. Devolve (ok, nota).

    Camadas, da mais estrita p/ a mais tolerante: __code__.co_argcount
    (cyfunction de Cython 3 tem); inspect.signature (cobre builtins e
    cyfunction sem __code__); se nenhuma introspecção funcionar, aceita
    e deixa a chamada decidir — um TypeError vira erro visível no
    resultado, não um vazio silencioso. Um .so arm64 compilado com
    Cython antigo pode não expor __code__; rejeitar por isso era o
    que deixava o 2º dispatch cair no runpy (no-op) sem erro.
    """
    try:
        code = run.__code__
    except Exception:
        code = None
    if code is not None:
        try:
            argc = code.co_argcount
            return (argc == 1, "argc=%d" % argc)
        except Exception:
            pass
    try:
        import inspect
        sig = inspect.signature(run)
        params = [p for p in sig.parameters.values()
                  if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD)]
        required = [p for p in params if p.default is p.empty]
        has_varargs = any(p.kind == p.VAR_POSITIONAL
                          for p in sig.parameters.values())
        ok = len(required) <= 1 and (len(params) >= 1 or has_varargs)
        return (ok, "sig=%s" % sig)
    except Exception:
        pass
    return (True, "assinatura? (tentar)")


def _entry_run_from_module(mod, lib_real, exact_file=True):
    """Valida o run(argv) de um módulo candidato. Devolve (run, nota)."""
    try:
        f = getattr(mod, "__file__", None) or ""
    except Exception:
        return None, "sem __file__"
    if exact_file:
        try:
            if os.path.realpath(f) != lib_real:
                return None, "__file__=%s" % os.path.basename(f)
        except Exception:
            return None, "__file__?"
    run = getattr(mod, "run", None)
    if run is None:
        return None, "sem run"
    if not callable(run):
        return None, "run nao-chamavel"
    ok, note = _run_arity(run)
    if not ok:
        return None, "run %s" % note
    return run, "%s %s" % (type(run).__name__, note)


def _find_entry_run(lib, diag=None):
    """Devolve o run(argv) do módulo de entrada já carregado (ou None).

    Addons com .so (ex.: Vulgata) registram o módulo de entrada em
    sys.modules na 1ª execução, e o .so guarda o objeto do módulo em
    cache (Cython): re-executar via runpy é no-op — a guarda de
    re-inicialização impede o corpo do módulo de rodar de novo — e o
    2º dispatch volta vazio, sem erro. O módulo em cache expõe
    run(argv), a função de roteamento, que lê o argv novo e despacha
    de novo. Addons Python puros não registram o módulo de entrada
    (o runpy roda como __main__), então caem no runpy normal.

    O lookup é robusto e não depende de um único caminho (o .so
    arm64 pode registrar o módulo com outro nome, ou o run pode não
    expor __code__ se o .so foi compilado com Cython antigo):
    1. nomes prováveis (basename do lib, "main", "default", "__main__")
       com __file__ == lib e run(argv) válido;
    2. varredura de sys.modules por qualquer módulo com
       __file__ == lib e run(argv) válido;
    3. fallback: módulo com nome de entrada dentro do diretório do
       addon (mesmo que o __file__ não bata exatamente).
    Só aceita run de módulo DESTE addon (__file__ dentro do dir do
    addon), p/ nunca chamar o run de outro addon. Cada passo é
    registrado em diag (lista, se fornecida) p/ o diagnóstico visível.
    """
    if diag is None:
        diag = []
    lib_real = os.path.realpath(lib or "")
    base = os.path.splitext(os.path.basename(lib or ""))[0]
    addon_dir = os.path.dirname(lib_real)
    names = []
    for n in (base, "main", "default", "__main__"):
        if n and n not in names:
            names.append(n)
    for name in names:
        mod = sys.modules.get(name)
        if mod is None:
            continue
        run, note = _entry_run_from_module(mod, lib_real)
        if run is not None:
            diag.append("mod=%s file=OK run=%s" % (name, note))
            return run
        diag.append("mod=%s: %s" % (name, note))
    found = 0
    for name, mod in list(sys.modules.items()):
        if name in names:
            continue
        try:
            f = getattr(mod, "__file__", None)
        except Exception:
            continue
        if not f:
            continue
        try:
            if os.path.realpath(f) != lib_real:
                continue
        except Exception:
            continue
        found += 1
        run, note = _entry_run_from_module(mod, lib_real)
        if run is not None:
            diag.append("sweep mod=%s file=OK run=%s" % (name, note))
            return run
        diag.append("sweep mod=%s: %s" % (name, note))
    if found == 0:
        diag.append("sweep: nenhum modulo com __file__==lib")
    for name in names:
        mod = sys.modules.get(name)
        if mod is None:
            continue
        try:
            f = getattr(mod, "__file__", None) or ""
            if not f:
                continue
            if os.path.commonpath((addon_dir, os.path.realpath(f))) != addon_dir:
                continue
        except Exception:
            continue
        run, note = _entry_run_from_module(mod, lib_real, exact_file=False)
        if run is not None:
            diag.append("fallback mod=%s file~dir run=%s" % (name, note))
            return run
        diag.append("fallback mod=%s: %s" % (name, note))
    diag.append("sem run reutilizavel (procurou: %s)" % ", ".join(names))
    return None


def _dispatch_call_summary(n0):
    """Resumo das chamadas xbmc* feitas durante um dispatch.

    Lê o log em memória (_aotv_kodi_log._calls) e resume o que o
    addon fez DURANTE este dispatch — é ele que distingue os três
    motivos de um 2º nível vazio sem erro:
    - guarda do addon ("Aguarde, essa ação já foi recebida"): o run
      recusou a ação repetida — só checks do loader + endOfDirectory
      ok=False, sem "Carregando..." e sem addDirectoryItem;
    - falha de rede/servidor: "Carregando..." + setContent, mas nenhum
      addDirectoryItem (o fetch não devolveu nada);
    - sucesso: vários addDirectoryItem.
    Nunca levanta (o diagnóstico não pode quebrar o dispatch).
    """
    try:
        import _aotv_kodi_log as _lm
        calls = _lm._calls
    except Exception:
        return "chamadas: ?"
    try:
        n = len(calls)
        new = calls[n0:] if n >= n0 else calls[-100:]
    except Exception:
        return "chamadas: ?"
    try:
        n_add = 0
        end_ok = None
        notes = []
        for name, args, _result in new:
            if name == "xbmcplugin.addDirectoryItem":
                n_add += 1
            elif name == "xbmcplugin.endOfDirectory":
                # args: repr dos args (handle, succeeded, ...) ou kwargs.
                if "succeeded=False" in args or "succeeded = False" in args:
                    end_ok = False
                else:
                    parts = [p.strip() for p in args.split(",")]
                    if len(parts) >= 2:
                        end_ok = (parts[1] != "False")
                    else:
                        end_ok = True
            elif name == "xbmcgui.Dialog.notification":
                # args traz heading e message em repr.
                if "Aguarde" in args:
                    notes.append("guarda!")
                elif "Carregando" in args:
                    notes.append("carregando")
        end_s = "?" if end_ok is None else ("ok" if end_ok else "falso")
        note_s = (" " + ",".join(notes)) if notes else ""
        return "chamadas: addDir=%d endDir=%s%s" % (n_add, end_s, note_s)
    except Exception:
        return "chamadas: ?"


def _run_plugin_inner(addon_id, path="", query="", handle=1):
    result = {"items": [], "resolved_url": "", "succeeded": False,
              "error": "", "handle": int(handle), "diag": ""}
    _ensure_simplejson()
    # Sonda HTTP (v1.2.4): garante instalada mesmo se configure() não
    # foi chamado (idempotente — reinstalar não duplica o wrapper).
    _install_http_probe()
    t_start = time.monotonic()
    d = find_addon_dir(addon_id)
    if not d:
        result["error"] = "addon não instalado: %s" % addon_id
        return result
    lib = get_plugin_library(addon_id)
    if not lib or not os.path.isfile(lib):
        result["error"] = "biblioteca do plugin não encontrada: %s" % addon_id
        return result
    # sys.argv como o Kodi invoca um plugin de vídeo.
    url = "plugin://%s/%s" % (addon_id, path.lstrip("/"))
    argv = [url, str(handle), query or ""]
    sys.argv = argv
    set_current_addon(addon_id)
    if d not in sys.path:
        sys.path.insert(0, d)
    # Limpa a coleta deste handle antes de rodar.
    mod = _collector()
    if mod is not None:
        try:
            mod.clear_handle(int(handle))
        except Exception:
            pass
    diag = []
    # Diagnóstico profundo (v1.2.3): o que EXATAMENTE foi passado ao
    # run e o que ele fez durante o dispatch. O diag da v1.2.2 provou
    # que o lookup funciona (mod=main, via run() direto) mas o 2º
    # nível volta vazio sem erro — sem o argv exato, sem saber se o
    # sys.argv global bate com o parâmetro, e sem as chamadas xbmc*
    # durante o dispatch, não dá p/ distinguir a guarda do addon
    # ("Aguarde, essa ação já foi recebida") de falha de rede/servidor.
    diag.append("argv=[%s | %s | %s]" % (url, handle, (query or "")[:120]))
    try:
        import _aotv_kodi_log as _lm
        n0 = len(_lm._calls)
    except Exception:
        n0 = None
    # Índice das chamadas HTTP no início deste dispatch (sonda v1.2.4).
    try:
        n_http0 = len(_http_calls)
    except Exception:
        n_http0 = None
    run_exc = None
    try:
        entry_run = _find_entry_run(lib, diag)
        if entry_run is not None:
            # Módulo de entrada já carregado com run(argv) reutilizável
            # (addons com .so — ver _find_entry_run). Chamar run(argv)
            # direto também evita o runpy, que sobrescreve sys.argv[0]
            # com o caminho do arquivo (e o addon montava URLs de
            # arquivo em vez de plugin:// no 1º dispatch).
            diag.append("via: run() direto")
            # O run lê o argv do PARÂMETRO (provado na VM: param=raiz
            # com sys.argv=browse devolve o menu raiz, não o browse).
            # Conferir se o global bate com o parâmetro no momento
            # da chamada — se algo o trocou no meio do caminho, o
            # run pode estar recebendo outro argv. Reafirmar o global
            # aqui (é a mesma lista; no-op se nada o trocou) p/ o
            # run que porventura leia o sys.argv global em vez do
            # parâmetro.
            diag.append("sys.argv==argv: %s" % (list(sys.argv) == list(argv)))
            sys.argv = argv
            entry_run(argv)
        else:
            diag.append("via: runpy (sem run)")
            import runpy
            runpy.run_path(lib, run_name="__main__")
    except BaseException as e:
        import traceback
        run_exc = e
        result["error"] = "%s: %s\n%s" % (
            type(e).__name__, e, traceback.format_exc(limit=12))
        _log("aotv_kodi.run_plugin", "ERRO %s: %s" % (addon_id, e))
    # Resumo das chamadas xbmc* DURANTE este dispatch (ver
    # _dispatch_call_summary): é ele que diz se o roteamento nem
    # começou (guarda), se começou e travou no fetch (rede), ou se
    # adicionou itens que se perderam na coleta.
    if n0 is not None:
        diag.append(_dispatch_call_summary(n0))
    # Tempo total do dispatch + resultado do fetch (sonda v1.2.4): é
    # ele que distingue, no celular, "fetch falhou" (timeout/SSL/
    # DNS/403, com o tempo gasto) de "fetch nem aconteceu" (nenhuma
    # chamada HTTP) e de "fetch OK mas parse vazio" (200 + 0 itens).
    try:
        diag.append("tempo=%dms" % int((time.monotonic() - t_start) * 1000))
    except Exception:
        pass
    if n_http0 is not None:
        diag.append(_http_summary(n_http0))
    if run_exc is not None:
        diag.append("run levantou: %s: %s" % (type(run_exc).__name__, run_exc))
    # O diag vai sempre (mesmo com erro) — é ele que explica o vazio.
    result["diag"] = "\n".join(diag)
    # Coleta o que o plugin devolveu (mesmo com erro parcial).
    if mod is not None:
        try:
            items = mod.get_items(int(handle))
            collected = []
            for entry in items:
                # (url, listitem, isFolder) — o coletor guarda a tupla.
                try:
                    url, li, is_folder = entry[0], entry[1], entry[2]
                except Exception:
                    continue
                d = listitem_to_dict(li)
                d["url"] = url
                d["is_folder"] = bool(is_folder)
                collected.append(d)
            result["items"] = collected
            resolved = mod.get_resolved(int(handle))
            if resolved:
                result["resolved_url"] = resolved.get("url", "")
                result["succeeded"] = bool(resolved.get("succeeded", False))
        except Exception as e:
            if not result["error"]:
                result["error"] = "coleta falhou: %s" % e
    return result


def run_script(addon_id, func_name):
    """Entrada script (RunScript) — Fase 3. Na Fase 1 só registra."""
    _log("aotv_kodi.run_script",
         "%s.%s (dispatch de script é Fase 3)" % (addon_id, func_name))
    return {"ok": False, "error": "RunScript é Fase 3 (não implementado)"}


def listitem_to_dict(item):
    """Traduz um xbmcgui.ListItem para dict (o Kotlin lê via Chaquopy).

    Campos: label, label2, path, is_folder, art {dict}, info {dict},
    properties {dict}, mime_type, context_menu [[label, action]...].
    """
    if item is None:
        return {}
    try:
        import xbmcgui
        is_listitem = isinstance(item, xbmcgui.ListItem)
    except Exception:
        is_listitem = False
    if not is_listitem:
        # Não é ListItem (ex.: string) — devolve como label/path cru.
        return {"label": str(item), "label2": "", "path": str(item),
                "is_folder": False, "art": {}, "info": {},
                "properties": {}, "mime_type": "", "context_menu": []}
    return {
        "label": item.getLabel(),
        "label2": item.getLabel2(),
        "path": item.getPath(),
        "is_folder": bool(item.isFolder()),
        "art": dict(item.getArt()),
        "info": dict(item.getInfo()),
        "properties": dict(item.getProperties()),
        "mime_type": item.getMimeType(),
        "context_menu": list(item.getContextMenuItems()),
    }
