"""Driver do teste de carga — spike da v1.1 (Fase 0).

Executa o main.py do Vulgata como o Kodi faria (sys.argv de plugin de
vídeo) e registra onde ele para.

Spike4: o shim libkodi.so já está no APK e os símbolos Py* resolvem, mas
o teste continua falhando com "Protected execution failed" — o loader do
Vulgata engole a causa real (raise ... from None, linha 102 do
__init__.py). Por isso, ANTES do main.py, o driver tenta (a) carregar o
libkodi.so via ctypes.CDLL (por nome e pelo caminho absoluto no dir
nativo) e (b) carregar o addon_core.so diretamente
(spec_from_file_location + exec_module), capturando o erro BRUTO dos
dois. O fluxo do teste principal (runpy do main.py) não mudou.

Chamado do Kotlin (KodiSpike) via Chaquopy:
    Python.getInstance().getModule("aotv_spike").callAttr(
        "run_load_test", addon_dir, native_lib_dir)
"""

import sys
import traceback


def run_load_test(addon_dir, native_lib_dir=""):
    result = {
        "python_version": sys.version.split()[0],
        "addon_dir": addon_dir,
        "stubs_ok": False,
        "loader_started": False,
        "so_loaded": False,
        "first_error": "",
        "stage": "start",
        # Spike4: erro bruto dos diagnósticos ("" = não rodado).
        "direct_so": "",
        "cdll_libkodi": "",
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

        # 1b. Diagnóstico spike4 (ANTES do main.py): o loader do Vulgata
        # engole a causa real do dlopen ("Protected execution failed",
        # linha 102 do __init__.py). Estes dois testes capturam o erro
        # BRUTO — tipo, mensagem e traceback — sem o wrapper do Vulgata.
        import ctypes
        import os

        # (a) CDLL libkodi.so: diz se o linker acha o shim. Primeiro por
        # nome (como o DT_NEEDED o referencia), depois pelo caminho
        # absoluto no dir nativo do app.
        cdll_targets = ["libkodi.so"]
        if native_lib_dir:
            cdll_targets.append(os.path.join(native_lib_dir, "libkodi.so"))
        cdll_errors = []
        cdll_ok = None
        for target in cdll_targets:
            try:
                ctypes.CDLL(target)
                cdll_ok = target
                break
            except BaseException as e:
                cdll_errors.append(
                    "%s -> %s: %s" % (target, type(e).__name__, e))
        if cdll_ok:
            result["cdll_libkodi"] = "OK (%s)" % cdll_ok
        else:
            result["cdll_libkodi"] = "FALHOU\n" + "\n".join(cdll_errors)

        # (b) Carga direta do addon_core.so: o mesmo dlopen que o loader do
        # Vulgata faz, mas sem o wrapper "Protected execution failed".
        # NOTA: se esta carga passar, o .so fica inicializado no processo;
        # o loader do Vulgata pode então tropeçar no guarda de
        # re-inicialização do Cython — nesse caso este campo é o sinal
        # autoritativo (o .so carrega), não o estágio do main.py.
        so_path = addon_dir.rstrip("/") + \
            "/resources/lib/android_arm64/addon_core.so"
        try:
            import importlib.util
            spec = importlib.util.spec_from_file_location(
                "aotv_direct_addon_core", so_path)
            if spec is None or spec.loader is None:
                raise ImportError(
                    "spec_from_file_location devolveu None p/ %s" % so_path)
            direct_mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(direct_mod)
            result["direct_so"] = "OK — carregou (%s)" % so_path
        except BaseException as e:
            result["direct_so"] = "%s: %s\n%s" % (
                type(e).__name__, e, traceback.format_exc(limit=8))

        # 2. Addon no sys.path (o main.py faz
        # `from _re005f0d31a6b3889 import __run__`).
        if addon_dir not in sys.path:
            sys.path.insert(0, addon_dir)

        # 3. argv como o Kodi invoca um plugin de vídeo:
        # argv[0] = plugin://<id>/, argv[1] = handle, argv[2] = query.
        sys.argv = ["plugin://plugin.video.vulgata/", "1", ""]

        # 4. Executa main.py como __main__ (o stub chama
        # __run__(__name__, __file__, 'main')). É aqui que o loader escolhe
        # o .so por plataforma, verifica o SHA256 e importa o .so. O fluxo
        # não mudou no spike4 — só ganhou os diagnósticos do passo 1b.
        import runpy
        main_path = addon_dir.rstrip("/") + "/main.py"
        result["loader_started"] = True
        result["stage"] = "running_main"
        runpy.run_path(main_path, run_name="__main__")

        # main.py retornou sem erro: o .so carregou.
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
