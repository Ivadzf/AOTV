"""Driver do teste de carga — spike da v1.1 (Fase 0).

Executa o main.py do Vulgata como o Kodi faria (sys.argv de plugin de
vídeo) e registra onde ele para. O resultado esperado neste estágio é
FALHAR no dlopen do .so com "libkodi.so não encontrado" (o .so tem
DT_NEEDED libkodi.so e ainda não há shim); isso CONFIRMA o diagnóstico
do plano (§2.4) e registra o erro exato do linker.

Chamado do Kotlin (KodiSpike) via Chaquopy:
    Python.getInstance().getModule("aotv_spike").callAttr("run_load_test", addon_dir)
"""

import sys
import traceback


def run_load_test(addon_dir):
    result = {
        "python_version": sys.version.split()[0],
        "addon_dir": addon_dir,
        "stubs_ok": False,
        "loader_started": False,
        "so_loaded": False,
        "first_error": "",
        "stage": "start",
    }
    try:
        # 1. Stubs xbmc* importam? (O Chaquopy já os põe no sys.path via
        # app/src/main/python; se um deles falhar, o problema é nosso, não
        # do addon.)
        import xbmc
        import xbmcaddon
        import xbmcgui
        import xbmcvfs
        import xbmcplugin
        result["stubs_ok"] = True
        result["stage"] = "stubs_ok"

        # 2. Addon no sys.path (o main.py faz
        # `from _re005f0d31a6b3889 import __run__`).
        if addon_dir not in sys.path:
            sys.path.insert(0, addon_dir)

        # 3. argv como o Kodi invoca um plugin de vídeo:
        # argv[0] = plugin://<id>/, argv[1] = handle, argv[2] = query.
        sys.argv = ["plugin://plugin.video.vulgata/", "1", ""]

        # 4. Executa main.py como __main__ (o stub chama
        # __run__(__name__, __file__, 'main')). É aqui que o loader escolhe
        # o .so por plataforma, verifica o SHA256 e importa o .so — o
        # dlopen deve falhar com "libkodi.so não encontrado".
        import runpy
        main_path = addon_dir.rstrip("/") + "/main.py"
        result["loader_started"] = True
        result["stage"] = "running_main"
        runpy.run_path(main_path, run_name="__main__")

        # main.py retornou sem erro: o .so carregou (inesperado neste
        # estágio — significaria que o shim nem é necessário).
        result["stage"] = "main_returned"
        result["so_loaded"] = True
    except BaseException as e:
        result["first_error"] = "%s: %s\n%s" % (
            type(e).__name__, e, traceback.format_exc())
        if not result["stubs_ok"]:
            result["stage"] = "stubs_failed"
        elif not result["loader_started"]:
            result["stage"] = "path_failed"
        else:
            result["stage"] = "main_failed"
    return result
