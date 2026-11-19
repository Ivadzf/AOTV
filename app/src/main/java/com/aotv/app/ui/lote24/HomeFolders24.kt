package com.aotv.app.ui.lote24

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lote 24 (v1.34.0), item 3 — pastas na Home + setup de addons por código curto.
 *
 * PASTAS: diferente das Coleções (que juntam fontes de addons/TMDB/Trakt),
 * a pasta daqui é leve: nome + lista de chaves de fileiras da Home (as
 * mesmas chaves do layout, ex.: "continue", "trending_movies"). A pasta
 * agrupa fileiras que o usuário quer ver juntas; a ordem das pastas é a
 * ordem da lista. Serializa em JSON na pref [Lote24PrefKeys.HOME_FOLDERS].
 */
data class HomeFolder24(
    val id: String,
    val name: String,
    val rowKeys: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("rows", JSONArray(rowKeys))
    }

    companion object {
        const val MAX_FOLDERS = 20
        const val MAX_ROWS = 30

        /** Nova pasta com nome limpo; null quando o nome fica vazio. */
        fun new(name: String): HomeFolder24? {
            val clean = name.trim().take(60)
            if (clean.isBlank()) return null
            return HomeFolder24(id = java.util.UUID.randomUUID().toString(), name = clean)
        }

        /** Lê a lista do JSON guardado; qualquer erro volta a lista vazia. */
        fun listFromJson(raw: String?): List<HomeFolder24> = runCatching {
            val arr = JSONArray(raw ?: return emptyList())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "").ifBlank { return@mapNotNull null }
                val name = o.optString("name", "").trim().ifBlank { return@mapNotNull null }
                val rows = o.optJSONArray("rows")?.let { r ->
                    (0 until r.length()).mapNotNull { r.optString(it, "").takeIf { s -> s.isNotBlank() } }
                }.orEmpty()
                HomeFolder24(id = id, name = name, rowKeys = rows.distinct().take(MAX_ROWS))
            }.take(MAX_FOLDERS)
        }.getOrDefault(emptyList())

        /** Escreve a lista como JSON compacto. */
        fun listToJson(folders: List<HomeFolder24>): String {
            val arr = JSONArray()
            folders.take(MAX_FOLDERS).forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/**
 * Código curto p/ compartilhar o setup de addons (quais addons instalados e
 * ativos) com outro aparelho.
 *
 * FORMATO: o setup vira um JSON compacto `[{"u":"<url>","e":1}, ...]`,
 * os bytes vão p/ Base32 Crockford (alfabeto sem ambíguos: sem I, L, O, U)
 * e o último caractere é um checksum (soma dos bytes + tamanho, mod 32).
 * O código é exibido em grupos de 4 separados por "-". Ao colar, o decode
 * confere o charset e o checksum antes de devolver a lista — código
 * digitado errado é recusado com mensagem, nunca aplicado pela metade.
 * Aplicar o código instala/ativa os addons da lista pelos fluxos normais de
 * instalação (o mesmo caminho de colar a URL do manifest); nada é baixado
 * de fora além dos próprios addons.
 *
 * TAMANHO (honesto): 6 caracteres só cabem ~3 bytes — um setup real não
 * cabe. Cada addon custa ~60-120 caracteres de código (a URL do manifest
 * domina). Ex.: 1 addon com URL de 45 caracteres = JSON de ~60 bytes =
 * ~97 caracteres de código + checksum. O formato é o menor possível sem
 * servidor: sem encurtador e sem backend, não há como um código de 6
 * caracteres carregar URLs arbitrárias.
 */
object AddonShareCode {
    /** Alfabeto Base32 Crockford: sem I, L, O, U (nada ambíguo ao digitar). */
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** Um addon no setup: URL do manifest + se está ativo. */
    data class SharedAddon(val url: String, val enabled: Boolean = true)

    /** JSON compacto do setup. */
    fun toJson(addons: List<SharedAddon>): String {
        val arr = JSONArray()
        addons.forEach { a ->
            arr.put(JSONObject().apply { put("u", a.url); put("e", if (a.enabled) 1 else 0) })
        }
        return arr.toString()
    }

    /** JSON compacto de volta p/ a lista; erro = JSON inválido ou vazio. */
    fun fromJson(raw: String): Result<List<SharedAddon>> = runCatching {
        val arr = JSONArray(raw)
        require(arr.length() > 0) { "O setup está vazio" }
        require(arr.length() <= 200) { "Setup grande demais (máx. 200 addons)" }
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val url = o.getString("u")
            require(url.isNotBlank()) { "Addon ${i + 1} sem URL" }
            SharedAddon(url = url, enabled = o.optInt("e", 1) == 1)
        }
    }

    /** Gera o código a partir da lista de addons. */
    fun encode(addons: List<SharedAddon>): String {
        val bytes = toJson(addons).toByteArray(Charsets.UTF_8)
        val body = base32Encode(bytes)
        val checksum = ALPHABET[(bytes.sumOf { it.toInt() and 0xFF } + bytes.size) % 32]
        return groupCode(body + checksum)
    }

    /** Decodifica o código colado (com ou sem "-" e espaços); erro = checksum ou charset. */
    fun decode(code: String): Result<List<SharedAddon>> = runCatching {
        val clean = code.uppercase().replace("-", "").replace(" ", "").replace("\n", "")
        require(clean.length >= 2) { "Código curto demais" }
        require(clean.all { it in ALPHABET }) { "O código tem caracteres inválidos" }
        val body = clean.dropLast(1)
        val expected = ALPHABET[(base32Decode(body).sumOf { it.toInt() and 0xFF } + base32Decode(body).size) % 32]
        require(clean.last() == expected) { "Código inválido (checksum não confere) — confira os caracteres" }
        val json = base32Decode(body).toString(Charsets.UTF_8)
        fromJson(json).getOrThrow()
    }

    /** Agrupa o código em blocos de 4 p/ digitar/ler. */
    fun groupCode(code: String): String = code.chunked(4).joinToString("-")

    /** Tamanho esperado do código p/ N addons (estimativa, depende das URLs). */
    fun estimatedLength(addons: List<SharedAddon>): Int {
        val bytes = toJson(addons).toByteArray(Charsets.UTF_8).size
        return (bytes * 8 + 4) / 5 + 1 + (bytes * 8 / 5) / 4
    }

    private fun base32Encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        bytes.forEach { b ->
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                out.append(ALPHABET[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) out.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        return out.toString()
    }

    private fun base32Decode(text: String): ByteArray {
        val out = ArrayList<Byte>()
        var buffer = 0
        var bitsLeft = 0
        text.forEach { c ->
            val value = ALPHABET.indexOf(c)
            require(value >= 0) { "Caractere inválido no código" }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}

/**
 * Editor de pastas da Home (item 3). Sem ViewModel: recebe o
 * [HomeFolderSettings] e as fileiras disponíveis como pares (chave, título).
 * Criar, renomear, apagar, subir/descer e ligar/desligar fileiras da pasta.
 * A tela de Configurações (junção) instancia com o settings ligado ao
 * PlatformPrefs.
 */
@Composable
fun HomeFolderEditor(
    settings: HomeFolderSettings,
    availableRows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val folders by settings.foldersFlow.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Pastas juntam fileiras da Início com um nome só. A pasta aparece na Início com as fileiras dela.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(60) },
                singleLine = true,
                label = { Text("Nome da pasta") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                ),
            )
            TextButton(
                onClick = { settings.addFolder(newName); newName = "" },
                enabled = newName.trim().isNotBlank() && folders.size < HomeFolder24.MAX_FOLDERS,
            ) {
                Text("Criar", color = Accent, fontWeight = FontWeight.ExtraBold)
            }
        }
        folders.forEachIndexed { index, folder ->
            HomeFolderCard(
                folder = folder,
                canUp = index > 0,
                canDown = index < folders.size - 1,
                availableRows = availableRows,
                onMoveUp = { settings.moveFolder(folder.id, -1) },
                onMoveDown = { settings.moveFolder(folder.id, 1) },
                onDelete = { settings.deleteFolder(folder.id) },
                onRename = { renamingId = folder.id; renameText = folder.name },
                onToggleRow = { key, on ->
                    val rows = if (on) folder.rowKeys + key else folder.rowKeys - key
                    settings.setFolderRows(folder.id, rows)
                },
            )
        }
        if (folders.isEmpty()) {
            Text("Nenhuma pasta ainda.", color = TextTertiary, fontSize = 14.sp)
        }
        if (renamingId != null) {
            val folder = folders.firstOrNull { it.id == renamingId }
            if (folder != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it.take(60) },
                        singleLine = true,
                        label = { Text("Novo nome") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                        ),
                    )
                    TextButton(onClick = { settings.renameFolder(folder.id, renameText); renamingId = null }) {
                        Text("Salvar", color = Accent, fontWeight = FontWeight.ExtraBold)
                    }
                    TextButton(onClick = { renamingId = null }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFolderCard(
    folder: HomeFolder24,
    canUp: Boolean,
    canDown: Boolean,
    availableRows: List<Pair<String, String>>,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onToggleRow: (String, Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Folder, null, tint = Accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (folder.rowKeys.isEmpty()) "Sem fileiras" else "${folder.rowKeys.size} fileira(s)",
                    color = TextTertiary, fontSize = 12.sp,
                )
            }
            IconButton(onClick = onMoveUp, enabled = canUp, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowUpward, "Subir ${folder.name}", tint = if (canUp) TextSecondary else TextTertiary.copy(alpha = .35f), modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onMoveDown, enabled = canDown, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowDownward, "Descer ${folder.name}", tint = if (canDown) TextSecondary else TextTertiary.copy(alpha = .35f), modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Delete, "Apagar ${folder.name}", tint = TextSecondary, modifier = Modifier.size(19.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Renomear", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onRename).padding(vertical = 6.dp, horizontal = 8.dp),
            )
        }
        if (availableRows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableRows.forEach { row ->
                    val rowKey = row.first
                    val rowTitle = row.second
                    val on = rowKey in folder.rowKeys
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Accent.copy(alpha = 0.14f) else BackgroundCard)
                            .border(1.dp, if (on) Accent.copy(alpha = 0.4f) else Border, RoundedCornerShape(10.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Checkbox, onClick = { onToggleRow(rowKey, !on) })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(rowTitle, color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(if (on) "Na pasta" else "Adicionar", color = if (on) Accent else TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
