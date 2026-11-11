"""kodi_six vendored — AOTV v1.1 (Fase 1).

O pacote `kodi-six` NÃO está no PyPI, então não dá para instalar via
pip do Chaquopy. Este shim re-exporta os módulos xbmc* do AOTV, para
os addons que fazem `from kodi_six import xbmc, xbmcgui, ...` (ex.:
BrazucaPlay) funcionarem sem ele.

É só um re-export — não é código do kodi-six nem do Kodi.
"""

import xbmc as xbmc
import xbmcaddon as xbmcaddon
import xbmcgui as xbmcgui
import xbmcplugin as xbmcplugin
import xbmcvfs as xbmcvfs

__all__ = ["xbmc", "xbmcaddon", "xbmcgui", "xbmcplugin", "xbmcvfs"]
