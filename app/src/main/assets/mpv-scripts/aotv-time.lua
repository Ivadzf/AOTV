-- aotv-time.lua — exemplo de script Lua do AOTV (Lote mpv 4, v1.39.0).
--
-- O que faz: a cada 30 s, lê a posição atual do vídeo (time-pos) e grava
-- em user-data/aotv/time-pos — uma propriedade que o app pode ler de volta
-- (mp.get_property / observe_property). Demonstra a comunicação script→app:
-- o script observa o player e deixa o valor onde o app enxerga.
--
-- Como usar: scripts .lua do usuário na pasta filesDir/mpv/scripts
-- carregam sozinhos no próximo init do Libmpv (o mpv lê a pasta
-- <config-dir>/scripts/ sem precisar de comando). Não vale p/ .js: o motor
-- (mpv 0.41 do AAR) só tem Lua, sem mujs.

mp.add_periodic_timer(30, function()
    mp.set_property("user-data/aotv/time-pos", mp.get_property_number("time-pos", 0))
end)
