package com.aotv.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.kodi.KodiSpike
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tela de diagnóstico do spike Kodi (Fase 0, v1.1): instala o ZIP do
 * Vulgata por SAF e roda o teste de carga do .so, mostrando o resultado.
 */
@HiltViewModel
class KodiSpikeViewModel @Inject constructor(
    private val spike: KodiSpike,
) : ViewModel() {

    data class UiState(
        val installedId: String? = null,
        val installedVersion: String? = null,
        val installing: Boolean = false,
        val testing: Boolean = false,
        val result: String? = null,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refreshInstalled()
    }

    fun refreshInstalled() {
        val installed = spike.installedVulgata()
        _uiState.update {
            it.copy(installedId = installed?.id, installedVersion = installed?.version)
        }
    }

    fun installZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(installing = true, error = null) }
            val res = spike.installZip(uri)
            _uiState.update {
                it.copy(
                    installing = false,
                    installedId = res.getOrNull()?.id ?: it.installedId,
                    installedVersion = res.getOrNull()?.version ?: it.installedVersion,
                    error = res.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun runTest() {
        viewModelScope.launch {
            _uiState.update { it.copy(testing = true, result = null, error = null) }
            val res = spike.runLoadTest()
            _uiState.update { it.copy(testing = false, result = formatResult(res)) }
        }
    }

    private fun formatResult(res: KodiSpike.LoadTestResult): String {
        val sb = StringBuilder()
        sb.appendLine("Python: ${res.pythonVersion.ifBlank { "?" }}")
        sb.appendLine("Estágio: ${res.stage}")
        sb.appendLine("Stubs xbmc*: ${if (res.stubsOk) "OK" else "FALHARAM"}")
        sb.appendLine(".so carregou: ${if (res.soLoaded) "SIM" else "NÃO"}")
        // Spike4: erro bruto dos diagnósticos (antes do main.py).
        sb.appendLine("Carga direta do .so: ${res.directSo ?: "—"}")
        sb.appendLine("CDLL libkodi.so: ${res.cdllLibkodi ?: "—"}")
        if (res.firstError != null) {
            sb.appendLine()
            sb.appendLine("Primeiro erro:")
            sb.appendLine(res.firstError)
        }
        if (res.stage == "main_failed" &&
            res.firstError?.contains("libkodi.so") == true
        ) {
            sb.appendLine()
            sb.appendLine(
                "Diagnóstico confirmado: o .so do Vulgata exige libkodi.so " +
                    "e o shim ainda não existe (próximo passo do spike)."
            )
        }
        return sb.toString().trimEnd()
    }
}
