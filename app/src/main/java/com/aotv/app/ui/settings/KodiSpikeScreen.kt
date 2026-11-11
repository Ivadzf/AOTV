package com.aotv.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.ui.common.PageHeader
import com.aotv.app.ui.common.pagePadding
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Tela de diagnóstico do spike Kodi (Fase 0, v1.1).
 *
 * 1. Instala o ZIP do Vulgata escolhido pelo usuário (SAF) em
 *    filesDir/kodi/addons/plugin.video.vulgata/ — o ZIP é proprietário e
 *    nunca é embutido no app nem vai ao repo.
 * 2. Roda o teste de carga (aotv_spike.run_load_test) e mostra o resultado.
 *
 * Resultado esperado neste estágio: o .so NÃO carrega — "libkodi.so não
 * encontrado". Isso confirma o diagnóstico do plano e registra o erro
 * exato do linker; o shim libkodi.so vem depois.
 */
@Composable
fun KodiSpikeScreen(
    onBack: () -> Unit,
    viewModel: KodiSpikeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickZip = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.installZip(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .topInsetsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = pagePadding(), vertical = 8.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
        }
        PageHeader(
            title = "Kodi (spike)",
            sub = "Fase 0 da v1.1 — prova de carga do Vulgata sob o Python do Chaquopy.",
        )

        Text(
            "O teste tenta carregar o .so do Vulgata (plugin.video.vulgata). " +
                "O resultado esperado agora é o erro \"libkodi.so não encontrado\" — " +
                "é ele que confirma o diagnóstico e diz o que o shim precisa resolver.",
            color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))

        // 1. Instalar o ZIP do Vulgata.
        OutlinedButton(
            onClick = { pickZip.launch(arrayOf("application/zip")) },
            enabled = !state.installing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.installing) "Instalando…" else "Instalar ZIP do Vulgata")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.installedId != null)
                "Instalado: ${state.installedId} v${state.installedVersion}"
            else
                "Nenhum addon Kodi instalado ainda.",
            color = TextTertiary, fontSize = 13.sp,
        )
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text("Erro: ${state.error}", color = TextPrimary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))

        // 2. Rodar o teste de carga.
        Button(
            onClick = { viewModel.runTest() },
            enabled = state.installedId != null && !state.testing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Rodar teste de carga")
        }
        Spacer(Modifier.height(12.dp))
        if (state.testing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(
                "Carregando o Python e o .so… (a primeira vez demora alguns segundos)",
                color = TextTertiary, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        if (state.result != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                state.result!!,
                color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BackgroundCard)
                    .padding(12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
