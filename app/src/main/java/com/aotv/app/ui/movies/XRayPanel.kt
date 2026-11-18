package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aotv.app.domain.model.CastMember
import com.aotv.app.ui.common.Eyebrow
import com.aotv.app.ui.common.SidePanel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.tv.TvScaled
import kotlin.math.abs

/** Marcador de capítulo/trecho no X-Ray — toque pula p/ o ponto. */
data class XRayMarker(val label: String, val startMs: Long, val endMs: Long)

/**
 * Confiança do corte: duração do arquivo vs. runtime do TMDB. Até 2 min de
 * diferença é o mesmo corte (arredondamento/credits); até 10 min pode ser
 * outro corte (diretor/estendido); acima disso, destoa.
 */
internal fun cutConfidenceLabel(fileMin: Int, tmdbMin: Int): String = when {
    fileMin <= 0 || tmdbMin <= 0 -> "Sem dados"
    abs(fileMin - tmdbMin) <= 2 -> "Alta — mesmo corte"
    abs(fileMin - tmdbMin) <= 10 -> "Média — pode ser outro corte"
    else -> "Baixa — duração destoa do TMDB"
}

/**
 * Curva de intensidade: nº de falas da legenda por décimo da duração
 * (10 baldes). Sem spoiler: só a quantidade de falas, nunca o texto.
 * Balde i cobre [i*duração/10, (i+1)*duração/10).
 */
internal fun intensityBuckets(cues: List<SubCue>, durationMs: Long): List<Int> {
    val buckets = MutableList(10) { 0 }
    if (durationMs <= 0L) return buckets
    for (cue in cues) {
        val idx = ((cue.start * 10L) / durationMs).toInt().coerceIn(0, 9)
        buckets[idx]++
    }
    return buckets
}

/**
 * X-Ray no player (Lote 21, v1.31.0): qual corte é este, elenco (foto do
 * TMDB — a mesma do detalhe — + personagem), "a história por trás"
 * (Wikipédia), curva de intensidade a partir das falas da legenda ativa
 * (toque pula p/ o trecho) e marcadores (IntroDB/SkipDB — abertura,
 * recapitulação, encerramento, pós-créditos). Capítulos do arquivo NÃO
 * aparecem: a Media3 1.6.0 não expõe capítulos MKV (limitação conhecida) —
 * os marcadores de pular trechos fazem esse papel.
 * Sem dado, a seção some ou diz que não há — nunca inventa.
 */
@Composable
internal fun XRayPanel(
    title: String,
    loading: Boolean,
    cast: List<CastMember>,
    director: String?,
    summary: String?,
    releaseLabel: String,
    fileDurationMs: Long,
    tmdbRuntimeMin: Int,
    buckets: List<Int>,
    hasSubtitles: Boolean,
    markers: List<XRayMarker>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean = false,
) {
    if (television) TvScaled { XRayPanelBody(title, loading, cast, director, summary, releaseLabel, fileDurationMs, tmdbRuntimeMin, buckets, hasSubtitles, markers, positionMs, onSeek, onDismiss, true) }
    else XRayPanelBody(title, loading, cast, director, summary, releaseLabel, fileDurationMs, tmdbRuntimeMin, buckets, hasSubtitles, markers, positionMs, onSeek, onDismiss, false)
}

@Composable
private fun XRayPanelBody(
    title: String,
    loading: Boolean,
    cast: List<CastMember>,
    director: String?,
    summary: String?,
    releaseLabel: String,
    fileDurationMs: Long,
    tmdbRuntimeMin: Int,
    buckets: List<Int>,
    hasSubtitles: Boolean,
    markers: List<XRayMarker>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean,
) {
    SidePanel(
        title = "X-Ray",
        onDismiss = onDismiss,
        width = if (television) 800.dp else 440.dp,
        titleSize = 26.sp,
        dismissLabel = "Fechar",
        sub = title,
    ) {
        LazyColumn(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp))
                    }
                }
            }
            // ── Qual corte é este? ───────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("Qual corte é este?")
                    XRayRow("Fonte", releaseLabel)
                    XRayRow("Duração do arquivo", if (fileDurationMs > 0) "${fileDurationMs / 60_000} min" else "desconhecida")
                    XRayRow("Duração no TMDB", if (tmdbRuntimeMin > 0) "$tmdbRuntimeMin min" else "desconhecida")
                    XRayRow("Confiança", cutConfidenceLabel((fileDurationMs / 60_000).toInt(), tmdbRuntimeMin))
                    if (tmdbRuntimeMin > 0 && fileDurationMs > 0) {
                        val diff = (fileDurationMs / 60_000).toInt() - tmdbRuntimeMin
                        if (diff != 0) {
                            Text(
                                if (diff > 0) "O arquivo tem $diff min a mais que o TMDB — pode ser corte estendido ou com extras."
                                else "O arquivo tem ${-diff} min a menos que o TMDB — pode ser corte editado.",
                                color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                            )
                        }
                    }
                }
            }
            // ── Elenco ───────────────────────────────────────────────────
            if (director != null || cast.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Eyebrow("Elenco")
                        if (director != null) Text("Direção: $director", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (cast.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(cast, key = { _, c -> c.id }) { _, member ->
                                    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (member.photoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = member.photoUrl,
                                                contentDescription = member.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(64.dp).clip(CircleShape),
                                            )
                                        } else {
                                            Box(
                                                Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(member.name.firstOrNull()?.toString() ?: "?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(member.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (member.character.isNotBlank()) {
                                            Text(member.character, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // ── A história por trás ──────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("A história por trás")
                    if (!summary.isNullOrBlank()) {
                        Text(summary, color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 12, overflow = TextOverflow.Ellipsis)
                        Text("Fonte: Wikipédia", color = TextSecondary, fontSize = 11.sp)
                    } else if (!loading) {
                        Text("Sem resumo na Wikipédia p/ este título.", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
            // ── Formato do filme ─────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow("Formato do filme")
                    if (!hasSubtitles) {
                        Text("Ative uma legenda p/ ver a curva de intensidade (quantidade de falas por trecho).", color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
                    } else {
                        val max = buckets.maxOrNull()?.coerceAtLeast(1) ?: 1
                        val currentBucket = if (fileDurationMs > 0) ((positionMs * 10L) / fileDurationMs).toInt().coerceIn(0, 9) else -1
                        Row(
                            Modifier.fillMaxWidth().height(64.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            buckets.forEachIndexed { i, count ->
                                val h = (count * 56 / max).coerceAtLeast(if (count > 0) 4 else 2)
                                Box(
                                    Modifier.weight(1f).height(h.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (i == currentBucket) Accent else Color.White.copy(alpha = 0.35f))
                                        .clickable { if (fileDurationMs > 0) onSeek(i * fileDurationMs / 10) },
                                )
                            }
                        }
                        Text("Toque numa barra p/ pular p/ o trecho — sem spoiler: só a quantidade de falas.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
            // ── Marcadores ───────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("Marcadores")
                    if (markers.isEmpty()) {
                        Text("Sem marcadores p/ este título (abertura, recapitulação, encerramento ou pós-créditos).", color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
                    } else {
                        markers.forEach { marker ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSeek(marker.startMs) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(marker.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatTime(marker.startMs)} – ${formatTime(marker.endMs)}",
                                    color = TextSecondary, fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun XRayRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 15.sp)
        Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
