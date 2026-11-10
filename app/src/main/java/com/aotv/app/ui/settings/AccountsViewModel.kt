package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.accounts.AccountImporter
import com.aotv.app.data.accounts.ImportReport
import com.aotv.app.data.accounts.NuvioAccountService
import com.aotv.app.data.accounts.NuvioDeviceCode
import com.aotv.app.data.accounts.NuvioProfile
import com.aotv.app.data.accounts.NuvioSession
import com.aotv.app.data.accounts.StremioAccountService
import com.aotv.app.data.accounts.StremioLink
import com.aotv.app.data.accounts.StremioSession
import com.aotv.app.data.debrid.DebridProvider
import com.aotv.app.data.debrid.DebridService
import com.aotv.app.data.local.PlatformPrefs
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
class AccountsViewModel @Inject constructor(
    private val stremio: StremioAccountService,
    private val nuvio: NuvioAccountService,
    private val importer: AccountImporter,
    private val debrid: DebridService,
    private val prefs: PlatformPrefs,
    private val collections: com.aotv.app.data.collections.CollectionsRepository,
) : ViewModel() {

    data class Ui(
        val stremio: StremioSession? = null,
        val nuvio: NuvioSession? = null,
        /** Ação em andamento ("stremio_login", "nuvio_addons"...). Trava os outros botões. */
        val busy: String? = null,
        val progress: String? = null,
        val message: String? = null,
        val error: String? = null,
        val stremioLink: StremioLink? = null,
        val nuvioCode: NuvioDeviceCode? = null,
        val nuvioProfiles: List<NuvioProfile> = emptyList(),
        val nuvioProfile: Int = 1,
        /** Perfil do AOTV que recebe a importação. */
        val profileName: String = "",
    )

    private val local = MutableStateFlow(Ui(profileName = prefs.profile.name))
    val ui: StateFlow<Ui> = combine(local, stremio.session, nuvio.session, prefs.profileId, prefs.profiles) { l, s, n, pid, plist ->
        l.copy(
            stremio = s, nuvio = n,
            // "Importa para" segue o perfil local ativo.
            profileName = plist.firstOrNull { it.id == pid }?.name ?: l.profileName,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    private var codeJob: Job? = null

    init {
        if (nuvio.session.value != null) loadNuvioProfiles()
        // Troca de perfil local: a origem da importação segue o vínculo dele.
        viewModelScope.launch {
            prefs.profileId.collect { pid ->
                prefs.nuvioProfileIndexFor(pid)?.let { linked ->
                    local.update { it.copy(nuvioProfile = linked) }
                }
            }
        }
    }

    /** Roda uma ação de rede com o botão marcado e travado até a resposta. */
    private fun act(tag: String, block: suspend () -> String?) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = tag, error = null, message = null, progress = null) }
        viewModelScope.launch {
            val msg = runCatching { block() }
            local.update {
                it.copy(
                    busy = null, progress = null,
                    message = msg.getOrNull(), error = msg.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun step(done: Int, total: Int) = local.update { it.copy(progress = "Importando $done de $total") }

    // ── Stremio ─────────────────────────────────────────────────────────────────

    fun stremioLogin(email: String, password: String) = act("stremio_login") {
        val s = stremio.login(email, password).getOrThrow()
        "Conectado como ${s.email}"
    }

    fun stremioCode() {
        if (local.value.busy != null) return
        local.update { it.copy(busy = "stremio_code", error = null, message = null) }
        codeJob?.cancel()
        codeJob = viewModelScope.launch {
            val link = stremio.linkStart().getOrElse { e -> local.update { it.copy(busy = null, error = e.message) }; return@launch }
            local.update { it.copy(stremioLink = link) }
            val until = System.currentTimeMillis() + 5 * 60_000
            while (System.currentTimeMillis() < until) {
                delay(3_000)
                val s = stremio.linkPoll(link)
                if (s != null) {
                    local.update { it.copy(busy = null, stremioLink = null, message = "Conectado" + if (s.email.isNotBlank()) " como ${s.email}" else "") }
                    return@launch
                }
            }
            local.update { it.copy(busy = null, stremioLink = null, error = "O código venceu. Peça outro.") }
        }
    }

    fun stremioImportAddons() = act("stremio_addons") {
        val list = stremio.addons().getOrThrow()
        addonsMessage(importer.importAddons(list, ::step), list.count { !it.canUse })
    }

    fun stremioImportLibrary() = act("stremio_library") {
        val (lib, prog) = stremio.library().getOrThrow()
        val l = importer.importLibrary(lib, ::step)
        val p = importer.importProgress(prog, ::step)
        libraryMessage(l, p)
    }

    fun stremioPushAddons() = act("stremio_push") {
        val n = stremio.pushAddons(prefs.addonEntries.filter { it.enabled }.map { it.url }).getOrThrow()
        if (n == 0) "A conta Stremio já tinha todos os seus addons" else "$n addons enviados para a conta Stremio"
    }

    fun stremioLogout() { stremio.logout(); local.update { it.copy(message = "Saiu da conta Stremio") } }

    // ── Nuvio ───────────────────────────────────────────────────────────────────

    fun nuvioLogin(email: String, password: String, server: String) = act("nuvio_login") {
        val s = nuvio.login(email, password, server.ifBlank { NuvioAccountService.OFFICIAL }).getOrThrow()
        fetchProfiles()
        "Conectado como ${s.email}"
    }

    fun nuvioCode(server: String) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = "nuvio_code", error = null, message = null) }
        codeJob?.cancel()
        codeJob = viewModelScope.launch {
            val code = nuvio.deviceStart(server.ifBlank { NuvioAccountService.OFFICIAL }).getOrElse { e ->
                local.update { it.copy(busy = null, error = e.message) }; return@launch
            }
            local.update { it.copy(nuvioCode = code) }
            repeat(120) {
                delay(code.intervalSec * 1000L)
                val r = nuvio.devicePoll(code)
                r.exceptionOrNull()?.let { e -> local.update { it.copy(busy = null, nuvioCode = null, error = e.message) }; return@launch }
                val s = r.getOrNull()
                if (s != null) {
                    runCatching { fetchProfiles() }
                    local.update { it.copy(busy = null, nuvioCode = null, message = "Conectado" + if (s.email.isNotBlank()) " como ${s.email}" else "") }
                    return@launch
                }
            }
            local.update { it.copy(busy = null, nuvioCode = null, error = "O código venceu. Peça outro.") }
        }
    }

    fun cancelCode() {
        codeJob?.cancel()
        local.update { it.copy(busy = null, stremioLink = null, nuvioCode = null) }
    }

    private fun loadNuvioProfiles() = viewModelScope.launch { runCatching { fetchProfiles() } }

    private suspend fun fetchProfiles() {
        val list = nuvio.profiles().getOrThrow()
        // Cria no app os perfis do Nuvio que ainda não existem (só o nome, com o vínculo
        // para a importação). Nada é importado aqui e nenhum perfil local é apagado.
        list.forEach { np ->
            val existing = prefs.profiles.value.firstOrNull { it.name.equals(np.name, ignoreCase = true) }
            val linked = existing ?: prefs.addProfile(np.name, kids = false)
            if (linked != null) prefs.setNuvioProfileLink(linked.id, np.index)
        }
        val active = prefs.nuvioProfileIndexFor(prefs.profileId.value)
        local.update { it.copy(nuvioProfiles = list, nuvioProfile = active ?: list.firstOrNull()?.index ?: 1) }
    }

    fun pickNuvioProfile(index: Int) {
        // O toque no perfil do Nuvio troca também o perfil LOCAL ativo: a importação
        // (addons, lista, progresso, debrid, coleções) cai no perfil com o nome certo
        // (Juliana/Davi/Igor), em vez de cair sempre no Principal. Antes o toque só
        // mudava DE ONDE os dados vinham; o destino era sempre o perfil local ativo.
        val name = local.value.nuvioProfiles.firstOrNull { it.index == index }?.name
        var localId = prefs.nuvioProfileLinks.entries.firstOrNull { it.value == index }?.key
        if (localId == null && name != null) {
            localId = prefs.profiles.value.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
            if (localId != null) prefs.setNuvioProfileLink(localId, index)
        }
        if (localId == null && name != null) {
            localId = prefs.addProfile(name, kids = false)?.id
            if (localId != null) prefs.setNuvioProfileLink(localId, index)
        }
        if (localId != null && prefs.profileId.value != localId) prefs.selectProfile(localId)
        local.update { it.copy(nuvioProfile = index) }
    }

    private fun nuvioProfile(): NuvioProfile {
        val index = prefs.nuvioProfileIndexFor(prefs.profileId.value) ?: local.value.nuvioProfile
        return local.value.nuvioProfiles.firstOrNull { it.index == index } ?: NuvioProfile(index, "Perfil", false)
    }

    fun nuvioImportAddons() = act("nuvio_addons") {
        val list = nuvio.addons(nuvioProfile()).getOrThrow()
        addonsMessage(importer.importAddons(list, ::step), list.count { !it.canUse })
    }

    fun nuvioImportLibrary() = act("nuvio_library") {
        val p = nuvioProfile()
        val l = importer.importLibrary(nuvio.library(p).getOrThrow(), ::step)
        val w = importer.importProgress(nuvio.progress(p).getOrThrow(), ::step)
        libraryMessage(l, w)
    }

    /** Chaves de debrid salvas na conta Nuvio. Cada uma é conferida no serviço antes de entrar. */
    fun nuvioImportDebrid() = act("nuvio_debrid") {
        val keys = nuvio.debridKeys(nuvioProfile()).getOrThrow()
        if (keys.isEmpty()) return@act "A conta Nuvio não tem chave de debrid salva"
        val ok = keys.mapNotNull { (id, key) -> DebridProvider.of(id)?.let { p -> if (debrid.connectKey(p, key).isSuccess) p.label else null } }
        if (ok.isEmpty()) error("As chaves da conta Nuvio não foram aceitas pelos serviços")
        "Debrid conectado (${ok.joinToString(", ")})"
    }

    /** Coleções do perfil escolhido no Nuvio. Mesmo id substitui a coleção que já estava aqui. */
    fun nuvioImportCollections() = act("nuvio_collections") {
        val json = nuvio.collectionsJson(nuvioProfile()).getOrThrow()
            ?: return@act "Esse perfil do Nuvio não tem coleções"
        val n = collections.importJson(json).getOrThrow()
        (if (n == 1) "1 coleção importada" else "$n coleções importadas") + " para a Início do perfil ${local.value.profileName}"
    }

    fun nuvioLogout() { nuvio.logout(); local.update { it.copy(nuvioProfiles = emptyList(), message = "Saiu da conta Nuvio") } }

    fun clearMessage() = local.update { it.copy(message = null, error = null) }

    private fun addonsMessage(r: ImportReport, unusable: Int): String = buildString {
        append(if (r.added == 0) "Nenhum addon novo" else "${r.added} addons importados")
        val extra = listOfNotNull(
            (r.skipped - unusable).takeIf { it > 0 }?.let { "$it já estavam" },
            unusable.takeIf { it > 0 }?.let { "$it só rodam no outro app" },
            r.failed.takeIf { it > 0 }?.let { "$it não responderam" },
        )
        if (extra.isNotEmpty()) append(" (").append(extra.joinToString(", ")).append(")")
    }

    private fun libraryMessage(list: ImportReport, progress: ImportReport): String =
        "${list.added} títulos na Minha Lista e ${progress.added} no Continuar assistindo do perfil ${local.value.profileName}" +
            (list.failed + progress.failed).takeIf { it > 0 }?.let { " ($it sem correspondência no TMDB)" }.orEmpty()
}
