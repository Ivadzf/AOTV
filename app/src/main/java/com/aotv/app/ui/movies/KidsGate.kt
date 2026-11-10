package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.certBR
import com.aotv.app.data.remote.kidsBlocked
import com.aotv.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class KidsGateViewModel @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
) : ViewModel() {
    val kids get() = prefs.isKids

    /**
     * O título passa no perfil infantil? Lê gênero e classificação no detalhe do TMDB. Sem rede
     * ou sem id bloqueia, porque não dá pra conferir (vale também para download offline).
     */
    suspend fun blocked(tmdbId: Int, type: String): Boolean {
        if (tmdbId <= 0) return true
        cache[tmdbId to type]?.let { return it }
        val key = prefs.tmdbApiKey
        return runCatching {
            if (type == "SERIES") tmdb.getSeriesDetail(tmdbId, append = "content_ratings", apiKey = key)
                .let { kidsBlocked(it.genres.map { g -> g.id }, it.contentRatings.certBR(), it.adult) }
            else tmdb.getMovieDetail(tmdbId, append = "release_dates", apiKey = key)
                .let { kidsBlocked(it.genres.map { g -> g.id }, it.releaseDates.certBR(), it.adult) }
        }.getOrNull()?.also { cache[tmdbId to type] = it } ?: true
    }

    private companion object {
        val cache = java.util.concurrent.ConcurrentHashMap<Pair<Int, String>, Boolean>()
    }
}

/**
 * Porta do detalhe e do player no perfil infantil. Fora dele mostra o conteúdo direto, sem
 * espera. No infantil confere o título antes e, acima de 12 anos ou fora de animação e família,
 * mostra "Bloqueado no perfil infantil".
 */
@Composable
fun KidsGate(tmdbId: Int, type: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val vm: KidsGateViewModel = hiltViewModel()
    if (!vm.kids) { content(); return }
    val blocked by produceState<Boolean?>(null, tmdbId, type) { value = vm.blocked(tmdbId, type) }
    when (blocked) {
        false -> content()
        null -> Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
        }
        true -> {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Column(
                Modifier.fillMaxSize().background(Background).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Lock, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Bloqueado no perfil infantil", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Este título não é para crianças. Troque de perfil para assistir.",
                    color = TextTertiary, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).focusRequester(focus)) { Text("Voltar") }
            }
        }
    }
}
