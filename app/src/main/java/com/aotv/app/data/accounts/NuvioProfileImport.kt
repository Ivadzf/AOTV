package com.aotv.app.data.accounts

import com.aotv.app.data.collections.CollectionsRepository
import com.aotv.app.data.debrid.DebridProvider
import com.aotv.app.data.debrid.DebridService
import com.aotv.app.data.local.MAIN_PROFILE
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.Profile
import com.aotv.app.data.plugins.PluginRepo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Importa TODOS os perfis da conta Nuvio de uma vez (v1.46.0, Lote 1 item 1).
 * Desenho reimplementado do diálogo "De qual perfil do Nuvio?" do JOTV
 * 1.0.24 — nada de código do JOTV aqui, só o desenho: escolher "Todos os
 * perfis" ou perfis individuais, cada um virando um perfil do aparelho.
 *
 * Modelo correto (dex do JOTV 1.0.24): cada perfil do Nuvio tem
 * `usesPrimaryAddons` / `usesPrimaryPlugins`. Flag LIGADA → o perfil usa a
 * configuração primária (a do principal): addons, plugins e chaves de
 * debrid vão para os outros perfis sem configurar. Flag DESLIGADA →
 * configuração manual por perfil (repositórios de plugins diferentes,
 * addons diferentes, chaves diferentes).
 *
 * O que cada perfil do Nuvio traz para o perfil local dele:
 * - nome e foto (quando o servidor devolve a foto; senão mantém o avatar local);
 * - as flags primárias (`usesPrimaryAddons` / `usesPrimaryPlugins`), copiadas
 *   para o perfil local (o principal fica com as duas desligadas — ele JÁ é
 *   o primário);
 * - addons: por perfil, com herança primária — o perfil com a flag ligada
 *   funde os addons dele na lista do principal ([PlatformPrefs.addonOwnerId]),
 *   o manual funde na própria; mesclados por URL, sem apagar os que já
 *   estavam; o que também é por perfil são as fileiras da Início
 *   (home_layout_v1) e as fontes fixadas, que o layout recalcula a cada
 *   troca de perfil;
 * - repositórios de plugins: importáveis sim — [NuvioAccountService.plugins]
 *   lê a tabela `/rest/v1/plugins` (com resolução primária para o índice 1
 *   quando a flag está ligada) e cada repo entra na `pluginRepoList` do
 *   DONO do perfil, mesclado por URL, sem apagar os que já estavam e
 *   respeitando o `enabled` do servidor;
 * - Minha Lista e Continuar assistindo: vão para o perfil local certo
 *   (importLibraryForProfile/importProgressForProfile), e o progresso local
 *   vale quando é mais novo (LibraryRepository.importPlayback já só aceita
 *   progresso mais recente);
 * - coleções: por perfil (collections_v1 tem escopo); a importação troca
 *   temporariamente para o perfil de destino e volta no fim;
 * - chaves de debrid: por perfil, com herança primária — o perfil com a
 *   flag de addons primários ligada funde as chaves dele nas contas do
 *   principal, o manual guarda nas próprias; "primeira chave vence" só
 *   vale DENTRO do mesmo dono (uma chave de outro perfil do mesmo provedor
 *   NÃO é ignorada), sem sobrescrever a que já estava no dono.
 *
 * Antes de escrever os addons/plugins/debrid de um perfil, a importação
 * troca para o perfil local de destino (`prefs.selectProfile(local.id)`) —
 * os accessors ([PlatformPrefs.addonEntries], `pluginRepoList`,
 * [DebridService.accounts]/`connectKey`) resolvem o dono sozinhos, então
 * um perfil com flag primária escreve na lista do principal.
 *
 * O perfil principal do Nuvio (índice 1) SUBSTITUI o Principal do app
 * (perfil de id "main", [MAIN_PROFILE]): ele é renomeado com o nome (e a
 * foto, quando houver) do principal do Nuvio, sem apagar lista, progresso,
 * PIN, cor ou modo infantil dele. Sem o "main" (removido antes), o primeiro
 * perfil da lista faz o papel de principal.
 *
 * No fim a troca volta para o perfil principal importado usando o
 * [com.aotv.app.data.local.ProfileEpoch] — a MainActivity observa a troca
 * de perfil e recria a navegação sem reiniciar o app (v1.45.0).
 */
data class NuvioProfileImportReport(
    val profiles: Int,
    val created: Int,
    val replacedMain: Boolean,
    val mainName: String,
    val addons: ImportReport,
    val plugins: ImportReport,
    val library: ImportReport,
    val progress: ImportReport,
    val debrid: List<String>,
    val debridSkipped: List<String>,
    val collections: Int,
    val failed: List<String>,
) {
    /** Resumo em PT-BR para a tela de Contas / o diálogo de importação. */
    fun summary(): String = buildString {
        append(if (profiles == 1) "1 perfil do Nuvio importado" else "$profiles perfis do Nuvio importados")
        if (replacedMain) append(" — o Principal do app agora é $mainName")
        append(". ${addons.added} addons novos, ${plugins.added} repositórios de plugins novos, ${library.added} títulos na Minha Lista, ${progress.added} no Continuar assistindo")
        if (collections > 0) append(", $collections coleções")
        if (debrid.isNotEmpty()) append(". Debrid: ${debrid.joinToString(", ")}")
        if (debridSkipped.isNotEmpty()) append(" (chaves de ${debridSkipped.joinToString(", ")} já estavam no perfil de destino)")
        if (failed.isNotEmpty()) append(". Não veio: ${failed.joinToString("; ")}")
        append(". Addons, plugins e debrid seguem cada perfil (quem usa os primários herda os do Principal); fileiras e coleções são de cada perfil.")
    }
}

@Singleton
class NuvioProfileImport @Inject constructor(
    private val prefs: PlatformPrefs,
    private val nuvio: NuvioAccountService,
    private val importer: AccountImporter,
    private val debrid: DebridService,
    private val collections: CollectionsRepository,
) {
    /**
     * Resolve o perfil local de um perfil do Nuvio, criando quando não há.
     * Índice 1 vira o Principal do app (renomeado, nunca apagado); os
     * demais procuram primeiro pelo vínculo, depois pelo nome, e só então
     * criam um perfil novo. null quando o aparelho já tem o máximo de
     * perfis ([com.aotv.app.data.local.MAX_PROFILES]). As flags primárias
     * vêm do Nuvio (o principal fica com as duas desligadas — ele já é o
     * primário).
     */
    fun resolveLocalProfile(np: NuvioProfile): Profile? {
        val current = prefs.profiles.value
        if (np.index == 1) {
            val main = current.firstOrNull { it.id == MAIN_PROFILE } ?: current.firstOrNull() ?: return null
            val updated = main.copy(
                name = np.name,
                avatarUrl = np.avatarUrl.ifBlank { main.avatarUrl },
                usePrimaryAddons = false,
                usePrimaryPlugins = false,
                kids = main.kids || np.kids,
            )
            prefs.updateProfile(updated)
            prefs.setNuvioProfileLink(main.id, 1)
            return updated
        }
        val linkedId = prefs.nuvioProfileLinks.entries.firstOrNull { it.value == np.index }?.key
        val existing = linkedId?.let { id -> current.firstOrNull { it.id == id } }
            ?: current.firstOrNull { it.name.equals(np.name, ignoreCase = true) }
        if (existing != null) {
            val updated = existing.copy(
                avatarUrl = np.avatarUrl.ifBlank { existing.avatarUrl },
                usePrimaryAddons = np.usesPrimaryAddons,
                usePrimaryPlugins = np.usesPrimaryPlugins,
                kids = existing.kids || np.kids,
            )
            prefs.updateProfile(updated)
            prefs.setNuvioProfileLink(existing.id, np.index)
            return updated
        }
        val created = prefs.addProfile(np.name, kids = np.kids) ?: return null
        val updated = created.copy(
            avatarUrl = np.avatarUrl, usePrimaryAddons = np.usesPrimaryAddons,
            usePrimaryPlugins = np.usesPrimaryPlugins,
        )
        prefs.updateProfile(updated)
        prefs.setNuvioProfileLink(created.id, np.index)
        return updated
    }

    /**
     * Importa os perfis escolhidos ([selected] vazio = todos). Um perfil
     * que falha (rede, sem correspondência) não trava os outros — o nome
     * dele vai para [NuvioProfileImportReport.failed]. [onProgress] avança
     * 6 passos por perfil (addons, plugins, lista, progresso, debrid,
     * coleções). Antes dos passos de addons/plugins/debrid a importação
     * troca para o perfil local de destino, para os accessors resolverem o
     * dono certo (principal, quando a flag primária está ligada).
     */
    suspend fun importAll(selected: Set<Int>, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result<NuvioProfileImportReport> {
        val all = nuvio.profiles().getOrElse { return Result.failure(it) }
        val wanted = if (selected.isEmpty()) all else all.filter { it.index in selected }
        if (wanted.isEmpty()) return Result.failure(IllegalStateException("Nenhum perfil do Nuvio selecionado"))
        val originalProfileId = prefs.profileId.value
        val profilesBefore = prefs.profiles.value.size
        val total = wanted.size * 6
        var done = 0
        var replacedMain = false
        var mainName = ""
        var mainLocalId: String? = null
        var addons = ImportReport(0, 0, 0)
        var plugins = ImportReport(0, 0, 0)
        var library = ImportReport(0, 0, 0)
        var progress = ImportReport(0, 0, 0)
        var collectionsCount = 0
        val debridOk = mutableListOf<String>()
        val debridSkipped = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (np in wanted) {
            val local = resolveLocalProfile(np)
            if (local == null) {
                failed += "${np.name} (sem espaço para outro perfil)"
                repeat(6) { done++; onProgress(done, total) }
                continue
            }
            if (np.index == 1) {
                replacedMain = true
                mainName = np.name
                mainLocalId = local.id
            }
            // Addons, plugins e debrid são do DONO do perfil: troca para o
            // destino antes de escrever — o accessor resolve o principal
            // quando a flag primária está ligada, e o próprio perfil quando
            // a configuração é manual. Nada do que já estava é apagado.
            if (prefs.profileId.value != local.id) prefs.selectProfile(local.id)
            runCatching {
                val list = nuvio.addons(np).getOrThrow()
                val r = importer.importAddons(list)
                addons = ImportReport(addons.added + r.added, addons.skipped + r.skipped, addons.failed + r.failed)
            }.onFailure { failed += "${np.name}: addons (${it.message ?: "sem resposta"})" }
            done++; onProgress(done, total)
            runCatching {
                val remote = nuvio.plugins(np).getOrThrow()
                val current = prefs.pluginRepoList
                val merged = current.toMutableList()
                var added = 0
                var skipped = 0
                remote.forEach { rp ->
                    val url = rp.url.trim()
                    if (url.isBlank()) {
                        skipped++
                        return@forEach
                    }
                    val i = merged.indexOfFirst { it.url.equals(url, true) }
                    if (i >= 0) {
                        // Já estava no dono: não apaga, não duplica e não
                        // religa — o `enabled` local vale (o Nuvio devolve
                        // sempre true; pausar é decisão local).
                        skipped++
                    } else {
                        merged += PluginRepo(url = url, name = rp.name, enabled = rp.enabled)
                        added++
                    }
                }
                if (merged != current) prefs.pluginRepoList = merged
                plugins = ImportReport(plugins.added + added, plugins.skipped + skipped, plugins.failed)
            }.onFailure { failed += "${np.name}: plugins (${it.message ?: "sem resposta"})" }
            done++; onProgress(done, total)
            runCatching {
                val r = importer.importLibraryForProfile(nuvio.library(np).getOrThrow(), local.id)
                library = ImportReport(library.added + r.added, library.skipped + r.skipped, library.failed + r.failed)
            }.onFailure { failed += "${np.name}: Minha Lista (${it.message ?: "sem resposta"})" }
            done++; onProgress(done, total)
            runCatching {
                val r = importer.importProgressForProfile(nuvio.progress(np).getOrThrow(), local.id)
                progress = ImportReport(progress.added + r.added, progress.skipped + r.skipped, progress.failed + r.failed)
            }.onFailure { failed += "${np.name}: Continuar assistindo (${it.message ?: "sem resposta"})" }
            done++; onProgress(done, total)
            runCatching {
                val keys = nuvio.debridKeys(np).getOrThrow()
                keys.forEach { (id, key) ->
                    val provider = DebridProvider.of(id) ?: return@forEach
                    // "Primeira chave vence" só DENTRO do mesmo dono: os
                    // accessors já resolvem as contas do dono do perfil
                    // atual, então a chave de outro perfil do mesmo
                    // provedor NÃO é ignorada aqui.
                    if (debrid.accounts.value.any { it.provider == provider }) {
                        if (provider.label !in debridSkipped) debridSkipped += provider.label
                    } else if (debrid.connectKey(provider, key).isSuccess) {
                        if (provider.label !in debridOk) debridOk += provider.label
                    }
                }
            }.onFailure { failed += "${np.name}: debrid (${it.message ?: "sem resposta"})" }
            done++; onProgress(done, total)
            runCatching {
                val json = nuvio.collectionsJson(np).getOrThrow() ?: return@runCatching
                // Coleções são do perfil ATIVO: troca para o destino, importa
                // e segue — a troca final (abaixo) deixa no principal.
                if (prefs.profileId.value != local.id) prefs.selectProfile(local.id)
                collectionsCount += collections.importJson(json).getOrThrow()
            }.onFailure { failed += "${np.name}: coleções (${it.message ?: "sem resposta"})" }
            done++; onProgress(done, total)
        }
        // Fim: volta para o principal importado (ou para onde estava, se o
        // principal não estava na escolha). A MainActivity observa a troca
        // e recria a navegação com ProfileEpoch — sem reiniciar o app.
        val landing = mainLocalId ?: originalProfileId
        if (prefs.profileId.value != landing) prefs.selectProfile(landing)
        // v1.46.1 (hotfix): uma colisão de nome (ex.: Principal renomeado
        // para um nome que já existia) é limpa na hora, sem esperar a
        // próxima abertura do app.
        prefs.dedupeProfiles()
        // Perfis criados de verdade: diferença no tamanho da lista.
        val created = (prefs.profiles.value.size - profilesBefore).coerceAtLeast(0)
        return Result.success(
            NuvioProfileImportReport(
                profiles = wanted.size, created = created, replacedMain = replacedMain, mainName = mainName,
                addons = addons, plugins = plugins, library = library, progress = progress,
                debrid = debridOk, debridSkipped = debridSkipped,
                collections = collectionsCount, failed = failed,
            ),
        )
    }
}
