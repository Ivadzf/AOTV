package com.aotv.app.ui.movies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.StremioAddonService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Preferências da folha de fontes (Fase C). Lê os fluxos do PlatformPrefs,
 * então mudar nas Configurações aplica na hora na folha, mesmo com ela na pilha.
 * Os setters gravam no PlatformPrefs, que avisa quem observa.
 */
@HiltViewModel
class StreamPrefsViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val stremio: StremioAddonService,
) : ViewModel() {
    val search: StateFlow<Boolean> = prefs.streamSearchFlow
    fun setSearch(v: Boolean) { prefs.streamSearch = v }

    val badgesSize: StateFlow<Boolean> = prefs.streamBadgesSizeFlow
    fun setBadgesSize(v: Boolean) { prefs.streamBadgesSize = v }

    val badgesCodec: StateFlow<Boolean> = prefs.streamBadgesCodecFlow
    fun setBadgesCodec(v: Boolean) { prefs.streamBadgesCodec = v }

    val badgesLang: StateFlow<Boolean> = prefs.streamBadgesLangFlow
    fun setBadgesLang(v: Boolean) { prefs.streamBadgesLang = v }

    val badgesPos: StateFlow<String> = prefs.streamBadgesPosFlow
    fun setBadgesPos(v: String) { prefs.streamBadgesPos = v }

    val logo: StateFlow<Boolean> = prefs.streamLogoFlow
    fun setLogo(v: Boolean) { prefs.streamLogo = v }

    val pinned: StateFlow<List<String>> = prefs.streamPinnedFlow
    /** Desfixa uma fonte. Fixar é pelo botão de pin na folha de fontes. */
    fun unpin(name: String) { prefs.streamPinned = prefs.streamPinned.filterNot { it == name } }

    /** Fixa se não está fixada, desfixa se está (botão de pin no cabeçalho da seção). */
    fun togglePin(name: String) {
        val cur = prefs.streamPinned
        prefs.streamPinned = if (name in cur) cur - name else cur + name
    }

    /** Logo do manifest do addon ("" quando o addon não tem logo ou o manifest não foi lido). */
    fun logoFor(addon: String): String = stremio.logoForAddon(addon)
}

/**
 * Valores das prefs de streams já resolvidos p/ desenhar os cards de fonte.
 * Lido 1x por lista (folha ou detalhe do filme), não 1x por card.
 */
data class StreamCardPrefs(
    val showSize: Boolean = true,
    val showCodec: Boolean = true,
    val showLang: Boolean = true,
    /** "stats" = tamanho no canto superior direito; "pills" = tamanho como pill. */
    val sizeAsPill: Boolean = false,
    val showLogo: Boolean = true,
    val logoOf: (String) -> String = { "" },
)

/** Coleta os fluxos de [StreamPrefsViewModel] num [StreamCardPrefs] (recompõe quando muda). */
@Composable
fun rememberStreamCardPrefs(viewModel: StreamPrefsViewModel): StreamCardPrefs {
    val showSize by viewModel.badgesSize.collectAsStateWithLifecycle()
    val showCodec by viewModel.badgesCodec.collectAsStateWithLifecycle()
    val showLang by viewModel.badgesLang.collectAsStateWithLifecycle()
    val badgesPos by viewModel.badgesPos.collectAsStateWithLifecycle()
    val showLogo by viewModel.logo.collectAsStateWithLifecycle()
    return StreamCardPrefs(
        showSize = showSize,
        showCodec = showCodec,
        showLang = showLang,
        sizeAsPill = badgesPos == "pills",
        showLogo = showLogo,
        logoOf = viewModel::logoFor,
    )
}
