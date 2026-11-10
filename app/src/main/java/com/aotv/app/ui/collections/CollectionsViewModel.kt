package com.aotv.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.accounts.NuvioAccountService
import com.aotv.app.data.collections.CollectionsRepository
import com.aotv.app.data.collections.MediaCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repo: CollectionsRepository,
    private val nuvio: NuvioAccountService,
    private val http: OkHttpClient,
) : ViewModel() {

    /** [busy] é a importação em andamento ("paste", "url" ou "nuvio"). */
    data class Ui(
        val collections: List<MediaCollection> = emptyList(),
        val busy: String? = null,
        val message: String? = null,
        val error: String? = null,
        val nuvioLogged: Boolean = false,
    )

    private val local = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = combine(repo.flow, local, nuvio.session) { cols, l, s -> l.copy(collections = cols, nuvioLogged = s != null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ui())

    fun move(id: String, delta: Int) = repo.move(id, delta)

    fun remove(id: String) {
        val title = repo.get(id)?.title.orEmpty()
        repo.remove(id)
        local.update { it.copy(message = "Coleção $title apagada", error = null) }
    }

    fun togglePin(id: String) { repo.get(id)?.let { repo.upsert(it.copy(pinToTop = !it.pinToTop)) } }

    fun exportJson(): String = repo.exportJson()

    fun importText(text: String) = act("paste") { imported(repo.importJson(text).getOrThrow()) }

    fun importUrl(url: String) = act("url") {
        val u = url.trim()
        require(u.startsWith("https://", true) || u.startsWith("http://", true)) { "O link precisa começar com https://" }
        val text = fetch(u) ?: error("Não deu para baixar o JSON desse link")
        imported(repo.importJson(text).getOrThrow())
    }

    /** Perfil principal da conta Nuvio. Para escolher outro perfil, use Contas. */
    fun importNuvio() = act("nuvio") {
        val p = nuvio.mainProfile()
        val json = nuvio.collectionsJson(p).getOrThrow() ?: return@act "O perfil ${p.name} do Nuvio não tem coleções"
        imported(repo.importJson(json).getOrThrow()) + " do perfil ${p.name} do Nuvio"
    }

    private fun imported(n: Int) = if (n == 1) "1 coleção importada" else "$n coleções importadas"

    fun clearMessage() = local.update { it.copy(message = null, error = null) }

    private fun act(key: String, block: suspend () -> String) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = key, message = null, error = null) }
        viewModelScope.launch {
            val r = runCatching { block() }
            local.update { it.copy(busy = null, message = r.getOrNull(), error = r.exceptionOrNull()?.let { e -> e.message ?: "Não deu certo" }) }
        }
    }

    /** Baixa o texto com teto de tamanho. JSON de coleção é pequeno, e arquivo enorme derrubaria o app. */
    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val src = r.body?.source() ?: return@use null
                if (src.request(MAX_BYTES + 1L)) return@use null
                src.readUtf8()
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_BYTES = 2L * 1024 * 1024
    }
}
