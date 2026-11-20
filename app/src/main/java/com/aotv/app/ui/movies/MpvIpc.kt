package com.aotv.app.ui.movies

import android.content.Context
import java.io.File

/**
 * Arquitetura cliente-servidor do mpv (Lote mpv 4, v1.39.0 — Frente 3).
 *
 * O AAR `io.github.abdallahmehiz:mpv-android-lib:0.1.12` (mpv 0.41) aceita a
 * opção `input-ipc-server`: com ela o mpv escuta comandos JSON no socket e
 * outro processo no mesmo aparelho pode controlar o player (pausar, buscar,
 * ler propriedades). Este objeto só define ONDE o socket vive — quem liga o
 * servidor é o MpvPlayer, quando a pref `pref_mpv_ipc_v1` está ligada.
 *
 * O socket fica no armazenamento interno (`filesDir/mpv/ipc.sock`), que só
 * o UID do app acessa: nenhum outro app do aparelho lê o socket, e nada
 * sai p/ a rede — o IPC do mpv é local por desenho.
 */
object MpvIpc {
    /** Caminho do socket IPC: `filesDir/mpv/ipc.sock`. */
    fun socketPath(context: Context): File = File(context.filesDir, "mpv/ipc.sock")
}
