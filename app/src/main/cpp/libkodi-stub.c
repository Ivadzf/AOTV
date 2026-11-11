/*
 * libkodi.so — shim mínimo para o spike Kodi da v1.1 (Fase 0).
 *
 * Por que existe: o addon_core.so do Vulgata (plugin.video.vulgata,
 * android_arm64) tem DT_NEEDED `libkodi.so`. Sem um arquivo com esse nome
 * no APK, o dlopen falha e o loader do Vulgata converte qualquer falha de
 * carga em "Protected execution failed". O .so NÃO usa nenhum símbolo do
 * Kodi: `nm -D --undefined-only` no addon_core.so mostra 224 símbolos
 * indefinidos — 210 Py* + 8 _Py* (API C do Python, resolvidos pelo
 * interpretador do Chaquopy) + 6 de libc (__cxa_atexit, __cxa_finalize,
 * _register_atfork, _stack_chk_fail, strcmp, strrchr). Ou seja: o .so só
 * precisa que o libkodi.so EXISTA para o linker aceitar o dlopen.
 *
 * Por isso este shim não exporta símbolo nenhum: é só um arquivo vazio
 * com uma função dummy (para a unidade de tradução não ficar vazia).
 * Nada chama essa função — ela só existe para o compilador não reclamar
 * de arquivo vazio.
 *
 * NÃO é binário: este .c vai ao repo como texto e o .so é gerado no CI
 * (passo "Compilar shim libkodi.so" no workflow, com o clang do NDK),
 * em app/src/main/jniLibs/arm64-v8a/libkodi.so — antes do Gradle
 * empacotar o APK. Ver kodi-v11-plan.md §2.4 e Fase 0, item 4.
 */

/* Dummy: nunca chamada; só impede unidade de tradução vazia. */
void aotv_libkodi_shim_dummy(void) {}
