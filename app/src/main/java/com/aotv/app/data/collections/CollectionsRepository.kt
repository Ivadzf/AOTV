package com.aotv.app.data.collections

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coleções do perfil ativo. Ficam no PlatformPrefs junto do layout da Início, então salvar uma
 * coleção já atualiza as fileiras e a troca de perfil recarrega as coleções dele.
 */
@Singleton
class CollectionsRepository @Inject constructor(private val prefs: PlatformPrefs) {

    val flow: StateFlow<List<MediaCollection>> get() = prefs.collectionsFlow
    val list: List<MediaCollection> get() = prefs.collectionsFlow.value

    fun get(id: String): MediaCollection? = list.firstOrNull { it.id == id }

    fun upsert(c: MediaCollection) {
        val cur = list
        val i = cur.indexOfFirst { it.id == c.id }
        prefs.saveCollections(if (i < 0) cur + c else cur.toMutableList().also { it[i] = c })
    }

    fun remove(id: String) = prefs.saveCollections(list.filterNot { it.id == id })

    fun move(id: String, delta: Int) {
        val cur = list.toMutableList()
        val i = cur.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in cur.indices) return
        val t = cur[i]; cur[i] = cur[j]; cur[j] = t
        prefs.saveCollections(cur)
    }

    /**
     * Junta as coleções do JSON às do perfil. Mesmo id substitui a antiga (o Nuvio troca tudo,
     * aqui nada se perde sem querer). Devolve quantas coleções entraram.
     */
    fun importJson(text: String): Result<Int> = CollectionsJson.parse(text).map { incoming ->
        val byId = LinkedHashMap<String, MediaCollection>()
        list.forEach { byId[it.id] = it }
        incoming.forEach { byId[it.id] = it }
        prefs.saveCollections(byId.values.toList())
        incoming.size
    }

    fun exportJson(): String = CollectionsJson.encode(list, pretty = true)

    fun newId(): String = java.util.UUID.randomUUID().toString()
}
