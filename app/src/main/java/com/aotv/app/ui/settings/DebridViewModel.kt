package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.debrid.DebridAccount
import com.aotv.app.data.debrid.DebridProvider
import com.aotv.app.data.debrid.DebridService
import com.aotv.app.data.debrid.DebridSettings
import com.aotv.app.data.debrid.DeviceCode
import com.aotv.app.data.debrid.DevicePoll
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebridViewModel @Inject constructor(
    private val debrid: DebridService,
) : ViewModel() {

    data class Ui(
        val accounts: List<DebridAccount> = emptyList(),
        val settings: DebridSettings = DebridSettings(),
        /** Serviço com conexão em andamento (botão girando e travado). */
        val busy: DebridProvider? = null,
        val error: String? = null,
        val device: DeviceCode? = null,
        val message: String? = null,
    ) {
        val active: DebridAccount? get() =
            if (!settings.enabled) null else accounts.firstOrNull { it.provider.id == settings.preferred } ?: accounts.firstOrNull()
    }

    private val local = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = combine(local, debrid.accounts, debrid.settings) { l, a, s -> l.copy(accounts = a, settings = s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ui())

    private var polling: Job? = null

    fun connectKey(p: DebridProvider, key: String) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = p, error = null, message = null) }
        viewModelScope.launch {
            val r = debrid.connectKey(p, key)
            local.update {
                it.copy(
                    busy = null,
                    error = r.exceptionOrNull()?.message,
                    message = r.getOrNull()?.let { a -> "${p.label} conectado" + if (a.user.isNotBlank()) " como ${a.user}" else "" },
                )
            }
        }
    }

    fun startDevice(p: DebridProvider) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = p, error = null, message = null) }
        polling?.cancel()
        polling = viewModelScope.launch {
            val code = debrid.startDevice(p).getOrElse { e ->
                local.update { it.copy(busy = null, error = e.message) }
                return@launch
            }
            local.update { it.copy(device = code) }
            while (true) {
                delay(code.intervalSec * 1000L)
                when (val r = debrid.pollDevice(code)) {
                    DevicePoll.Pending -> continue
                    is DevicePoll.Done -> {
                        local.update {
                            it.copy(busy = null, device = null, message = "${p.label} conectado" + if (r.account.user.isNotBlank()) " como ${r.account.user}" else "")
                        }
                        return@launch
                    }
                    DevicePoll.Expired -> { local.update { it.copy(busy = null, device = null, error = "O código venceu. Peça outro.") }; return@launch }
                    is DevicePoll.Failed -> { local.update { it.copy(busy = null, device = null, error = r.message) }; return@launch }
                }
            }
        }
    }

    fun cancelDevice() {
        polling?.cancel()
        local.update { it.copy(busy = null, device = null) }
    }

    fun disconnect(p: DebridProvider) {
        debrid.disconnect(p)
        local.update { it.copy(message = "${p.label} desconectado", error = null) }
    }

    fun setEnabled(on: Boolean) = debrid.setEnabled(on)
    fun setPreferred(p: DebridProvider) = debrid.setPreferred(p)
    fun setHideUncached(on: Boolean) = debrid.setHideUncached(on)
    fun clearMessage() = local.update { it.copy(error = null, message = null) }
}
