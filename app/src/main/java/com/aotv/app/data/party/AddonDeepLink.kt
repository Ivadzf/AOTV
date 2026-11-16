package com.aotv.app.data.party

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pedido de instalação de addon vindo de deep link stremio:// (Lote 17,
 * v1.27.0). A MainActivity oferece a URL (onCreate/onNewIntent), o
 * MoviesNavigation navega p/ `movies_addons` e o AddonsViewModel consome e
 * instala pelo fluxo normal de install(). Espelha o PartyDeepLink (Lote 10).
 *
 * Nada é instalado sozinho: a URL só viaja até a tela de addons, que mostra
 * o progresso/resultado do install() como se o usuário tivesse colado o
 * link. Link inválido é ignorado em silêncio na validação (ver
 * [com.aotv.app.data.remote.AddonLinks.capturedLink]).
 */
object AddonDeepLink {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()
    fun offer(url: String) { _url.value = url }
    fun consume() { _url.value = null }

    /**
     * Extrai a URL de um deep link `stremio://...`. Devolve a URI completa
     * como string, ou null quando não é stremio://. A validação (host de
     * verdade, /manifest.json) fica p/ quem consome, não aqui.
     */
    fun parse(uri: Uri?): String? {
        if (uri == null) return null
        if (!uri.scheme.equals("stremio", ignoreCase = true)) return null
        return uri.toString().takeIf { it.isNotBlank() }
    }
}
