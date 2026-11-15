package com.aotv.app.data.local

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Variantes de ícone do app (Lote 11, v1.21.0 — ideia do Tuvora).
 *
 * O ícone atual (fundo preto, wordmark vermelho escuro + turquesa) continua
 * sendo o padrão e não muda: ele é a MainActivity, sem alias. Cada cor é um
 * `activity-alias` no AndroidManifest.xml (desligado por padrão) com o mesmo
 * wordmark sobre um fundo colorido — a troca liga o alias da cor e desliga a
 * MainActivity (ou o contrário, ao voltar p/ o Padrão), via
 * [applyVariant]. Nada é copiado do Tuvora: só a ideia (ícones coloridos
 * escolhíveis); o desenho é o wordmark do AOTV e as cores são as do app
 * (as de [PROFILE_COLORS] + o vermelho escuro do tema).
 *
 * Os deep links (AniList e Watch Party) estão duplicados nos aliases: com a
 * MainActivity desligada (ícone colorido ativo), o convite e o retorno do
 * OAuth continuam abrindo o app — sem duplicar, o link não resolveria. Em
 * cada estado, só UM componente está ligado, então o link nunca casa duas
 * vezes. O atalho antigo na tela inicial aponta p/ a MainActivity: com um
 * ícone colorido ativo ele pode falhar — voltar ao Padrão restaura.
 *
 * A troca usa DONT_KILL_APP (o app continua aberto); o launcher atualiza o
 * ícone em alguns segundos — alguns launchers só depois de reabrir o app.
 * A variante é DO APARELHO (pref global `app_icon_variant`, ver
 * [PlatformPrefs.appIconVariant]) e viaja no backup.
 */
data class AppIconVariant(
    val id: String,
    val label: String,
    /** Cor de fundo do ícone, ARGB (a do drawable de fundo da variante). */
    val backgroundColor: Long,
    /** Sufixo do activity-alias no manifesto ("" = o ícone padrão, sem alias). */
    val aliasSuffix: String,
)

object AppIconVariants {
    const val DEFAULT_ID = "default"

    val VARIANTS = listOf(
        AppIconVariant(DEFAULT_ID, "Padrão", 0xFF000000, ""),
        AppIconVariant("red", "Vermelho", 0xFF9E1B1B, "Red"),
        AppIconVariant("teal", "Turquesa", 0xFF00C2B8, "Teal"),
        AppIconVariant("green", "Verde", 0xFF3DDC84, "Green"),
        AppIconVariant("amber", "Amarelo", 0xFFF5A524, "Amber"),
        AppIconVariant("purple", "Roxo", 0xFFA970FF, "Purple"),
        AppIconVariant("pink", "Rosa", 0xFFFF6FB5, "Pink"),
    )

    fun byId(id: String): AppIconVariant? = VARIANTS.firstOrNull { it.id == id }

    /** Componente do alias de uma variante (null = o ícone padrão, sem alias). */
    fun aliasComponent(context: Context, variant: AppIconVariant): ComponentName? =
        variant.aliasSuffix.takeIf { it.isNotEmpty() }?.let { ComponentName(context, "com.aotv.app.AppIcon$it") }

    /**
     * Variante ativa de fato (lê o que o PackageManager tem ligado). Sem
     * alias ligado = o Padrão. Serve p/ corrigir a pref quando a troca
     * falha (a pref nunca pode dizer uma cor que não está ativa).
     */
    fun currentVariantId(context: Context): String {
        val pm = context.packageManager
        for (v in VARIANTS) {
            val alias = aliasComponent(context, v) ?: continue
            if (pm.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return v.id
        }
        return DEFAULT_ID
    }

    /**
     * Troca o ícone do app p/ a variante [variantId]. Id desconhecido = sem
     * troca (false). NÃO grava a pref: quem chama grava
     * [PlatformPrefs.appIconVariant] depois que isto devolver true.
     */
    fun applyVariant(context: Context, variantId: String): Boolean {
        val variant = byId(variantId) ?: return false
        val pm = context.packageManager
        return runCatching {
            val main = ComponentName(context, "com.aotv.app.MainActivity")
            val useAlias = variant.aliasSuffix.isNotEmpty()
            pm.setComponentEnabledSetting(
                main,
                if (useAlias) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            VARIANTS.forEach { v ->
                aliasComponent(context, v)?.let { alias ->
                    pm.setComponentEnabledSetting(
                        alias,
                        if (v.id == variant.id) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
            true
        }.getOrDefault(false)
    }
}
