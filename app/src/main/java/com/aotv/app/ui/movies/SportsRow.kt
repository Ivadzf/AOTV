package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.live.LiveProgram
import com.aotv.app.data.local.HomeDisplayPrefs
import com.aotv.app.data.sports.SportsGame
import com.aotv.app.data.sports.visibleScore
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/** "HH:mm" no fuso do aparelho. */
internal fun upcomingHhmm(ms: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(ms))

/**
 * Seção de esportes do Ao vivo (Lote 8, v1.18.0).
 *
 * Fileira horizontal com os canais de esporte que estão no ar agora (ou com
 * programa de esporte passando), com o placar extraído do EPG quando existe
 * — ver [visibleScore]: sem placar no texto, sem placar na tela.
 *
 * PERTENCE À FRENTE 1: este composable vive aqui (a Frente 2 é dona da
 * lógica de esportes), mas quem o embute na aba Ao vivo é a Frente 1, em
 * LiveTvScreen.kt, depois das pílulas de filtro — com as linhas vindas de
 * `liveSportsNow(view.rows...)` e das prefs `sportsShow`/`sportsLiveScores`.
 * Sem linhas, não desenha nada.
 */
@Composable
fun SportsSection(
    rows: List<Pair<LiveChannel, LiveProgram?>>,
    now: Long,
    hideScores: Boolean,
    scoreDelaySec: Int,
    liveScores: Boolean,
    onPlay: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    Column {
        com.aotv.app.ui.common.SectionHeader(
            title = "Esportes ao vivo",
            underline = true,
            compact = true,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rows, key = { it.first.id }) { pair ->
                val channel = pair.first
                val program = pair.second
                val score = if (liveScores && program != null) {
                    visibleScore(program, now, hideScores, scoreDelaySec)
                } else {
                    null
                }
                Column(
                    Modifier.width(240.dp)
                        .clip(RoundedCornerShape(14.dp)).background(BackgroundCard2)
                        .clickable { onPlay(channel.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(Accent)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("AO VIVO", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(channel.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (program != null) {
                        Text(program.title, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (score != null) {
                            Text("Placar: $score", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        Text("Sem guia para este canal", color = TextTertiary, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Fileira "Próximos" da Início (Lote 8, v1.18.0): próximos episódios das
 * séries acompanhadas (mesmo conjunto do Calendário da Biblioteca — sem
 * `air_date` o episódio NÃO aparece) + próximos jogos de esporte do EPG.
 *
 * Spoilers: com `calendar_hide_spoilers` ligado e o episódio ainda sem ir
 * ao ar, o card esconde o "T2 E5" (mostra "Episódio em breve"); a data fica,
 * porque a fileira É o calendário. Jogos: o placar só aparece quando o jogo
 * já começou E o texto do EPG traz o placar (com o atraso anti-spoiler).
 * O botão "Gravar" do jogo abre o diálogo de agendar gravação.
 */
@Composable
fun UpcomingRow(
    episodes: List<CalendarEntry>,
    games: List<SportsGame>,
    now: Long,
    hideSpoilers: Boolean,
    hideScores: Boolean,
    scoreDelaySec: Int,
    sportsShow: Boolean,
    display: HomeDisplayPrefs = HomeDisplayPrefs(),
    onEpisodeClick: (CalendarEntry) -> Unit,
    onPlayGame: (SportsGame) -> Unit,
    onRecordGame: (SportsGame) -> Unit,
    /** "Ver tudo" (Lote 19, v1.29.0): abre a grade com todos os episódios e jogos. null = sem link. */
    onSeeMore: (() -> Unit)? = null,
) {
    if (episodes.isEmpty() && games.isEmpty()) return
    val today = todayMs()
    Column {
        com.aotv.app.ui.common.SectionHeader(
            title = "Próximos",
            action = onSeeMore?.let { seeMore -> { com.aotv.app.ui.common.SeeAllLink("Ver tudo", seeMore) } },
            underline = display.sectionUnderline,
            compact = display.compact,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(episodes, key = { "ep:${it.tmdbId}:${it.season}:${it.episode}" }) { entry ->
                val aired = entry.isAired(today)
                val hideLabel = hideSpoilers && !aired
                Row(
                    Modifier.width(300.dp).height(96.dp)
                        .clip(RoundedCornerShape(14.dp)).background(BackgroundCard)
                        .clickable { onEpisodeClick(entry) }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.posterUrl.isNotBlank()) {
                        AsyncImage(
                            entry.posterUrl, entry.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 56.dp, height = 80.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (hideLabel) "Episódio em breve" else entry.seLabel,
                            color = TextSecondary, fontSize = 13.sp, maxLines = 1,
                        )
                        Text(
                            entry.dateLabel(today), color = TextTertiary, fontSize = 12.sp, maxLines = 1,
                        )
                    }
                }
            }
            if (sportsShow) {
                items(games, key = { "game:${it.channelId}:${it.startMs}" }) { game ->
                    UpcomingGameCard(game, now, hideScores, scoreDelaySec, onPlayGame, onRecordGame)
                }
            }
        }
    }
}

/**
 * Cartão de jogo da fileira "Próximos" (Lote 19, v1.29.0): extraído p/ ser
 * o MESMO cartão na fileira e na grade "Ver tudo" — "Assistir" abre o ao
 * vivo e "Gravar" agenda a gravação, nos dois lugares. Na fileira o cartão
 * tem 300dp de largura; na grade ocupa a largura toda.
 */
@Composable
internal fun UpcomingGameCard(
    game: SportsGame,
    now: Long,
    hideScores: Boolean,
    scoreDelaySec: Int,
    onPlayGame: (SportsGame) -> Unit,
    onRecordGame: (SportsGame) -> Unit,
    modifier: Modifier = Modifier.width(300.dp).height(96.dp),
) {
    val live = game.isNow(now)
    val program = LiveProgram(game.startMs, game.stopMs, game.title, game.desc)
    val score = if (live) visibleScore(program, now, hideScores, scoreDelaySec) else null
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp)).background(BackgroundCard2)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("AO VIVO", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            } else {
                Text(upcomingHhmm(game.startMs), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(game.channelName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(game.title, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (score != null) {
                Text("Placar: $score", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f))
            }
            TextButton(onClick = { onRecordGame(game) }) {
                Text("Gravar", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { onPlayGame(game) }) {
                Text("Assistir", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
