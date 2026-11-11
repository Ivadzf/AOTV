"""Stub mínimo do módulo xbmcaddon do Kodi — spike da v1.1 (Fase 0).

Clean-room (nomes/assinaturas públicos da API Python do Kodi). A classe
Addon é uma classe Python normal e patchável — o loader do Vulgata a
embrulha (monkey-patch), então ela NÃO pode ser um tipo travado.
"""

from _aotv_kodi_log import log as _log


class Addon:
    """Representa um addon instalado (id opcional; sem id = o addon chamador)."""

    def __init__(self, id=None):
        self._id = id or ""

    def getSetting(self, setting_id):
        _log("xbmcaddon.Addon.getSetting", "%s id=%s" % (self._id, setting_id))
        return ""

    def getAddonInfo(self, info_id):
        _log("xbmcaddon.Addon.getAddonInfo", "%s info=%s" % (self._id, info_id))
        if info_id == "id":
            return self._id
        return ""

    def openSettings(self):
        _log("xbmcaddon.Addon.openSettings", self._id)

# Spike6: registra cada chamada xbmcaddon* (nome, args, retorno) no log de
# chamadas (filesDir/kodi/xbmc_calls.log) — ver _aotv_kodi_log.record.
from _aotv_kodi_log import traced as _traced

for _m in ("getSetting", "getAddonInfo", "openSettings"):
    setattr(Addon, _m, _traced("xbmcaddon.Addon." + _m, getattr(Addon, _m)))
