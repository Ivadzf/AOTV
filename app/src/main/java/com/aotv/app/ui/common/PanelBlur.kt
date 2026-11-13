package com.aotv.app.ui.common

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Blur real atrás de folhas/painéis (Lote 3, v1.11.0; diagnóstico + fallback +
 * expansão no Lote 4, v1.12.0).
 *
 * COMO FUNCIONA: um RenderEffect desfoca o CONTEÚDO ONDE ELE É APLICADO — não
 * o que está atrás. Então o blur de fundo é aplicado ao conteúdo de baixo
 * (a página do detalhe) enquanto a folha está aberta, e a folha em si fica
 * nítida por cima, com o fundo levemente translúcido (efeito de vidro
 * fosco). O efeito é criado 1x com remember, como o do fundo Cinematográfico.
 * Raio 18f: se o aparelho fraco engasgar com a folha aberta, baixar o raio
 * (ou o toggle) é o primeiro ajuste.
 *
 * DIAGNÓSTICO — por que o blur do Lote 3 não aparecia no celular do Davi
 * (teste ~19:28 -03, v1.11.0; verificado no código, sem bug no caminho
 * aplicado — a cadeia toggle + efeito + folha aberta estava correta):
 * 1. ANDROID < 12 (API < 31): [rememberPanelBlurEffect] devolve null e
 *    `blurBehindSheets` (MovieDetailScreen:193) fica false — o toggle
 *    existia mas não fazia nada. Versão do Android do Davi desconhecida;
 *    se o aparelho for < 12, era ESTE o motivo. Coberto agora pelo
 *    fallback [FrostedScrim] (scrim fosco, mesma pref).
 * 2. SUPERFÍCIES SEM BLUR: o Davi testou onde o blur nunca foi aplicado —
 *    folha de fontes do player, cartões de Continuar assistindo e a
 *    pílula de navegação ("gota"). Coberto agora pela expansão abaixo.
 * 3. FILMES NÃO TÊM FOLHA: no detalhe de filme as fontes ficam num bloco
 *    inline (SourceRow), sem folha — o gatilho `sheetOpen` (selectedEpisode
 *    != null) só existe p/ séries, e a filmografia só abre pelo elenco.
 *    Não há o que desfocar ali sem reescrever a tela; fora do escopo.
 *
 * ONDE É APLICADO (toggle "Blur nos painéis", padrão ligado):
 * - Folha de fontes do episódio e folha de filmografia do ator, na página de
 *   detalhes ([com.aotv.app.ui.movies.MovieDetailScreen]): o conteúdo da
 *   página atrás da folha é desfocado com raio 18f e o card da folha fica com
 *   86% de opacidade, p/ o desfoque aparecer através dele. Abaixo do
 *   Android 12, o [FrostedScrim] escurece o conteúdo no lugar do desfoque.
 * - Folha "Mais opções" do player (ModalBottomSheet em TouchPlayerControls):
 *   blur da JANELA via `WindowManager.LayoutParams.blurBehindRadius` (API
 *   31+, melhor esforço — a OEM pode ignorar) + scrim mais escuro como
 *   fallback abaixo do Android 12.
 * - Vidro fosco (translúcido, sem RenderEffect — ver [frostedColor]): painel
 *   lateral do player (fontes/legendas/áudio/episódios), pílula de
 *   navegação ("gota"), mini player e cartões de Continuar assistindo.
 *
 * ONDE NÃO É APLICADO (e por quê):
 * - Painéis do player atrás do VÍDEO: o vídeo roda numa Surface separada
 *   do ExoPlayer e o RenderEffect do Compose não alcança Surface. O painel
 *   em si vira vidro fosco translúcido (o vídeo aparece através dele,
 *   escurecido); o vídeo NÃO é desfocado.
 * - Diálogos (AlertDialog / Dialog): janela separada, sem blur.
 * - Configurações: NADA de blur aqui por decisão do Davi (Lote 4) — nem o
 *   efeito nas telas, nem superfícies novas; só o toggle que já existe em
 *   Aparência, onde estava.
 * - Fundo Cinematográfico do detalhe: JÁ é blur real (28f) e não foi mexido;
 *   com a folha aberta ele desliga sozinho (como antes) p/ não pagar dois
 *   blurs ao mesmo tempo — o blur da folha assume o lugar dele.
 *
 * ABAIXO DO ANDROID 12 (API < 31): não há RenderEffect — o efeito é null e
 * o fallback é o scrim fosco ([FrostedScrim] no detalhe, scrim mais escuro
 * na folha do player) e o vidro translúcido nas superfícies flutuantes.
 * Tudo respeita o mesmo toggle.
 */

/** true no Android 12+ (API 31), onde existe RenderEffect. Abaixo disso, false. */
fun isPanelBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Efeito de blur reutilizável (API 31+); null abaixo do Android 12. Criado 1x. */
@Composable
fun rememberPanelBlurEffect(radius: Float = 18f): androidx.compose.ui.graphics.RenderEffect? {
    return remember(radius) {
        if (isPanelBlurSupported()) {
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else {
            null
        }
    }
}

/** Aplica o [effect] ao conteúdo quando [enabled]; sem efeito, não muda nada. */
fun Modifier.blurBehind(enabled: Boolean, effect: androidx.compose.ui.graphics.RenderEffect?): Modifier =
    if (enabled && effect != null) graphicsLayer { renderEffect = effect } else this

/**
 * Fallback abaixo do Android 12 (Lote 4): sem RenderEffect não há como
 * desfocar de verdade, então um scrim fosco (preto 45%) escurece o conteúdo
 * atrás da folha. Só aparece com o toggle ligado e sem efeito real — nunca
 * os dois juntos. Usar DENTRO do conteúdo que fica atrás da folha (irmão
 * da folha, nunca por cima dela).
 */
@Composable
fun FrostedScrim(enabled: Boolean, effect: androidx.compose.ui.graphics.RenderEffect?) {
    if (enabled && effect == null) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    }
}

/**
 * Vidro fosco SEM RenderEffect (Lote 4): fundo translúcido no lugar do
 * opaco. É o que cobre as superfícies flutuantes — pílula de navegação,
 * mini player, cartões de Continuar assistindo e painel lateral do player —
 * onde o blur real não alcança: o RenderEffect desfoca o PRÓPRIO conteúdo
 * (ícones inclusos), não o que está atrás, e o vídeo do player roda numa
 * Surface separada. Funciona em qualquer API e respeita o mesmo toggle.
 * Primeiro ajuste se engasgar num aparelho fraco: subir o alpha (menos
 * transparência) ou desligar o toggle.
 */
fun frostedColor(enabled: Boolean, solid: Color, frostedAlpha: Float = 0.78f): Color =
    if (enabled) solid.copy(alpha = frostedAlpha) else solid
