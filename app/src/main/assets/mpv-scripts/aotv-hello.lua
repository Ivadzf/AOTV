-- aotv-hello.lua — exemplo de script Lua do AOTV (Lote mpv 4, v1.39.0).
--
-- O que faz: quando um arquivo começa a tocar, mostra "AOTV + Libmpv" no
-- OSD do player por alguns segundos. É só um "olá" — prova que os scripts
-- da pasta carregaram no init do Libmpv.
--
-- Como usar: scripts .lua do usuário na pasta filesDir/mpv/scripts
-- carregam sozinhos no próximo init do Libmpv (o mpv lê a pasta
-- <config-dir>/scripts/ sem precisar de comando). Não vale p/ .js: o motor
-- (mpv 0.41 do AAR) só tem Lua, sem mujs.

mp.register_event("file-loaded", function()
    mp.osd_message("AOTV + Libmpv")
end)
