package com.aotv.app.data.sports

/**
 * Fix Channel (Lote 13, v1.23.0 — item 12 da análise PlusIPTV).
 *
 * Quando o canal achado p/ um jogo está errado, o usuário escolhe o certo
 * UMA vez e o app lembra. Escopos, do mais específico ao mais geral:
 * - GAME: só aquele jogo (chave = id do evento da ESPN).
 * - TEAM: todos os jogos de um time (chave = nome do time normalizado).
 * - NETWORK: todos os jogos de uma rede/emissora (chave = nome da rede
 *   normalizado, ex. "premiere" — a rede é a da League Networks).
 *
 * A resolução é GAME > TEAM > NETWORK: o mais específico ganha. A tabela
 * vive nas prefs do perfil (`sports_fix_channel_v1`, mapa JSON no padrão
 * de `live_audio_v1`, no máximo 100 — ver PlatformPrefs). Só guarda ids
 * de canal das próprias listas do usuário — nada de fora, nada embutido.
 * Sem fix, o jogo toca pelo canal da League Networks (ou pelo canal do
 * EPG, quando o jogo veio de lá).
 */

/** Escopo do override de canal. */
enum class FixScope { GAME, NETWORK, TEAM }

/** Um override: escopo + chave → canal escolhido (id + nome p/ mostrar). */
data class ChannelFix(
    val scope: FixScope,
    val key: String,
    val channelId: String,
    val channelName: String,
    val at: Long,
)

/** Chave do mapa: "game:<id>", "network:<rede>" ou "team:<time>". */
fun fixMapKey(scope: FixScope, key: String): String =
    scope.name.lowercase() + ":" + normalizeSportsName(key)

/**
 * Resolve o canal fixado p/ um jogo. [eventId] = id do evento ESPN (null
 * quando o jogo veio do EPG); [networkKey] = rede da League Networks
 * (ex. "Premiere"); [teamNames] = os dois times do jogo. Ordem: jogo >
 * time (casa ou fora) > rede. null = sem fix — usa o canal achado.
 */
fun resolveFixedChannel(
    fixes: Map<String, ChannelFix>,
    eventId: String?,
    networkKey: String?,
    teamNames: List<String>,
): ChannelFix? {
    if (fixes.isEmpty()) return null
    if (!eventId.isNullOrBlank()) {
        fixes[fixMapKey(FixScope.GAME, eventId)]?.let { return it }
    }
    teamNames.forEach { team ->
        fixes[fixMapKey(FixScope.TEAM, team)]?.let { return it }
    }
    if (!networkKey.isNullOrBlank()) {
        fixes[fixMapKey(FixScope.NETWORK, networkKey)]?.let { return it }
    }
    return null
}

/** Rótulo do escopo p/ mostrar na lista ("Este jogo", "Todos os jogos do Flamengo"...). */
fun fixScopeLabel(fix: ChannelFix): String = when (fix.scope) {
    FixScope.GAME -> "Este jogo"
    FixScope.TEAM -> "Todos os jogos do ${fix.key}"
    FixScope.NETWORK -> "Todos os jogos da ${fix.key}"
}
