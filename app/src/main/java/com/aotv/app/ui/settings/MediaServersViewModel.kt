package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.mediaservers.DiscoveredPlexServer
import com.aotv.app.data.mediaservers.MediaItem
import com.aotv.app.data.mediaservers.MediaLibrary
import com.aotv.app.data.mediaservers.MediaServerAccount
import com.aotv.app.data.mediaservers.MediaServerKind
import com.aotv.app.data.mediaservers.MediaServerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaServersViewModel @Inject constructor(
    private val servers: MediaServerService,
    private val prefs: PlatformPrefs,
) : ViewModel() {

    data class Ui(
        val accounts: List<MediaServerAccount> = emptyList(),
        /** Mostrar o atalho dos servidores na Biblioteca (por perfil). */
        val showInLibrary: Boolean = true,
        /** "add", id da conta em teste, ou null. */
        val busy: String? = null,
        val error: String? = null,
        val message: String? = null,
        /** Servidores Plex descobertos pelo plex.tv, aguardando escolha. */
        val plexServers: List<DiscoveredPlexServer> = emptyList(),
    )

    private val local = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = combine(local, servers.accounts, prefs.mediaServersShowFlow) { l, a, show ->
        l.copy(accounts = a, showInLibrary = show)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Ui())

    /** Servidores do usuário, p/ a Biblioteca decidir se mostra o atalho. */
    val hasServers: StateFlow<Boolean> = combine(servers.accounts, prefs.mediaServersShowFlow) { a, show ->
        show && a.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun connectJellyfin(kind: MediaServerKind, base: String, user: String, pass: String) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = "add", error = null, message = null, plexServers = emptyList()) }
        viewModelScope.launch {
            val r = servers.connectJellyfin(kind, base, user, pass)
            local.update {
                it.copy(
                    busy = null,
                    error = r.exceptionOrNull()?.message,
                    message = r.getOrNull()?.let { a -> "${a.label} conectado" },
                )
            }
        }
    }

    fun discoverPlex(token: String) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = "add", error = null, message = null, plexServers = emptyList()) }
        viewModelScope.launch {
            val r = servers.discoverPlex(token)
            local.update {
                it.copy(
                    busy = null,
                    error = r.exceptionOrNull()?.message,
                    plexServers = r.getOrNull().orEmpty(),
                    message = r.getOrNull()?.let { list ->
                        if (list.isEmpty()) "Nenhum servidor Plex nessa conta" else null
                    },
                )
            }
        }
    }

    fun connectPlexDiscovered(token: String, server: DiscoveredPlexServer) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = "add", error = null, message = null) }
        viewModelScope.launch {
            val r = servers.connectPlexDiscovered(token, server)
            local.update {
                it.copy(
                    busy = null,
                    error = r.exceptionOrNull()?.message,
                    plexServers = emptyList(),
                    message = r.getOrNull()?.let { a -> "${a.label} conectado" },
                )
            }
        }
    }

    fun connectPlexManual(token: String, base: String) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = "add", error = null, message = null, plexServers = emptyList()) }
        viewModelScope.launch {
            val r = servers.connectPlexManual(token, base)
            local.update {
                it.copy(
                    busy = null,
                    error = r.exceptionOrNull()?.message,
                    message = r.getOrNull()?.let { a -> "${a.label} conectado" },
                )
            }
        }
    }

    fun testConnection(acc: MediaServerAccount) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = acc.id, error = null, message = null) }
        viewModelScope.launch {
            val r = servers.testConnection(acc)
            local.update {
                it.copy(
                    busy = null,
                    error = r.exceptionOrNull()?.message,
                    message = r.getOrNull()?.let { info -> "${acc.label} respondeu: $info" },
                )
            }
        }
    }

    fun remove(acc: MediaServerAccount) {
        servers.remove(acc.id)
        local.update { it.copy(message = "${acc.label} removido", error = null) }
    }

    fun setShowInLibrary(on: Boolean) {
        prefs.mediaServersShow = on
    }

    fun clearMessage() = local.update { it.copy(error = null, message = null) }

    // ── Navegação das bibliotecas (a tela chama e mostra o erro real) ────────

    suspend fun libraries(acc: MediaServerAccount): Result<List<MediaLibrary>> = servers.libraries(acc)

    suspend fun libraryItems(acc: MediaServerAccount, library: MediaLibrary): Result<List<MediaItem>> =
        servers.libraryItems(acc, library)

    suspend fun seasons(acc: MediaServerAccount, series: MediaItem): Result<List<MediaItem>> =
        servers.seasons(acc, series)

    suspend fun episodes(acc: MediaServerAccount, series: MediaItem, season: MediaItem): Result<List<MediaItem>> =
        servers.episodes(acc, series, season)

    suspend fun search(acc: MediaServerAccount, query: String): Result<List<MediaItem>> =
        servers.search(acc, query)

    /** URL direta do vídeo no servidor, p/ tocar no player existente. */
    suspend fun streamUrl(acc: MediaServerAccount, item: MediaItem): String = servers.streamUrl(acc, item)

    /** Marca como visto (ou tira o visto). false quando o servidor recusou. */
    suspend fun setPlayed(acc: MediaServerAccount, item: MediaItem, played: Boolean): Boolean =
        servers.setPlayed(acc, item, played)
}
