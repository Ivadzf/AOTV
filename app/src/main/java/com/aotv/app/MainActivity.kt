package com.aotv.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.ui.common.DeviceLayout
import com.aotv.app.ui.common.LocalPosterQuality
import com.aotv.app.ui.common.LocalPosterRanks
import com.aotv.app.ui.common.currentDeviceLayout
import com.aotv.app.ui.navigation.MoviesNavigation
import com.aotv.app.ui.theme.AotvPalette
import com.aotv.app.ui.theme.AotvTheme
import com.aotv.app.ui.theme.currentPalette
import com.aotv.app.ui.theme.setCurrentPalette
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var platformPrefs: PlatformPrefs
    @Inject lateinit var posterInfo: com.aotv.app.data.local.PosterInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        setContent {
            // Paleta de cores (Fase A, Frente 3): publica a escolha salva no
            // holder que os vals de Color.kt e o AotvTheme leem — FORA da
            // composição, num efeito, para a escrita não invalidar a árvore.
            val paletteId by platformPrefs.themePalette.collectAsStateWithLifecycle()
            // Lote 3 (v1.11.0): com o Personalizado selecionado, a paleta vem das
            // 6 cores editáveis salvas — editar uma cor recompõe o app na hora.
            val customColors by platformPrefs.customTheme.collectAsStateWithLifecycle()
            val palette = AotvPalette.fromId(paletteId, customColors)
            LaunchedEffect(palette) { if (currentPalette != palette) setCurrentPalette(palette) }
            AotvTheme {
                val deviceLayout = currentDeviceLayout(platformPrefs.platformMode)
                // Estável entre recomposições: sem ele, o lambda novo a cada vez
                // impedia o Compose de pular o MoviesNavigation no wrapper.
                val onRestartApp = remember { { restartApp() } }
                // 1ª abertura: nome do perfil + confirmar a plataforma detectada. Só uma vez.
                var onboarding by rememberSaveable { mutableStateOf(!platformPrefs.onboarded) }
                // Com mais de um perfil, celular e TV perguntam quem está assistindo ao abrir.
                var picking by rememberSaveable {
                    mutableStateOf(platformPrefs.profiles.value.size > 1 && !intent.getBooleanExtra(EXTRA_PICKED, false))
                }
                // Trocar de perfil (ou ligar o infantil no perfil ativo) reabre a Activity. Toda tela,
                // ViewModel e cache de memória nasce de novo já com os dados do perfil escolhido.
                LaunchedEffect(Unit) {
                    combine(platformPrefs.profileId, platformPrefs.profiles) { id, list -> id to list.firstOrNull { it.id == id }?.kids }
                        .distinctUntilChanged().drop(1).collect { restartApp(picked = true) }
                }
                if (onboarding) {
                    com.aotv.app.ui.common.ProvideTvMode(deviceLayout == DeviceLayout.TV) {
                        com.aotv.app.ui.settings.FirstRunScreen(onDone = { onboarding = false })
                    }
                    return@AotvTheme
                }
                if (picking) {
                    com.aotv.app.ui.common.ProvideTvMode(deviceLayout == DeviceLayout.TV) {
                        com.aotv.app.ui.settings.ProfilesScreen(onPicked = { picking = false })
                    }
                    return@AotvTheme
                }
                // Ranking e resumo das fontes, lidos num wrapper próprio e
                // entregues aos cartões por CompositionLocal — antes cada emissão
                // recompunha o setContent inteiro (tema + navegação). Agora a
                // emissão só invalida este wrapper; o conteúdo é pulado quando
                // os parâmetros não mudam.
                PosterInfoScope(posterInfo) {
                    MoviesNavigation(
                        deviceLayout = deviceLayout,
                        onRestartApp = onRestartApp,
                    )
                }
            }
        }
    }

    // Coleta ranks/quality isolada: cada emissão só recompõe este wrapper.
    @androidx.compose.runtime.Composable
    private fun PosterInfoScope(
        posterInfo: com.aotv.app.data.local.PosterInfo,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val posterRanks = posterInfo.ranks.collectAsStateWithLifecycle()
        val posterQuality = posterInfo.quality.collectAsStateWithLifecycle()
        androidx.compose.runtime.CompositionLocalProvider(
            com.aotv.app.ui.common.LocalPosterInfo provides posterInfo,
            LocalPosterRanks provides posterRanks,
            LocalPosterQuality provides posterQuality,
        ) {
            content()
        }
    }

    // Tela cheia: esconde barras de status e navegação no app inteiro (reaparecem ao deslizar).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Picture-in-Picture: ao sair do app (Home) durante um filme/série, entra na mini tela.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        com.aotv.app.ui.movies.PipState.enter?.invoke()
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        com.aotv.app.ui.movies.PipState.inPip.value = isInPip
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun restartApp(picked: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java).putExtra(EXTRA_PICKED, picked)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private companion object {
        /** Reabertura depois de escolher o perfil. Não pergunta de novo quem está assistindo. */
        const val EXTRA_PICKED = "profile_picked"
    }
}
