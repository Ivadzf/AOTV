"""Módulo xbmcaddon do Kodi — AOTV v1.1 (Fase 1).

Clean-room (nomes/assinaturas públicos da API Python do Kodi). A classe
Addon é uma classe Python normal e patchável — o loader do Vulgata a
embrulha (monkey-patch), então ela NÃO pode ser um tipo travado.

getSetting/getAddonInfo leem de verdade (addon.xml + settings.xml +
addon_data/<id>/settings.xml) via aotv_kodi.py — import lazy para não
criar ciclo (aotv_kodi importa xbmcplugin/xbmcgui, não xbmcaddon).
"""

from _aotv_kodi_log import log as _log


def _bridge():
    """O aotv_kodi (lazy; sem ele, os getters devolvem vazio)."""
    try:
        import aotv_kodi
        return aotv_kodi
    except Exception:
        return None


class Addon:
    """Representa um addon instalado (id opcional; sem id = o addon chamador)."""

    def __init__(self, id=None):
        # Sem id: o addon em execução (o dispatcher seta via aotv_kodi).
        if id:
            self._id = id
        else:
            bridge = _bridge()
            self._id = bridge.get_current_addon() if bridge else ""

    def getSetting(self, setting_id):
        """Só a forma string (não há getSettingBool/Int no Vulgata)."""
        bridge = _bridge()
        if bridge is None:
            return ""
        return bridge.get_setting(self._id, setting_id)

    def getAddonInfo(self, info_id):
        bridge = _bridge()
        if bridge is None:
            return self._id if str(info_id).lower() == "id" else ""
        return bridge.get_addon_info(self._id, info_id)

    def openSettings(self):
        _log("xbmcaddon.Addon.openSettings", self._id)
        # Sem UI de settings na Fase 1 (é Fase 4) — só registra a chamada.


# Registra cada chamada xbmcaddon* (nome, args, retorno) no log de
# chamadas (filesDir/kodi/xbmc_calls.log) — ver _aotv_kodi_log.record.
from _aotv_kodi_log import traced as _traced

for _m in ("getSetting", "getAddonInfo", "openSettings"):
    setattr(Addon, _m, _traced("xbmcaddon.Addon." + _m, getattr(Addon, _m)))
