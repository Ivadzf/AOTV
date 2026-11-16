package com.aotv.app.ui.movies

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import com.aotv.app.data.local.SubLangs
import kotlinx.coroutines.delay
import org.json.JSONObject

// ── Legendas nível 2 (Lote 16, v1.26.0) ──────────────────────────────────────
//
// COMO PLUGAR (o coordenador integra no MoviePlayerScreen.kt):
//   // (a) Duas legendas: a 1ª continua no caminho atual (embutida via
//   // ExoPlayer ou externa via ExternalSubtitle); a 2ª é SEMPRE um arquivo
//   // externo baixado (SubtitleService/SubDlService) e desenhada por
//   // SecondarySubtitle (topo da tela) — ver o limite do ExoPlayer abaixo.
//   val second = SubTrackId(SubSourceKind.EXTERNAL, "eng", "OpenSubtitles", "Inglês")
//   var dual by remember { mutableStateOf(DualSubState(primary, second)) }
//   SecondarySubtitle(secondCues, exoPlayer, delayFor(dual, second),
//       subSize, subBg, isTv, controlsVisible, subColor, subFont, subBgOpacity)
//   // (b) Delay ao vivo: D-pad Esquerda/Direita = ±DELAY_STEP_FINE_MS
//   // (0,1 s); o menu continua com ±0,5 s (DELAY_STEP_COARSE_MS, igual ao
//   // atual). O delay é POR FAIXA (delayFor/withDelay) e persiste em
//   // SubDelayStore (por faixa, não por título — a mesma legenda externa
//   // costuma ter o mesmo desvio em todos os episódios).
//   dual = withDelay(dual, track, adjustDelayMs(delayFor(dual, track), +100L))
//   SubDelayStore.fromContext(context).setDelay(track, delayFor(dual, track))
//   // (c) Lembrar a faixa exata: SubTrackMemory.remember(titleKey, track)
//   // ao escolher; na abertura, resolve(titleKey, availableTracks).
//   // (d/e/f) Foreign audio only, forced e SDH: shouldShowSubs,
//   // pickForcedTrack, isSdhTrack/filterSdhTracks — ver cada KDoc.
//   // (g) SubDL: SubDlService (data/remote) — chave do usuário, nunca
//   // embutida; sem chave, desligado. (h) ZIP/UTF-16/BOM: JÁ EXISTE no
//   // SubtitleService.download (v1.13.0) — ver o GAP no fim deste arquivo.
//
// LIMITE HONESTO DO EXOPLAYER: o seletor de faixas só renderiza UMA faixa
// de texto por vez (C.TRACK_TYPE_TEXT). Duas legendas EMBUTIDAS ao mesmo
// tempo são impossíveis sem desmuxar o arquivo no app — [canRenderBoth]
// devolve false nesse caso. O que funciona: 1ª embutida (player) + 2ª
// externa (overlay), ou as duas externas (dois overlays). Se a 2ª língua
// só existir embutida, o coordenador precisa baixar o arquivo externo
// equivalente (OpenSubtitles/addons/SubDL) — não há atalho.

/** De onde vem a faixa: muxada no vídeo ou arquivo externo baixado. */
enum class SubSourceKind { EMBEDDED, EXTERNAL }

/**
 * Identidade EXATA de uma faixa de legenda: idioma + origem, não só o
 * idioma. É o que faz "lembrar a faixa exata por título" funcionar: duas
 * legendas em português (uma do vídeo, outra do OpenSubtitles) são faixas
 * diferentes e voltam como foram escolhidas. [lang] é o código do SubLangs
 * ("pob", "eng"...); [origin] é "No vídeo", o nome do addon ("OpenSubtitles"
 * quando não há addon) ou "SubDL"; [label] é o nome de exibição.
 */
data class SubTrackId(
    val kind: SubSourceKind,
    val lang: String,
    val origin: String,
    val label: String,
) {
    /** Chave estável p/ persistir delay e memória (sem "|" nos campos). */
    fun key(): String {
        fun clean(s: String): String = s.replace("|", "/").trim()
        return "${kind.name}|${clean(lang)}|${clean(origin)}"
    }
}

/** Chave de título p/ a memória de faixa ("MOVIE:27205" / "SERIES:1396"). */
fun subTrackTitleKey(type: String, tmdbId: Int): String = "${type.uppercase()}:$tmdbId"

/** Passo fino do delay ao vivo: D-pad Esquerda/Direita = ±0,1 s por toque. */
const val DELAY_STEP_FINE_MS = 100L

/** Passo do menu (o mesmo 0,5 s do ajuste atual do player). */
const val DELAY_STEP_COARSE_MS = 500L

/** Delay máximo por faixa, ±30 s (fora disso é outro arquivo/versão). */
const val DELAY_MAX_MS = 30_000L

/** As duas legendas + o delay de cada faixa (chave = [SubTrackId.key]). */
data class DualSubState(
    val primary: SubTrackId? = null,
    val secondary: SubTrackId? = null,
    val delaysMs: Map<String, Long> = emptyMap(),
)

/** Delay atual de uma faixa (0 quando nunca ajustado). */
fun delayFor(state: DualSubState, track: SubTrackId?): Long {
    if (track == null) return 0L
    return state.delaysMs[track.key()] ?: 0L
}

/** Novo estado com o delay desta faixa trocado (já dentro do limite). */
fun withDelay(state: DualSubState, track: SubTrackId, delayMs: Long): DualSubState {
    val clamped = delayMs.coerceIn(-DELAY_MAX_MS, DELAY_MAX_MS)
    val next = state.delaysMs.toMutableMap()
    next[track.key()] = clamped
    return state.copy(delaysMs = next)
}

/** Aplica um passo ao delay e prende no limite de ±30 s. */
fun adjustDelayMs(currentMs: Long, stepMs: Long): Long =
    (currentMs + stepMs).coerceIn(-DELAY_MAX_MS, DELAY_MAX_MS)

/**
 * Dá p/ desenhar as duas ao mesmo tempo? Só NÃO quando as duas são
 * embutidas (o ExoPlayer só renderiza uma faixa de texto — ver o topo).
 */
fun canRenderBoth(primary: SubTrackId?, secondary: SubTrackId?): Boolean {
    if (primary == null || secondary == null) return true
    if (primary.key() == secondary.key()) return false
    return !(primary.kind == SubSourceKind.EMBEDDED && secondary.kind == SubSourceKind.EMBEDDED)
}

// ── Delay por faixa, persistido ─────────────────────────────────────────────

/**
 * Delay de cada faixa, guardado em SharedPreferences ("aotv_prefs", chave
 * [STORE_KEY]): trackKey -> ms. Por FAIXA, não por título — o desvio de
 * uma legenda externa costuma se repetir nos episódios da série. No
 * máximo [MAX_TRACKS] faixas (as mais recentes).
 */
class SubDelayStore(private val prefs: SharedPreferences) {

    companion object {
        const val STORE_KEY = "sub_delay_v1"
        const val MAX_TRACKS = 60

        fun fromContext(context: Context): SubDelayStore =
            SubDelayStore(
                context.applicationContext.getSharedPreferences("aotv_prefs", Context.MODE_PRIVATE),
            )
    }

    fun delayFor(track: SubTrackId): Long {
        val raw = prefs.getString(STORE_KEY, null) ?: return 0L
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return 0L
        return obj.optLong(track.key(), 0L).coerceIn(-DELAY_MAX_MS, DELAY_MAX_MS)
    }

    fun setDelay(track: SubTrackId, delayMs: Long) {
        val raw = prefs.getString(STORE_KEY, null)
        val obj = runCatching { JSONObject(raw ?: "{}") }.getOrNull() ?: JSONObject()
        obj.put(track.key(), delayMs.coerceIn(-DELAY_MAX_MS, DELAY_MAX_MS))
        // Sem "quando foi usado" por faixa, a poda acima do limite é por
        // quantidade (o JSONObject não guarda ordem de inserção): quando o
        // mapa passa de 2x o limite, as entradas excedentes saem sem
        // critério de idade — o mapa volta a crescer por uso normal.
        if (obj.length() > MAX_TRACKS * 2) {
            val keys = obj.keys()
            val all = mutableListOf<String>()
            while (keys.hasNext()) all += keys.next()
            val drop = all.drop(MAX_TRACKS)
            for (k in drop) obj.remove(k)
        }
        prefs.edit().putString(STORE_KEY, obj.toString()).apply()
    }
}

// ── Lembrar a faixa exata por título ────────────────────────────────────────

/**
 * Última faixa escolhida em cada título, guardada em SharedPreferences
 * ("aotv_prefs", chave [STORE_KEY]): titleKey -> faixa exata (idioma +
 * origem). NUNCA guarda URL: links de legenda expiram/mudam — na volta,
 * [resolve] casa a faixa lembrada com as faixas disponíveis no momento
 * (1º pela chave exata, depois por idioma + origem). No máximo
 * [MAX_TITLES] títulos (os mais recentes).
 */
class SubTrackMemory(private val prefs: SharedPreferences) {

    companion object {
        const val STORE_KEY = "sub_track_v1"
        const val MAX_TITLES = 60

        fun fromContext(context: Context): SubTrackMemory =
            SubTrackMemory(
                context.applicationContext.getSharedPreferences("aotv_prefs", Context.MODE_PRIVATE),
            )
    }

    private fun read(): MutableMap<String, Pair<SubTrackId, Long>> {
        val out = LinkedHashMap<String, Pair<SubTrackId, Long>>()
        val raw = prefs.getString(STORE_KEY, null) ?: return out
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return out
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optJSONObject(k) ?: continue
            val kind = runCatching { SubSourceKind.valueOf(v.optString("k", "")) }.getOrNull() ?: continue
            val lang = v.optString("l", "")
            if (lang.isBlank()) continue
            out[k] = SubTrackId(kind, lang, v.optString("o", ""), v.optString("n", "")) to v.optLong("t", 0L)
        }
        return out
    }

    private fun write(map: Map<String, Pair<SubTrackId, Long>>) {
        val obj = JSONObject()
        val entries = map.entries.sortedByDescending { it.value.second }.take(MAX_TITLES)
        for ((k, pair) in entries) {
            val track = pair.first
            obj.put(
                k,
                JSONObject().put("k", track.kind.name).put("l", track.lang)
                    .put("o", track.origin).put("n", track.label).put("t", pair.second),
            )
        }
        prefs.edit().putString(STORE_KEY, obj.toString()).apply()
    }

    /** Guarda a faixa escolhida neste título. */
    fun remember(titleKey: String, track: SubTrackId) {
        if (titleKey.isBlank()) return
        val map = read()
        map[titleKey] = track to System.currentTimeMillis()
        write(map)
    }

    /** Faixa lembrada deste título, ou null quando nunca escolheu. */
    fun recall(titleKey: String): SubTrackId? = read()[titleKey]?.first

    /**
     * Casa a faixa lembrada com as disponíveis agora: 1º a chave exata
     * (idioma + origem), depois o mesmo idioma e origem, depois o mesmo
     * idioma e tipo. Null quando nada casa — aí vale a escolha automática
     * normal (SubLangs.choose).
     */
    fun resolve(titleKey: String, available: List<SubTrackId>): SubTrackId? {
        val wanted = recall(titleKey) ?: return null
        for (t in available) if (t.key() == wanted.key()) return t
        for (t in available) {
            if (t.kind == wanted.kind && t.lang == wanted.lang && t.origin == wanted.origin) return t
        }
        for (t in available) {
            if (t.kind == wanted.kind && t.lang == wanted.lang) return t
        }
        return null
    }
}

// ── Foreign audio only / forced / SDH ────────────────────────────────────────

/**
 * O áudio é de língua estrangeira p/ quem assiste? Compara a FAMÍLIA do
 * idioma do áudio com a do idioma de legenda preferido (pt-BR e pt-PT são
 * a mesma família — não é "estrangeiro"). Idioma do áudio desconhecido
 * (null/vazio/sem código) = false: sem dado, não se adivinha.
 */
fun isForeignAudio(audioLang: String?, prefLang: String): Boolean {
    val audio = SubLangs.of(audioLang) ?: return false
    val pref = SubLangs.of(prefLang) ?: return false
    return audio.family != pref.family
}

/**
 * "Foreign audio only" (d): com o modo ligado, a legenda só aparece quando
 * o áudio é estrangeiro — mesmo com a legenda "ligada" no player. Com o
 * modo desligado, vale o [subsOn] do usuário. Legenda desligada continua
 * desligada nos dois casos.
 */
fun shouldShowSubs(foreignOnly: Boolean, audioLang: String?, prefLang: String, subsOn: Boolean): Boolean {
    if (!subsOn) return false
    if (!foreignOnly) return true
    return isForeignAudio(audioLang, prefLang)
}

/** A faixa é forced (SELECTION_FLAG_FORCED do mux — falas em língua estrangeira). */
fun isForcedTrack(format: Format): Boolean =
    (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0

/**
 * A faixa é SDH/CC (legenda p/ surdos, com [sons] e [música])? Sinal
 * confiável: role flag DESCRIBES_MUSIC_AND_SOUND ou CAPTION do mux. Sinal
 * fraco (fallback): o rótulo traz "SDH" ou "CC" — alguns muxes só marcam
 * no nome. Sem nenhum dos dois, não é SDH.
 */
fun isSdhTrack(format: Format): Boolean {
    val flags = format.roleFlags
    if ((flags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) return true
    if ((flags and C.ROLE_FLAG_CAPTION) != 0) return true
    val label = format.label?.lowercase().orEmpty()
    if ("sdh" in label) return true
    if ("[cc]" in label || "(cc)" in label) return true
    if (Regex("""\bcc\b""").containsMatchIn(label)) return true
    return false
}

/** Tira as faixas SDH da lista quando o usuário pedir (hideSdh = true). */
fun filterSdhTracks(tracks: List<Format>, hideSdh: Boolean): List<Format> {
    if (!hideSdh) return tracks
    val out = ArrayList<Format>(tracks.size)
    for (t in tracks) if (!isSdhTrack(t)) out += t
    return out
}

/**
 * Forced subs (e): qual faixa forced mostrar mesmo com as legendas
 * desligadas, quando o áudio é estrangeiro. Ordem: forced no idioma de
 * legenda preferido, forced no idioma do áudio, qualquer forced. Null
 * quando não há faixa forced — aí não há o que mostrar (não se inventa
 * legenda). A faixa forced costuma estar no idioma do áudio (traduz só
 * as falas em outra língua dentro do filme); o preferido do usuário vem
 * primeiro porque é o que ele lê.
 */
fun pickForcedTrack(tracks: List<Format>, prefLang: String, audioLang: String?): Format? {
    var anyForced: Format? = null
    for (t in tracks) {
        if (!isForcedTrack(t)) continue
        if (anyForced == null) anyForced = t
        if (SubLangs.match(t.language, prefLang) == 2) return t
    }
    if (audioLang != null) {
        for (t in tracks) {
            if (!isForcedTrack(t)) continue
            if (SubLangs.match(t.language, audioLang) == 2) return t
        }
    }
    return anyForced
}

// ── 2ª legenda desenhada pelo app (topo da tela) ────────────────────────────

/**
 * Segunda legenda simultânea (a), desenhada pelo app no TOPO da tela —
 * espelho da ExternalSubtitle (PlayerOverlays.kt), que desenha embaixo.
 * [cues] são as falas do arquivo externo da 2ª língua (já baixado via
 * SubtitleService.download / episodeVm.subtitleCues) e [delayMs] é o
 * atraso PRÓPRIO dessa faixa (ver [delayFor]). Mesmo estilo da legenda
 * principal (cor, fonte, fundo) p/ não criar um segundo visual. Sem
 * falas no instante, não desenha nada.
 */
@Composable
internal fun BoxScope.SecondarySubtitle(
    cues: List<SubCue>,
    player: androidx.media3.common.Player,
    delayMs: Long,
    sizeScale: Float,
    withBg: Boolean,
    isTv: Boolean,
    raised: Boolean,
    textColorArgb: Int = 0xFFFFFFFF.toInt(),
    font: String = "default",
    bgOpacity: Float = 0.78f,
) {
    var subPos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(cues) { while (true) { subPos = player.currentPosition; delay(100) } }
    val line = cueAt(cues, subPos, delayMs)
    if (line.isEmpty()) return
    Text(
        line, color = subtitleTextColor(textColorArgb), textAlign = TextAlign.Center,
        fontSize = (if (isTv) 24 else 18).sp * sizeScale, lineHeight = (if (isTv) 30 else 23).sp * sizeScale,
        fontWeight = FontWeight.SemiBold,
        fontFamily = subFontFamily(font),
        style = androidx.compose.ui.text.TextStyle(
            shadow = if (withBg) null else androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 8f),
        ),
        modifier = Modifier.align(Alignment.TopCenter)
            .padding(start = 24.dp, end = 24.dp, top = if (raised) 110.dp else 32.dp)
            .then(if (withBg) Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = bgOpacity)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ── GAP / JÁ EXISTE — legendas ZIP/UTF-16/BOM (h) ───────────────────────────
// O subitem (h) NÃO precisou de código novo: o SubtitleService.download já
// trata tudo desde a v1.13.0 (JOTV 1.0.19 §3.6) — verificado no arquivo em
// 2026-11-16: magic bytes PK\x03\x04 → extrai o 1º .srt/.vtt/.ass do ZIP;
// BOM UTF-16 LE (FF FE) / BE (FE FF) → decodifica com o charset certo;
// BOM UTF-8 (EF BB BF) → removido; fallback UTF-8 estrito → windows-1252
// → ISO-8859-1. O que FALTA (gap real, fora do meu escopo — o parser mora
// em PlayerExtras.kt, que eu não posso tocar): arquivos .ASS — o
// unzipFirstSubtitle até extrai o .ass quando não há .srt/.vtt, mas o
// parseSubs só lê SRT/VTT, então um .ass vira 0 falas em silêncio. Até o
// coordenador resolver, o .ass deveria ser despriorizado na escolha.
