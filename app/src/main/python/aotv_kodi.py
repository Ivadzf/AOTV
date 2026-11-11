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


def _find_entry_run(lib):
    """Devolve o run(argv) do módulo de entrada já carregado (ou None).

    Addons com .so (ex.: Vulgata) registram o módulo de entrada em
    sys.modules na 1ª execução, e o .so guarda o objeto do módulo em
    cache (Cython): re-executar via runpy é no-op — a guarda de
    re-inicialização impede o corpo do módulo de rodar de novo — e o
    2º dispatch volta vazio, sem erro. O módulo em cache expõe
    run(argv), a função de roteamento, que lê o argv novo e despacha
    de novo. Addons Python puros não registram o módulo de entrada
    (o runpy roda como __main__), então caem no runpy normal.
    Só aceita run(argv) com exatamente 1 argumento posicional, e só
    se o __file__ do módulo for a biblioteca deste addon.
    """
    try:
        name = os.path.splitext(os.path.basename(lib))[0]
        mod = sys.modules.get(name)
        if mod is None:
            return None
        if os.path.realpath(getattr(mod, "__file__", "") or "") != \
                os.path.realpath(lib):
            return None
        run = getattr(mod, "run", None)
        if not callable(run):
            return None
        try:
            argc = run.__code__.co_argcount
        except Exception:
            return None
        if argc != 1:
            return None
        return run
    except Exception:
        return None


def _run_plugin_inner(addon_id, path="", query="", handle=1):
    result = {"items": [], "resolved_url": "", "succeeded": False,
              "error": "", "handle": int(handle)}
    _ensure_simplejson()
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
    try:
        entry_run = _find_entry_run(lib)
        if entry_run is not None:
            # Módulo de entrada já carregado com run(argv) reutilizável
            # (addons com .so — ver _find_entry_run). Chamar run(argv)
            # direto também evita o runpy, que sobrescreve sys.argv[0]
            # com o caminho do arquivo (e o addon montava URLs de
            # arquivo em vez de plugin:// no 1º dispatch).
            entry_run(argv)
        else:
            import runpy
            runpy.run_path(lib, run_name="__main__")
    except BaseException as e:
        import traceback
        result["error"] = "%s: %s\n%s" % (
            type(e).__name__, e, traceback.format_exc(limit=12))
        _log("aotv_kodi.run_plugin", "ERRO %s: %s" % (addon_id, e))
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
