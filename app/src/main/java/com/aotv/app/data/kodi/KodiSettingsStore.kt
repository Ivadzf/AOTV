package com.aotv.app.data.kodi

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings dos addons Kodi (v1.1, Fase 1).
 *
 * Lê as definições de `resources/settings.xml` do addon e os valores
 * persistidos em `addon_data/<id>/settings.xml` (formato do Kodi:
 * `<settings><setting id="..." value="..."/></settings>`).
 *
 * `getSetting` devolve sempre string (formato do Kodi: bool é
 * "true"/"false", enum é o índice como string). É o mesmo contrato que
 * o Python (aotv_kodi.get_setting) lê — os dois leem os mesmos arquivos.
 *
 * Decisões do Davi (§9) aplicadas aqui também (para o lado Kotlin):
 * no Vulgata, `analytics_enabled` e `automatic_updates` são sempre
 * "false" (presença/analytics e self-update DESLIGADOS).
 */
@Singleton
class KodiSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class SettingDefinition(
        val id: String,
        val type: String,      // bool, enum, text, number, action, lsep, ...
        val label: String,
        val default: String,
        val values: String,    // enum: "Kodi|FFmpeg Direct" (valores separados por |)
        val visible: String,   // "true"/"false" (default "true")
    )

    private val kodiDir: File
        get() = File(context.filesDir, "kodi")

    private val addonDataDir: File
        get() = File(kodiDir, "userdata/addon_data")

    /** Valores forçados por decisão do Davi (§9). */
    private val forcedValues = mapOf(
        "plugin.video.vulgata" to mapOf(
            "analytics_enabled" to "false",
            "automatic_updates" to "false",
        ),
    )

    private fun forcedValue(addonId: String, settingId: String): String? =
        forcedValues[addonId]?.get(settingId)

    /** Definições de settings.xml (ou vazio se o addon não tiver). */
    fun getDefinitions(addonId: String): List<SettingDefinition> {
        val xml = File(kodiDir, "addons/$addonId/resources/settings.xml")
        if (!xml.isFile) return emptyList()
        val defs = mutableListOf<SettingDefinition>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(xml.inputStream(), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "setting") {
                    val id = parser.getAttributeValue(null, "id").orEmpty()
                    if (id.isNotBlank()) {
                        defs += SettingDefinition(
                            id = id,
                            type = parser.getAttributeValue(null, "type").orEmpty().ifBlank { "text" },
                            label = parser.getAttributeValue(null, "label").orEmpty(),
                            default = parser.getAttributeValue(null, "default").orEmpty(),
                            values = parser.getAttributeValue(null, "values").orEmpty(),
                            visible = parser.getAttributeValue(null, "visible").orEmpty().ifBlank { "true" },
                        )
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // settings.xml inválido — devolve o que já leu.
        }
        return defs
    }

    /** Valores persistidos em addon_data/<id>/settings.xml. */
    private fun readUserSettings(addonId: String): Map<String, String> {
        val xml = File(addonDataDir, "$addonId/settings.xml")
        if (!xml.isFile) return emptyMap()
        val values = mutableMapOf<String, String>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(xml.inputStream(), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "setting") {
                    val id = parser.getAttributeValue(null, "id").orEmpty()
                    if (id.isNotBlank()) {
                        values[id] = parser.getAttributeValue(null, "value").orEmpty()
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // settings.xml do usuário inválido — ignora.
        }
        return values
    }

    /**
     * Valor de um setting (string). Ordem: forçado (Davi) > persistido >
     * default do settings.xml > "".
     */
    fun getSetting(addonId: String, settingId: String): String {
        forcedValue(addonId, settingId)?.let { return it }
        readUserSettings(addonId)[settingId]?.let { return it }
        return getDefinitions(addonId).firstOrNull { it.id == settingId }?.default.orEmpty()
    }

    /** Bool ("true"/"false"). Default do settings.xml se não houver valor. */
    fun getSettingBool(addonId: String, settingId: String): Boolean =
        getSetting(addonId, settingId).equals("true", ignoreCase = true)

    /** Int (enum é o índice). Default do settings.xml se não houver valor. */
    fun getSettingInt(addonId: String, settingId: String): Int =
        getSetting(addonId, settingId).toIntOrNull() ?: 0

    /** Persiste um setting em addon_data/<id>/settings.xml (formato Kodi). */
    fun setSetting(addonId: String, settingId: String, value: String): Boolean {
        if (addonId.isBlank() || settingId.isBlank()) return false
        val dir = File(addonDataDir, addonId).apply { mkdirs() }
        val file = File(dir, "settings.xml")
        val values = readUserSettings(addonId).toMutableMap()
        values[settingId] = value
        return try {
            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<settings>\n")
            for ((id, v) in values.entries.sortedBy { it.key }) {
                sb.append("    <setting id=\"${escapeXml(id)}\" value=\"${escapeXml(v)}\" />\n")
            }
            sb.append("</settings>\n")
            file.writeText(sb.toString(), Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
