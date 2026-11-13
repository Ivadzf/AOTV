package com.aotv.app.ui.common

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Blur real atrás de folhas/painéis (Lote 3, v1.11.0).
 *
 * COMO FUNCIONA: um RenderEffect desfoca o CONTEÚDO ONDE ELE É APLICADO — não
 * o que está atrás. Então o blur de fundo é aplicado ao conteúdo de baixo
 * (a página do detalhe) enquanto a folha está aberta, e a folha em si fica
 * nítida por cima, com o fundo levemente translúcido (efeito de vidro
 * fosco). O efeito é criado 1x com remember, como o do fundo Cinematográfico.
 *
 * ONDE É APLICADO (toggle "Blur nos painéis", padrão ligado):
 * - Folha de fontes do episódio e folha de filmografia do ator, na página de
 *   detalhes ([com.aotv.app.ui.movies.MovieDetailScreen]): o conteúdo da
 *   página atrás da folha é desfocado com raio 18f e o card da folha fica com
 *   86% de opacidade, p/ o desfoque aparecer através dele.
 *
 * ONDE NÃO É APLICADO (e por quê):
 * - Painéis do player (fontes, legendas, áudio, cor — PlayerActionPanel /
 *   ColorFilterPanel): atrás deles está o VÍDEO, que roda numa Surface
 *   separada do ExoPlayer. RenderEffect do Compose não alcança Surface, e
 *   desfocar a camada de controles desfocaria o próprio painel. Sem blur:
 *   fica o fundo opaco de sempre.
 * - Folha "Mais opções" do player (ModalBottomSheet em TouchPlayerControls):
 *   a folha vive numa janela de diálogo separada; o conteúdo do player está
 *   noutra janela e o RenderEffect não atravessa janelas. O blurBehind da
 *   janela (WindowManager.LayoutParams.blurBehindRadius) não é acessível pela
 *   API do ModalBottomSheet sem reescrever a folha — fora do escopo.
 * - Diálogos (AlertDialog / Dialog): mesma razão — janela separada.
 * - Fundo Cinematográfico do detalhe: JÁ é blur real (28f) e não foi mexido;
 *   com a folha aberta ele desliga sozinho (como antes) p/ não pagar dois
 *   blurs ao mesmo tempo — o blur da folha assume o lugar dele.
 *
 * ABAIXO DO ANDROID 12 (API < 31): não há RenderEffect — o efeito é null e
 * tudo fica como antes (scrim escuro, sem blur). O toggle continua existindo,
 * só não faz nada nesses aparelhos.
 */

/** Efeito de blur reutilizável (API 31+); null abaixo do Android 12. Criado 1x. */
@Composable
fun rememberPanelBlurEffect(radius: Float = 18f): androidx.compose.ui.graphics.RenderEffect? {
    return remember(radius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else {
            null
        }
    }
}

/** Aplica o [effect] ao conteúdo quando [enabled]; sem efeito, não muda nada. */
fun Modifier.blurBehind(enabled: Boolean, effect: androidx.compose.ui.graphics.RenderEffect?): Modifier =
    if (enabled && effect != null) graphicsLayer { renderEffect = effect } else this
