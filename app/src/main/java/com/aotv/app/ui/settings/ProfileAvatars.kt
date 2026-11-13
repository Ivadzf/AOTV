package com.aotv.app.ui.settings

/**
 * Catálogo de avatares prontos (Lote 3 item 2b, v1.11.0) — ref. foto do Nuvio
 * "Editar perfil" (1001054594), que mostra uma grade de avatares ilustrados
 * (animais e personagens, ex. "Kai").
 *
 * O catálogo é 100% LOCAL, sem URL de terceiros e sem credencial: cada preset
 * é um emoji desenhado pelo sistema sobre um círculo colorido — nada é baixado
 * nem embutido como imagem. O id guardado no [com.aotv.app.data.local.Profile.avatarId]
 * é só o id da lista abaixo; id desconhecido (ou vazio) volta p/ a inicial
 * colorida atual. A URL personalizada ([com.aotv.app.data.local.Profile.avatarUrl])
 * tem prioridade sobre o catálogo quando carrega; se falhar, cai p/ o catálogo
 * e depois p/ a inicial — nunca trava a tela.
 */
data class AvatarPreset(val id: String, val name: String, val glyph: String, val color: Int)

val AVATAR_CATALOG = listOf(
    AvatarPreset("urso", "Urso", "\uD83D\uDC3B", 0xFFE2703A.toInt()),
    AvatarPreset("vaca", "Vaca", "\uD83D\uDC2E", 0xFF7C5CFF.toInt()),
    AvatarPreset("porco", "Porco", "\uD83D\uDC37", 0xFF4CAF50.toInt()),
    AvatarPreset("coruja", "Coruja", "\uD83E\uDD89", 0xFF009688.toInt()),
    AvatarPreset("leao", "Leão", "\uD83E\uDD81", 0xFF2196F3.toInt()),
    AvatarPreset("gato", "Gato", "\uD83D\uDC31", 0xFF8BC34A.toInt()),
    AvatarPreset("elefante", "Elefante", "\uD83D\uDC18", 0xFFFFC107.toInt()),
    AvatarPreset("raposa", "Raposa", "\uD83E\uDD8A", 0xFF66BB6A.toInt()),
    AvatarPreset("coelho", "Coelho", "\uD83D\uDC30", 0xFFFF7043.toInt()),
    AvatarPreset("panda", "Panda", "\uD83D\uDC3C", 0xFFFF9800.toInt()),
    AvatarPreset("robo", "Robô", "\uD83E\uDD16", 0xFF607D8B.toInt()),
    AvatarPreset("astronauta", "Astronauta", "\uD83E\uDDD1\u200D\uD83D\uDE80", 0xFF5C6BC0.toInt()),
    AvatarPreset("kai", "Kai", "\uD83D\uDC71\u200D\u2642\uFE0F", 0xFF00796B.toInt()),
)

/** Preset pelo id; null quando o id não está no catálogo (perfil antigo ou id inválido). */
fun avatarPreset(id: String): AvatarPreset? = AVATAR_CATALOG.firstOrNull { it.id == id }

/** URL de avatar válida p/ carregar: http/https, sem espaço. "" = sem URL (usa catálogo/inicial). */
fun validAvatarUrl(url: String): String {
    val u = url.trim()
    return if ((u.startsWith("http://") || u.startsWith("https://")) && !u.contains(" ")) u else ""
}
