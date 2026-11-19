package com.aotv.app.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aotv.app.BuildConfig
import com.aotv.app.data.diagnostics.SpeedTest
import com.aotv.app.data.diagnostics.SpeedTestResult
import com.aotv.app.data.local.CrashLog
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Report a problem" (Lote 25, v1.35.0 — Frente B, item 5; ideia do MKCinema,
 * reimplementada — o MK abre issue no GitHub já preenchida).
 *
 * Monta um texto de relato pré-preenchido — versão do app, aparelho/Android
 * e o último crash (truncado) — e deixa compartilhar (ACTION_SEND) ou copiar.
 * NUNCA inclui chave, token ou segredo: o texto só usa versão + aparelho +
 * crash, e o crash passa pelo [scrubSecrets] antes de entrar no texto
 * (qualquer par chave=valor parecido com credencial vira "[removido]").
 * Nenhuma configuração do usuário entra no texto.
 *
 * O teste de velocidade embutido ([SpeedTest]) roda SÓ quando o usuário toca
 * no botão — nunca automático — e o último resultado fica guardado p/ ir no
 * relato sem testar de novo. Se falhar, mostra o erro sem crash.
 */

/** Padrão de par chave=valor que parece credencial (p/ apagar do crash antes de expor). */
private val SECRET_PATTERN = Regex(
    "(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|client[_-]?id|password|passwd|secret|bearer|authorization)[\"'=: ]+[^\\s,;\"']+",
)

/** Apaga valores que parecem credencial de um texto (o nome da chave fica, o valor some). */
fun scrubSecrets(text: String): String = SECRET_PATTERN.replace(text, "$1 [removido]")

/**
 * Monta o texto do relato. Só versão + aparelho + último crash (truncado em
 * 2000 chars e limpo por [scrubSecrets]) + resultado do último teste de
 * velocidade, quando houver. Nenhuma chave, token, URL assinada ou
 * configuração entra aqui — conferir o texto na tela antes de enviar.
 */
fun buildIssueText(context: Context, lastSpeedTest: SpeedTestResult? = null): String {
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val device = "${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val crash = CrashLog.read(context)?.take(2000)?.let { scrubSecrets(it) }
    val speed = lastSpeedTest?.let {
        if (it.ok) "Teste de velocidade: ${it.display()} (${it.bytesDownloaded / 1024} KB baixados)"
        else "Teste de velocidade: falhou (${it.error ?: "erro desconhecido"})"
    } ?: "Teste de velocidade: não feito"
    return buildString {
        appendLine("AOTV $version — relato de problema")
        appendLine("Aparelho: $device")
        appendLine(speed)
        appendLine()
        appendLine("O que aconteceu (descreva aqui):")
        appendLine()
        appendLine()
        if (crash != null) {
            appendLine("Último crash registrado:")
            appendLine(crash)
        } else {
            appendLine("Último crash registrado: nenhum")
        }
        appendLine()
        appendLine("Nenhuma chave, token ou senha vai neste texto — confira antes de enviar.")
    }
}

@HiltViewModel
class ReportProblemViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _speedTest = MutableStateFlow(SpeedTest.loadLast(prefs))
    val speedTest: StateFlow<SpeedTestResult?> = _speedTest.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    /** Texto do relato com o último teste de velocidade (se houver). */
    fun issueText(): String = buildIssueText(appContext, _speedTest.value)

    /** Roda o teste de velocidade (só sob demanda — nunca automático). */
    fun runSpeedTest() {
        if (_testing.value) return
        viewModelScope.launch {
            _testing.value = true
            val result = SpeedTest.run()
            SpeedTest.saveLast(prefs, result)
            _speedTest.value = result
            _testing.value = false
        }
    }
}

@Composable
fun ReportProblemScreen(
    onBack: () -> Unit,
    viewModel: ReportProblemViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val speedResult by viewModel.speedTest.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val issueText = remember(speedResult) { viewModel.issueText() }
    VpnPartyScaffold("Reportar um problema", "Relato pré-preenchido, sem chaves", onBack) {
        VpnPartySection("O que vai no relato")
        ReportCard {
            Text(
                "Versão, aparelho e o último crash — nada de chaves, tokens ou senhas. Leia o texto antes de enviar: ele está todo aqui embaixo.",
                fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp,
            )
        }
        ReportCard {
            Text(issueText, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
        }
        AotvButton(
            "Compartilhar relato",
            {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "AOTV ${BuildConfig.VERSION_NAME} — relato de problema")
                    putExtra(Intent.EXTRA_TEXT, issueText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(send, "Compartilhar relato"))
            },
            tone = ButtonTone.PRIMARY, modifier = Modifier.fillMaxWidth(),
        )
        AotvButton(
            "Copiar texto",
            {
                clipboard.setText(AnnotatedString(issueText))
                Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
            },
            tone = ButtonTone.SECONDARY, modifier = Modifier.fillMaxWidth(),
        )
        VpnPartySection("Teste de velocidade")
        ReportCard {
            val result = speedResult
            Text(
                if (result == null) "Nunca testado neste aparelho."
                else "Último teste: ${result.display()}",
                fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp,
            )
            Text(
                "Baixa ~200 KB do Cloudflare e mede latência + velocidade. Só roda quando você toca no botão — nunca automático.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
        }
        AotvButton(
            "Testar velocidade",
            { viewModel.runSpeedTest() },
            tone = ButtonTone.SECONDARY, modifier = Modifier.fillMaxWidth(),
            busy = testing, busyLabel = "Testando…",
        )
    }
}

/** Cartão simples de texto (mesmo visual dos cartões do Diagnóstico). */
@Composable
private fun ReportCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(BackgroundCard, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
