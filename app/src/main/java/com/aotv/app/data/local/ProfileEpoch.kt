package com.aotv.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Troca de perfil SEM reiniciar o app (v1.45.0, item 1 — ideia do
 * NuvioAstra, reimplementada: nada de código do Astra aqui).
 *
 * O AOTV reiniciava a Activity inteira a cada troca (`MainActivity.restartApp`)
 * porque o restart era a rede de segurança que limpava o estado: ViewModels
 * com o perfil antigo, caches em memória e respostas de rede tardias. A
 * troca em processo replica essa limpeza em 3 camadas:
 *
 * 1. `refreshLayout()` (já existia em [PlatformPrefs.selectProfile]) relê
 *    TODAS as prefs por perfil nos fluxos — nada muda aqui.
 * 2. [beginSwitch] invalida os caches em memória registrados aqui
 *    (IntroDB, SkipSegments e outros serviços se registram no `init`) e
 *    avança o [epoch].
 * 3. A Activity observa o [epoch] e recria a navegação com `key(epoch)` —
 *    os ViewModels do grafo nascem de novo já com o perfil novo, sem
 *    recriar a Activity.
 *
 * Respostas tardias do perfil anterior: quem busca dados por perfil carimba
 * o epoch no início ([beginSwitch] devolve o novo) e descarta o resultado
 * quando [isCurrent] é false — ver o uso em `MainActivity`.
 *
 * O QUE AINDA REINICIA: nada por padrão. Se um subsistema não puder ser
 * invalidado com segurança no futuro, ele deve se registrar aqui com um
 * invalidade que retorna false — hoje todos os registrados sempre limpam
 * (são só `clear()` de mapa), então não há fallback de restart. Singletons
 * Hilt que guardam estado por perfil relêem as prefs por fluxo
 * (refreshLayout) em vez de guardar cópia.
 */
object ProfileEpoch {

    private val _epoch = MutableStateFlow(0L)

    /** Epoch atual da sessão de perfil. Muda a cada troca de perfil. */
    val epoch: StateFlow<Long> = _epoch.asStateFlow()

    private val invalidators = ConcurrentHashMap<String, () -> Unit>()

    /**
     * Registra uma limpeza de cache p/ a troca de perfil. Chamado no `init`
     * do serviço dono do cache (o registro sobrevive à troca; a limpeza roda
     * a cada [beginSwitch]). O nome só serve p/ depurar.
     */
    fun registerInvalidator(name: String, invalidate: () -> Unit) {
        invalidators[name] = invalidate
    }

    /**
     * Troca de perfil em processo: limpa os caches registrados (falha de um
     * não trava os outros) e avança o epoch. Devolve o epoch novo, p/ quem
     * busca dados carimbar a resposta.
     */
    fun beginSwitch(): Long {
        invalidators.values.forEach { runCatching { it() } }
        val next = System.currentTimeMillis()
        _epoch.value = next
        return next
    }

    /** A resposta carimbada com este epoch ainda é do perfil atual? */
    fun isCurrent(epoch: Long): Boolean = epoch == _epoch.value
}
