package com.aotv.app.ui.movies

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Busca por voz (Lote 3, item 1 — só celular; na TV não há microfone).
 *
 * Usa o reconhecedor do sistema ([RecognizerIntent.ACTION_RECOGNIZE_SPEECH], pt-BR):
 * a transcrição alimenta [onResult] — a tela passa `viewModel::onQueryChange`, então
 * a busca ao vivo dispara como se o texto tivesse sido digitado. Nada aqui é pref
 * por perfil: é da sessão, some quando a tela fecha.
 *
 * Estados, todos sem travar a busca digitada (ela continua funcionando sempre):
 * - sem reconhecedor no aparelho → toast e nada acontece;
 * - sem permissão → pede em runtime; negada → toast explicando onde liberar;
 * - cancelado pelo usuário (voltar) → volta em silêncio, sem toast;
 * - sem resultado → toast "Não entendi", o campo fica como estava.
 * Enquanto o reconhecedor do sistema está aberto, [listening] fica true e o botão
 * do microfone acende — o Android mostra a própria UI de escuta por cima.
 */
class VoiceSearchHandle(
    /** O reconhecedor do sistema está aberto (botão do microfone aceso). */
    val listening: Boolean,
    /** O aparelho tem reconhecimento de voz. Sem ele, o botão nem aparece. */
    val available: Boolean,
    /** Abre o reconhecedor (pede a permissão antes, se precisar). */
    val start: () -> Unit,
)

@Composable
fun rememberVoiceSearch(onResult: (String) -> Unit): VoiceSearchHandle {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    // Checado uma vez por composição: aparelho sem serviço de voz (ou sem Google)
    // devolve false aqui e o botão nem é desenhado.
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val recognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false
        // Voltar/cancelar não é erro: só fecha e o campo fica como estava.
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val heard = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.trim().orEmpty()
        if (heard.isBlank()) toast("Não entendi — tente falar de novo.")
        else onResult(heard)
    }

    fun launchRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o nome do filme ou série")
        }
        listening = true
        runCatching { recognizerLauncher.launch(intent) }.onFailure {
            listening = false
            toast("Não foi possível abrir o reconhecimento de voz.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchRecognizer()
        else toast("Sem a permissão do microfone não dá para buscar por voz. Dá para liberar em Configurações do app.")
    }

    fun start() {
        if (!available) {
            toast("Este aparelho não tem reconhecimento de voz.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchRecognizer()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    return remember(listening, available) { VoiceSearchHandle(listening, available, ::start) }
}
