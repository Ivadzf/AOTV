package com.aotv.app.ui.movies

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Os mesmos caminhos vetoriais dos controles do webOS. */
internal object WebPlayerIcons {
    private fun line(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    val Subtitles by lazy { line("Subtitles", "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3zM10.6 10.1a2.2 2.2 0 1 0 0 3.8M16.6 10.1a2.2 2.2 0 1 0 0 3.8") }
    val Screen by lazy { line("Screen", "M8 3H5a2 2 0 0 0-2 2v3M21 8V5a2 2 0 0 0-2-2h-3M3 16v3a2 2 0 0 0 2 2h3M16 21h3a2 2 0 0 0 2-2v-3") }
    val Speed by lazy { line("Speed", "M12 14l4-4M3.34 19a10 10 0 1 1 17.32 0") }
    val Audio by lazy { line("Audio", "M11 5L6 9H2v6h4l5 4V5zM15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14") }
    val Sources by lazy { line("Sources", "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9z") }
    val Pin by lazy { line("Pin", "M12 16v5M8 3h8M9.5 3v6l-3.5 4v3h12v-3l-3.5-4V3") }
    val Episodes by lazy { line("Episodes", "M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01") }
    val Previous by lazy { line("Previous", "M19 20L9 12l10-8v16zM5 19V5") }
    val Next by lazy { line("Next", "M5 4l10 8-10 8V4zM19 5v14") }
    val Skip by lazy { line("Skip", "M13 19l9-7-9-7v14zM2 19l9-7-9-7v14z") }
}
