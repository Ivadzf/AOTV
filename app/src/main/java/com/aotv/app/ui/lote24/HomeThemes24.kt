package com.aotv.app.ui.lote24

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.AotvPalette
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

/**
 * Lote 24 (v1.34.0), item 1 — temas da tela inicial + AMOLED puro.
 *
 * Os 4 temas + AMOLED são paletas [AotvPalette] (ver Palette.kt): o tema
 * Normal É a paleta Padrão (vermelho escuro + turquesa, id "default") e
 * continua o padrão. O tema vale p/ a tela inicial e o app todo porque o
 * [com.aotv.app.ui.theme.AotvTheme] usa a paleta ativa p/ o MaterialTheme e
 * os vals de Color.kt leem a mesma paleta. A MainActivity já publica a
 * paleta pelo id salvo (`AotvPalette.fromId`), então nenhum outro arquivo
 * precisa mudar p/ o tema trocar: basta gravar o id.
 */
object Lote24Themes {
    /** Id do tema Normal (= Padrão, a identidade do AOTV). */
    const val DEFAULT_ID = "default"
    const val NETFLIXY_ID = "netflixy"
    const val CYBERPUNK_ID = "cyberpunk"
    const val PRIMEFLY_ID = "primefly"
    const val AMOLED_ID = "amoled"

    /** Os 5 temas do Lote 24, na ordem do seletor. */
    val all: List<AotvPalette> = listOf(
        AotvPalette.Default,
        AotvPalette.Netflixy,
        AotvPalette.Cyberpunk,
        AotvPalette.Primefly,
        AotvPalette.Amoled,
    )

    /** Descrição curta de cada tema p/ o seletor. */
    fun description(id: String): String = when (id) {
        DEFAULT_ID -> "Normal — a identidade do AOTV: vermelho escuro + turquesa"
        NETFLIXY_ID -> "Vermelho vivo sobre fundo bem escuro"
        CYBERPUNK_ID -> "Amarelo neon com magenta"
        PRIMEFLY_ID -> "Azul Prime sobre azul-marinho escuro"
        AMOLED_ID -> "Preto puro — fundo #000000, economia de bateria em tela OLED"
        else -> ""
    }

    /** Paleta pelo id; id desconhecido volta ao Padrão. */
    fun fromId(id: String): AotvPalette = all.firstOrNull { it.id == id } ?: AotvPalette.Default

    /** Ids válidos p/ o seletor. */
    fun isValid(id: String): Boolean = all.any { it.id == id }
}

/**
 * Aplica o preto puro por cima de qualquer tema: fundo #000000 e cartões
 * quase pretos. Usado quando a pref [Lote24PrefKeys.HOME_AMOLED] está ligada
 * com um tema que não é o AMOLED. Com o tema AMOLED, não muda nada.
 */
fun AotvPalette.withAmoled(): AotvPalette = copy(
    background = Color(0xFF000000),
    backgroundElevated = Color(0xFF0A0A0A),
    backgroundCard = Color(0xFF0D0D0D),
    backgroundCard2 = Color(0xFF111111),
    surface2 = Color(0xFF161616),
    border = Color(0xFF1F1F1F),
    glassBg = Color(0xB8000000),
    panelBackground = Color(0xF7000000),
    focusBackground = Color(0xFF1A1A1A),
)

/**
 * Resolve a paleta efetiva: tema escolhido + AMOLED por cima quando ligado.
 * O coordenador usa esta função na junção p/ publicar a paleta.
 */
fun resolveLote24Palette(themeId: String, amoled: Boolean): AotvPalette {
    val base = Lote24Themes.fromId(themeId)
    if (!amoled) return base
    if (base.id == Lote24Themes.AMOLED_ID) return base
    return base.withAmoled()
}

/**
 * Seletor de tema reutilizável (item 1). Sem ViewModel: recebe o id
 * selecionado e o callback — a tela de Configurações (junção) liga aos
 * flows de [HomeThemeSettings]. Mostra os 5 temas do Lote 24; os temas
 * antigos (Branco, Carmesim...) continuam no seletor existente
 * ([com.aotv.app.ui.theme.ThemeSelector]).
 */
@Composable
fun HomeThemePicker(
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Lote24Themes.all.forEach { palette ->
            val selected = palette.id == selectedId
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(BackgroundCard)
                    .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Border, RoundedCornerShape(14.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = { onSelect(palette.id) })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(palette.accent))
                    Box(Modifier.size(22.dp).clip(CircleShape).background(palette.accentSecondary))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(palette.background)
                            .border(1.dp, Border, CircleShape),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(palette.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(Lote24Themes.description(palette.id), color = TextSecondary, fontSize = 12.sp)
                }
                if (selected) {
                    Icon(Icons.Default.Check, "Tema atual", tint = Accent, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
