package com.aotv.app.ui.settings

import androidx.compose.material.icons.filled.TouchApp
import com.aotv.app.ui.common.topInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.os.Message
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aotv.app.data.remote.AddonLinks
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Página /configure do addon em tela cheia, capturando o link sozinha. Pega o link quando a
 * página navega para stremio:// ou manifest.json (inclusive por window.open), quando copia
 * para a área de transferência (pelo script injetado e pelo ClipboardManager do sistema) e
 * varrendo a página a cada 1 s. Todo texto passa por [AddonLinks.capturedLink] antes de valer.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AddonSiteConfig(
    name: String,
    configureUrl: String,
    /** Link puro do addon, para "Instalar sem configurar". null quando a configuração é obrigatória. */
    plainUrl: String?,
    busy: Boolean,
    onInstall: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // found = link achado. fromAction = veio de cópia ou navegação (vale mais que a varredura).
    var found by remember { mutableStateOf<String?>(null) }
    var fromAction by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Carregamento da página de configuração: indicador enquanto carrega e mensagem clara
    // quando a página não existe (addon sem /configure, que dá 404) ou a rede falha.
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    /** Mensagem de erro da página (code null = falha de rede). */
    fun pageError(code: Int?): String = when (code) {
        404 -> "Este addon não tem página de configuração. " +
            if (plainUrl != null) "Você pode instalá-lo direto, sem configurar."
            else "Não há nada para configurar nele."
        null -> "Não foi possível carregar a página de configuração. Verifique sua conexão" +
            if (plainUrl != null) " ou instale direto, sem configurar." else " e tente de novo."
        else -> "A página de configuração devolveu erro $code. " +
            if (plainUrl != null) "Você pode instalar direto, sem configurar." else "Tente de novo mais tarde."
    }

    fun offer(text: String?, action: Boolean): Boolean {
        val link = AddonLinks.capturedLink(text) ?: return false
        if (action || !fromAction) {
            found = link
            if (action) fromAction = true
        }
        return true
    }

    // TV. O controle pode ficar preso dentro da página, então Voltar primeiro tira o foco dela
    // e leva para o painel do link. Fora da página, Voltar volta a página ou fecha.
    val tv = com.aotv.app.ui.common.LocalIsTv.current
    var webFocused by remember { mutableStateOf(false) }
    val panelFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    BackHandler {
        val w = webView
        when {
            tv && webFocused -> { w?.clearFocus(); runCatching { panelFocus.requestFocus() } }
            w != null && w.canGoBack() -> w.goBack()
            else -> onClose()
        }
    }
    if (tv) LaunchedEffect(Unit) { delay(300); runCatching { panelFocus.requestFocus() } }

    // Área de transferência do sistema enquanto a tela está aberta.
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val t = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
            offer(t, true)
        }
        cm.addPrimaryClipChangedListener(l)
        onDispose { cm.removePrimaryClipChangedListener(l) }
    }

    // Varredura de 1 em 1 s. Também reaplica o embrulho da área de transferência (página SPA).
    LaunchedEffect(webView) {
        val w = webView ?: return@LaunchedEffect
        while (true) {
            delay(1000)
            w.evaluateJavascript(HOOK_JS, null)
            w.evaluateJavascript(SCAN_JS) { r ->
                // evaluateJavascript devolve string JSON ("..." com escapes).
                val text = r?.trim('"')?.replace("\\n", "\n")?.replace("\\/", "/")?.replace("\\u0026", "&")
                text?.split('\n')?.forEach { offer(it, false) }
            }
        }
    }

    val header: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, "Fechar", tint = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Configurar $name", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    fontFamily = SoraFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Configure na página. O link do addon é pego sozinho.", color = TextTertiary, fontSize = 11.5.sp,
                    fontFamily = ManropeFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    val page: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            if (loading) LinearProgressIndicator(
                color = Accent, trackColor = BackgroundCard2,
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            AndroidView(
            // clipToBounds evita a WebView pintar por cima do cabeçalho.
            modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setOnFocusChangeListener { _, has -> webFocused = has }
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    val main = this
                    // Único método exposto. Recebe texto e só aceita link de addon validado.
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun send(text: String?) { main.post { offer(text, true) } }
                    }, "AotvBridge")

                    /** true quando a URL foi tratada aqui e a WebView não deve abrir. */
                    fun handle(url: String): Boolean {
                        if (offer(url, true)) return true
                        return !url.startsWith("http://", true) && !url.startsWith("https://", true)
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                            handle(request.url.toString())

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            // Redirecionamento para o manifest (ex.: /configure que devolve o
                            // manifest.json): intercepta e oferece a instalação, sem abrir a página.
                            if (url != null && offer(url, true)) {
                                view.stopLoading()
                                loading = false
                                return
                            }
                            loading = true
                            loadError = null
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            view.evaluateJavascript(HOOK_JS, null)
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                            if (!request.isForMainFrame) return
                            loading = false
                            loadError = pageError(null)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                            loading = false
                            loadError = pageError(null)
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                            if (!request.isForMainFrame) return
                            loading = false
                            loadError = pageError(errorResponse.statusCode)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        // window.open e target=_blank. Uma WebView de passagem lê a URL e decide.
                        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                            val popup = WebView(view.context)
                            popup.settings.allowFileAccess = false
                            popup.webViewClient = object : WebViewClient() {
                                var done = false
                                fun route(u: String) {
                                    if (done) return
                                    done = true
                                    if (!handle(u)) main.loadUrl(u)
                                    popup.post { popup.destroy() }
                                }
                                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                    route(r.url.toString()); return true
                                }
                                override fun onPageStarted(v: WebView, u: String?, favicon: android.graphics.Bitmap?) {
                                    if (u != null && u != "about:blank") { v.stopLoading(); route(u) }
                                }
                            }
                            (resultMsg.obj as WebView.WebViewTransport).webView = popup
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    loadUrl(configureUrl)
                    webView = this
                }
            },
            onRelease = { it.destroy() },
            )
        }
    }

    // ── Painel do link: link encontrado, colar e digitar ──
    val linkPanel: @Composable ColumnScope.() -> Unit = {
            val link = found
            if (link != null) {
                // Qualquer página aberta aqui (inclusive anúncio dentro dela) consegue oferecer um link.
                // Link de outro site que não o do addon vem marcado, com o site à vista.
                val linkSite = AddonLinks.siteOf(link)
                val sameSite = linkSite == AddonLinks.siteOf(configureUrl)
                val tone = if (sameSite) Success else LiveRed
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = 0.12f))
                        .border(1.dp, tone.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (sameSite) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = tone, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (sameSite) "Link do addon encontrado" else "Link de outro site ($linkSite)",
                            color = tone, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = ManropeFontFamily,
                        )
                        if (!sameSite) Text("Instale só se você confia nesse site", color = TextSecondary, fontSize = 11.sp, fontFamily = ManropeFontFamily)
                        Text(link, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = ManropeFontFamily)
                    }
                    BusyButton(if (sameSite) "Instalar" else "Instalar mesmo assim", busy, primary = sameSite) { onInstall(link) }
                }
            }
            loadError?.let {
                Text(it, color = LiveRed, fontSize = 12.5.sp, fontFamily = ManropeFontFamily, lineHeight = 17.sp)
            }
            note?.let { Text(it, color = LiveRed, fontSize = 12.sp, fontFamily = ManropeFontFamily) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.aotv.app.ui.common.TvTextFieldGate(Modifier.weight(1f)) { gate ->
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it; note = null },
                        singleLine = true,
                        placeholder = { Text("Ou digite o link", color = TextTertiary, fontSize = 13.sp) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent),
                        modifier = gate,
                    )
                }
                if (typed.isNotBlank()) {
                    BusyButton("Usar", busy, primary = false) {
                        val u = AddonLinks.capturedLink(AddonLinks.normalizeManifestUrl(typed))
                        if (u == null) note = "Esse endereço não é um link de addon." else onInstall(u)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val t = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
                            if (!offer(t, true)) note = "Nada de link de addon na área de transferência."
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Default.ContentPaste, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Colar link", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            if (plainUrl != null && found == null) {
                TextButton(onClick = { onInstall(plainUrl) }, enabled = !busy) {
                    Text("Instalar sem configurar", color = TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
    }

    if (tv) {
        // Página à esquerda (ponteiro do controle ou setas) e, à direita, o QR para configurar
        // no celular e o campo para colar ou digitar o link que o site devolver.
        Row(Modifier.fillMaxSize().background(Background)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                header()
                page(Modifier.fillMaxWidth().weight(1f))
            }
            Column(
                Modifier.width(380.dp).fillMaxHeight().background(BackgroundCard)
                    .verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Configurar pelo celular", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = SoraFontFamily)
                Text(
                    "Aponte a câmera do celular para o código, configure lá e copie o link do addon. Depois cole ou digite o link aqui embaixo.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = ManropeFontFamily,
                )
                QrCode(configureUrl, Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                Text(
                    "Na página ao lado use o ponteiro do controle ou as setas. Voltar traz o controle para cá.",
                    color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = ManropeFontFamily,
                )
                // A seta sozinha não entra na página (ela é uma View do Android). Este botão leva o controle.
                com.aotv.app.ui.common.AotvButton(
                    "Usar a página com as setas", { webView?.requestFocus() }, Modifier.fillMaxWidth(),
                    icon = Icons.Default.TouchApp, compact = true,
                )
                Box(Modifier.focusRequester(panelFocus).focusGroup()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { linkPanel() }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding().navigationBarsPadding()) {
        header()
        page(Modifier.fillMaxWidth().weight(1f))
        Column(
            Modifier.fillMaxWidth().background(BackgroundCard).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { linkPanel() }
    }
}

/** QR code preto no branco, um ponto por módulo e sem suavizar. */
@Composable
internal fun QrCode(text: String, modifier: Modifier, description: String = "Código QR da página de configuração") {
    val img = remember(text) {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(
            text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
        )
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        android.graphics.Bitmap.createBitmap(px, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    androidx.compose.foundation.Image(img, description, modifier, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
}

/**
 * Botão de ação que chama a rede. Mostra "Salvando…" com o ícone girando e fica travado até a
 * resposta, para ninguém clicar de novo achando que não deu certo.
 */
@Composable
fun BusyButton(label: String, busy: Boolean, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Mesmo botão pílula do app. Ocupado gira "Salvando…" e trava o segundo toque.
    com.aotv.app.ui.common.AotvButton(
        label, onClick, modifier,
        tone = if (primary) com.aotv.app.ui.common.ButtonTone.PRIMARY else com.aotv.app.ui.common.ButtonTone.SECONDARY,
        busy = busy, compact = true,
    )
}

/** Embrulha a cópia da página e manda o texto para o app. Idempotente. */
private const val HOOK_JS = """
(function(){
 if(window.__aotvHook)return; window.__aotvHook=1;
 var B=window.AotvBridge; function send(t){try{if(t&&B)B.send(String(t))}catch(e){}}
 try{
  var c=navigator.clipboard;
  if(c&&c.writeText){var o=c.writeText.bind(c);c.writeText=function(t){send(t);return o(t).catch(function(){});};}
  else{Object.defineProperty(navigator,'clipboard',{value:{writeText:function(t){send(t);return Promise.resolve();},readText:function(){return Promise.resolve('');}},configurable:true});}
 }catch(e){}
 try{var ex=document.execCommand.bind(document);document.execCommand=function(cmd){
  if(String(cmd).toLowerCase()==='copy'){var a=document.activeElement;send((a&&a.value)||String(window.getSelection?window.getSelection():''));}
  return ex.apply(document,arguments);};}catch(e){}
 document.addEventListener('copy',function(){var a=document.activeElement;send(String(window.getSelection?window.getSelection():'')||(a&&a.value));},true);
})();
"""

/** Junta href e texto da página que pareçam link de addon. Uma linha por candidato. */
private const val SCAN_JS = """
(function(){
 var out=[];
 document.querySelectorAll('a[href],input,textarea').forEach(function(e){var v=e.href||e.value;if(v&&/manifest\.json|^stremio:/i.test(v))out.push(v);});
 var t=document.body?document.body.innerText:'';var m=t.match(/(stremio|https?):\/\/[^\s"'<>]+manifest\.json/ig);if(m)out=out.concat(m);
 return out.slice(-20).join('\n');
})();
"""
