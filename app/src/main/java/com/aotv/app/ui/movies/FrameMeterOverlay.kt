package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Overlay do medidor de quadros global (Lote 2 item 3, v1.47.0): pílula
 * pequena no canto superior com fps, p90 e % de quadros acima de 16 ms /
 * 33 ms. Os números são do vsync da UI (Choreographer) — não há como
 * separar UI/render/GPU com API pública, então o overlay não finge que
 * separa. Some sozinho antes de 10 amostras (o medidor publica null).
 */
@Composable
fun FrameMeterOverlay(meter: GlobalFrameMeter, modifier: Modifier = Modifier) {
    val stats by meter.stats.collectAsStateWithLifecycle()
    val s = stats ?: return
    Text(
        text = "%.0f fps · p90 %.0f ms · >16 ms %.0f%% · >33 ms %.0f%%".format(s.fps, s.p90Ms, s.pctOver16, s.pctOver33),
        color = Color.White,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
