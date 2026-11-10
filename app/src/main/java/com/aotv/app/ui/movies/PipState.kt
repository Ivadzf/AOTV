package com.aotv.app.ui.movies

import androidx.compose.runtime.mutableStateOf

/**
 * Ponte entre o player (Compose) e a MainActivity para Picture-in-Picture.
 * O player registra [enter] enquanto reproduz; a Activity chama no onUserLeaveHint
 * (usuário saiu do app) e reporta o estado em [inPip] p/ o player esconder os controles.
 */
object PipState {
    // ponytail: singleton simples — só existe 1 player ativo por vez.
    var enter: (() -> Unit)? = null
    val inPip = mutableStateOf(false)
}
