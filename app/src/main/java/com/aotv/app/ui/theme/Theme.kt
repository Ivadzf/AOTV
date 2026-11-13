package com.aotv.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.R

// Mesma fonte local do webOS. Cada peso usa o eixo wght do arquivo variável.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jakarta(w: FontWeight) = Font(
    R.font.plus_jakarta_sans, w,
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w.weight)),
)
val PlusJakartaSans = FontFamily(
    jakarta(FontWeight.Normal), jakarta(FontWeight.Medium), jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold), jakarta(FontWeight.ExtraBold),
)
val Inter = PlusJakartaSans
val JetBrainsSans = Inter

val DisplayFont = PlusJakartaSans

// Nomes mantidos para os componentes existentes.
val SoraFontFamily    = Inter
val ManropeFontFamily = Inter
val InterFontFamily   = Inter

private fun aotvColorScheme(palette: AotvPalette) =
    if (palette.isLight) lightColorScheme(
        primary            = palette.accent,
        onPrimary          = palette.onAccent,
        primaryContainer   = palette.accentSoft,
        onPrimaryContainer = palette.textPrimary,
        secondary          = palette.accentDark,
        onSecondary        = palette.onAccent,
        background         = palette.background,
        onBackground       = palette.textPrimary,
        surface            = palette.backgroundCard,
        onSurface          = palette.textPrimary,
        surfaceVariant     = palette.backgroundCard2,
        surfaceContainerHighest = palette.surface2,
        surfaceContainerHigh = palette.backgroundCard2,
        surfaceContainer   = palette.backgroundCard,
        onSurfaceVariant   = palette.textSecondary,
        error              = palette.danger,
        onError            = Color.White,
        outline            = palette.border,
        outlineVariant     = palette.borderSubtle,
    ) else darkColorScheme(
        primary            = palette.accent,
        onPrimary          = palette.onAccent,
        primaryContainer   = palette.accentSoft,
        onPrimaryContainer = palette.textPrimary,
        secondary          = palette.accentDark,
        onSecondary        = palette.onAccent,
        background         = palette.background,
        onBackground       = palette.textPrimary,
        surface            = palette.backgroundCard,
        onSurface          = palette.textPrimary,
        surfaceVariant     = palette.backgroundCard2,
        surfaceContainerHighest = palette.surface2,
        surfaceContainerHigh = palette.backgroundCard2,
        surfaceContainer   = palette.backgroundCard,
        onSurfaceVariant   = palette.textSecondary,
        error              = palette.danger,
        onError            = Color.White,
        outline            = palette.border,
        outlineVariant     = palette.borderSubtle,
    )

// Títulos em ExtraBold com letra mais junta, como no webOS.
private val AotvTypography = Typography(
    displayLarge   = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold,     fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-1.2).sp),
    displayMedium  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold,     fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    displaySmall   = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold,     fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp),
    headlineLarge  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp),
    headlineSmall  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.4).sp),
    titleLarge     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium    = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge      = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall      = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal,   fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium    = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
    labelSmall     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

// Raios do webOS. Etiqueta 6, pôster e cartão 14, resumo e diálogo 20.
private val AotvShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AotvTheme(content: @Composable () -> Unit) {
    // A paleta vem do holder (publicado pela MainActivity a partir do
    // PlatformPrefs): ler aqui assina o State e recompõe a árvore inteira
    // quando o tema troca. As cores dos vals de Color.kt leem o mesmo holder.
    val palette = currentPaletteState.value
    MaterialTheme(
        colorScheme = aotvColorScheme(palette),
        typography  = AotvTypography,
        shapes      = AotvShapes,
        content     = content,
    )
}
