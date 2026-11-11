package com.aotv.app.data.plugins

import org.json.JSONObject

/**
 * Prelúdio JS avaliado em cada execução de plugin, ANTES do código do scraper.
 * Ele fornece o que o QuickJS não tem de fábrica e o que os providers do repo
 * do Davi esperam do ambiente do Nuvio:
 *
 * - fetch global (via binding __nativeFetch, OkHttp com o cookie jar do scraper),
 *   com Response/Headers (incl. getSetCookie) e AbortController/AbortSignal
 *   (16 providers criam AbortController; o abort cancela a Promise, a chamada
 *   OkHttp de fundo é ignorada — o comportamento observável é o mesmo).
 * - console (binding), __plugin_sleep, setTimeout/clearTimeout/setInterval/clearInterval.
 * - atob/btoa, TextEncoder/TextDecoder (UTF-8 puro JS), URL/URLSearchParams
 *   (parse via binding __parseUrl, java.net.URI).
 * - require('cheerio' | 'cheerio-without-node-native') -> { load } (ponte jsoup,
 *   ver PluginCheerio) e require('crypto-js') -> CryptoJS (asset em
 *   assets/plugin-libs/crypto-js-4.2.0.js, avaliado pelo motor antes do provider).
 * - module/exports, global/self/window como aliases de globalThis.
 * - TMDB_API_KEY global (chave do usuário ou undefined) — 23 ocorrências no repo.
 * - __patchCryptoJS(): o crypto-js 4.2.0 NÃO tem modo GCM; o patch desvia
 *   AES.decrypt com mode GCM p/ ponte nativa __aesGcmDecrypt (javax.crypto).
 *   Só o overflix.js usa GCM; os demais usam CBC/Pkcs7 do próprio crypto-js.
 *
 * O que NÃO existe aqui (nenhum provider usa, verificado nos 55 arquivos):
 * localStorage, document, navigator, WebSocket, crypto.subtle, WebAssembly.
 *
 * O placeholder __TMDB_API_KEY_JSON__ é substituído pela chave JSON (ou
 * "undefined") em build(). Não usar template literals nem ${} neste JS:
 * a string é uma raw string Kotlin.
 */
object PluginPrelude {

    private const val PRELUDE = """
// ===== Prelúdio AOTV — ambiente dos plugins do Nuvio =====
var global = globalThis;
var self = globalThis;
var window = globalThis;
var module = { exports: {} };
var exports = module.exports;
var TMDB_API_KEY = __TMDB_API_KEY_JSON__;
globalThis.TMDB_API_KEY = TMDB_API_KEY;

// --- atob / btoa (QuickJS não tem) ---
function atob(s) { return __atob(String(s)); }
function btoa(s) { return __btoa(String(s)); }

// --- __plugin_sleep: binding assíncrono do motor (delay em ms) ---

// --- setTimeout / setInterval sobre __plugin_sleep ---
var __timerSeq = 1;
var __timers = {};
function setTimeout(fn, ms) {
  var id = __timerSeq++;
  var args = Array.prototype.slice.call(arguments, 2);
  __timers[id] = { active: true };
  __plugin_sleep(Math.max(0, ms || 0)).then(function () {
    if (__timers[id] && __timers[id].active) { delete __timers[id]; fn.apply(null, args); }
  });
  return id;
}
function clearTimeout(id) { if (__timers[id]) { __timers[id].active = false; delete __timers[id]; } }
function setInterval(fn, ms) {
  var id = __timerSeq++;
  var args = Array.prototype.slice.call(arguments, 2);
  __timers[id] = { active: true };
  var tick = function () {
    if (!__timers[id] || !__timers[id].active) return;
    fn.apply(null, args);
    if (!__timers[id] || !__timers[id].active) return;
    __plugin_sleep(Math.max(0, ms || 0)).then(tick);
  };
  __plugin_sleep(Math.max(0, ms || 0)).then(tick);
  return id;
}
function clearInterval(id) { clearTimeout(id); }

// --- TextEncoder / TextDecoder (UTF-8, puro JS) ---
function TextEncoder() {}
TextEncoder.prototype.encode = function (str) {
  str = String(str);
  var bytes = [];
  for (var i = 0; i < str.length; i++) {
    var c = str.charCodeAt(i);
    if (c < 128) bytes.push(c);
    else if (c < 2048) { bytes.push(192 | (c >> 6), 128 | (c & 63)); }
    else if (c >= 55296 && c < 57344) {
      var c2 = str.charCodeAt(i + 1);
      if (c < 56320 && c2 >= 56320 && c2 < 57344) {
        var cp = 65536 + ((c - 55296) << 10) + (c2 - 56320);
        bytes.push(240 | (cp >> 18), 128 | ((cp >> 12) & 63), 128 | ((cp >> 6) & 63), 128 | (cp & 63));
        i++;
      } else { bytes.push(239, 191, 189); }
    }
    else bytes.push(224 | (c >> 12), 128 | ((c >> 6) & 63), 128 | (c & 63));
  }
  return new Uint8Array(bytes);
};
function TextDecoder() {}
TextDecoder.prototype.decode = function (input) {
  if (typeof input === 'string') return input;
  if (!input || input.length === undefined) return '';
  var out = '', i = 0;
  while (i < input.length) {
    var b = input[i++];
    if (b < 128) out += String.fromCharCode(b);
    else if (b >= 192 && b < 224) { var b2 = input[i++]; out += String.fromCharCode(((b & 31) << 6) | (b2 & 63)); }
    else if (b >= 224 && b < 240) { var b3 = input[i++]; var b4 = input[i++]; out += String.fromCharCode(((b & 15) << 12) | ((b3 & 63) << 6) | (b4 & 63)); }
    else if (b >= 240) { var b5 = input[i++]; var b6 = input[i++]; var b7 = input[i++]; var cp = ((b & 7) << 18) | ((b5 & 63) << 12) | ((b6 & 63) << 6) | (b7 & 63); cp -= 65536; out += String.fromCharCode(55296 + (cp >> 10), 56320 + (cp & 1023)); }
  }
  return out;
};

// --- URL / URLSearchParams (parse via binding __parseUrl) ---
function URLSearchParams(init) {
  this._map = {};
  if (typeof init === 'string') {
    var q = init.charAt(0) === '?' ? init.slice(1) : init;
    var pairs = q.split('&');
    for (var i = 0; i < pairs.length; i++) {
      var p = pairs[i];
      if (!p) continue;
      var eq = p.indexOf('=');
      var k = eq >= 0 ? decodeURIComponent(p.slice(0, eq).replace(/\+/g, ' ')) : decodeURIComponent(p.replace(/\+/g, ' '));
      var v = eq >= 0 ? decodeURIComponent(p.slice(eq + 1).replace(/\+/g, ' ')) : '';
      if (!this._map[k]) this._map[k] = [];
      this._map[k].push(v);
    }
  }
}
URLSearchParams.prototype.get = function (k) { var v = this._map[String(k)]; return v ? v[0] : null; };
URLSearchParams.prototype.getAll = function (k) { var v = this._map[String(k)]; return v ? v.slice() : []; };
URLSearchParams.prototype.has = function (k) { return !!this._map[String(k)]; };
URLSearchParams.prototype.append = function (k, v) { k = String(k); if (!this._map[k]) this._map[k] = []; this._map[k].push(String(v)); };
URLSearchParams.prototype.set = function (k, v) { this._map[String(k)] = [String(v)]; };
URLSearchParams.prototype.delete = function (k) { delete this._map[String(k)]; };
URLSearchParams.prototype.toString = function () {
  var parts = [];
  for (var k in this._map) { for (var i = 0; i < this._map[k].length; i++) parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(this._map[k][i])); }
  return parts.join('&');
};
URLSearchParams.prototype.entries = function () { var out = []; for (var k in this._map) { for (var i = 0; i < this._map[k].length; i++) out.push([k, this._map[k][i]]); } return out; };
URLSearchParams.prototype.keys = function () { return Object.keys(this._map); };
URLSearchParams.prototype.values = function () { var out = []; for (var k in this._map) out = out.concat(this._map[k]); return out; };
URLSearchParams.prototype.forEach = function (fn, thisArg) { var self = this; this.entries().forEach(function (e) { fn.call(thisArg, e[1], e[0], self); }); };

function URL(url, base) {
  var parsed = JSON.parse(__parseUrl(String(url), base ? String(base) : null));
  if (!parsed.ok) throw new TypeError('Invalid URL: ' + url);
  this._href = parsed.href;
  this.protocol = parsed.protocol;
  this.host = parsed.host;
  this.hostname = parsed.hostname;
  this.port = parsed.port;
  this.pathname = parsed.pathname;
  this.search = parsed.search;
  this.hash = parsed.hash;
  this.origin = parsed.origin;
  this.username = parsed.username || '';
  this.password = parsed.password || '';
  this.searchParams = new URLSearchParams(parsed.search);
}
URL.prototype.toString = function () { return this._href; };
URL.prototype.valueOf = function () { return this._href; };
Object.defineProperty(URL.prototype, 'href', { get: function () { return this._href; }, set: function (v) { var p = JSON.parse(__parseUrl(String(v), null)); if (p.ok) { this._href = p.href; this.protocol = p.protocol; this.host = p.host; this.hostname = p.hostname; this.port = p.port; this.pathname = p.pathname; this.search = p.search; this.hash = p.hash; this.origin = p.origin; this.searchParams = new URLSearchParams(p.search); } } });

// --- AbortController / AbortSignal ---
function AbortSignal() { this.aborted = false; this._listeners = []; this.onabort = null; }
AbortSignal.prototype.addEventListener = function (type, fn) { if (type === 'abort' && typeof fn === 'function') this._listeners.push(fn); };
AbortSignal.prototype.removeEventListener = function (type, fn) { if (type === 'abort') { var i = this._listeners.indexOf(fn); if (i >= 0) this._listeners.splice(i, 1); } };
AbortSignal.prototype._fire = function () {
  if (this.aborted) return;
  this.aborted = true;
  if (typeof this.onabort === 'function') { try { this.onabort(); } catch (e) {} }
  var ls = this._listeners.slice();
  for (var i = 0; i < ls.length; i++) { try { ls[i](); } catch (e) {} }
};
function AbortController() { this.signal = new AbortSignal(); }
AbortController.prototype.abort = function () { this.signal._fire(); };

// --- Headers ---
function Headers(init) {
  this._map = {};
  if (init) {
    if (init instanceof Headers) { this._map = JSON.parse(JSON.stringify(init._map)); }
    else if (typeof init === 'object') {
      if (Array.isArray(init)) { for (var i = 0; i < init.length; i++) this.append(init[i][0], init[i][1]); }
      else { for (var k in init) this.append(k, init[k]); }
    }
  }
}
Headers.prototype.append = function (name, value) { name = String(name).toLowerCase(); if (!this._map[name]) this._map[name] = []; this._map[name].push(String(value)); };
Headers.prototype.set = function (name, value) { this._map[String(name).toLowerCase()] = [String(value)]; };
Headers.prototype.get = function (name) { var v = this._map[String(name).toLowerCase()]; return v ? v.join(', ') : null; };
Headers.prototype.getSetCookie = function () { var v = this._map['set-cookie']; return v ? v.slice() : []; };
Headers.prototype.has = function (name) { return !!this._map[String(name).toLowerCase()]; };
Headers.prototype.delete = function (name) { delete this._map[String(name).toLowerCase()]; };
Headers.prototype.forEach = function (fn, thisArg) { var self = this; Object.keys(this._map).forEach(function (k) { fn.call(thisArg, self._map[k].join(', '), k, self); }); };
Headers.prototype.entries = function () { var out = []; for (var k in this._map) out.push([k, this._map[k].join(', ')]); return out; };
Headers.prototype.keys = function () { return Object.keys(this._map); };
Headers.prototype.values = function () { var out = []; for (var k in this._map) out.push(this._map[k].join(', ')); return out; };

// --- Response ---
function Response(body, status, statusText, headersObj) {
  this._body = (body === undefined || body === null) ? '' : String(body);
  this.status = status || 200;
  this.statusText = statusText || '';
  this.ok = this.status >= 200 && this.status < 300;
  this.headers = new Headers();
  if (headersObj) { for (var k in headersObj) { var vals = headersObj[k]; for (var i = 0; i < vals.length; i++) this.headers.append(k, vals[i]); } }
  this.url = '';
}
Response.prototype.text = function () { return Promise.resolve(this._body); };
Response.prototype.json = function () { return Promise.resolve(JSON.parse(this._body)); };
Response.prototype.clone = function () { return new Response(this._body, this.status, this.statusText, this.headers._map); };

// --- fetch (via binding __nativeFetch: OkHttp + cookie jar do scraper) ---
function fetch(url, options) {
  options = options || {};
  var signal = options.signal;
  var fetchPromise = (async function () {
    if (signal && signal.aborted) throw new Error('AbortError');
    var opts = {
      method: options.method || 'GET',
      headers: {},
      body: (options.body === undefined || options.body === null) ? null : String(options.body)
    };
    if (options.headers) {
      if (options.headers instanceof Headers) { opts.headers = options.headers._map; }
      else if (typeof options.headers === 'object') {
        for (var k in options.headers) opts.headers[k] = options.headers[k];
      }
    }
    var raw = await __nativeFetch(String(url), JSON.stringify(opts));
    var parsed = JSON.parse(raw);
    var res = new Response(parsed.body, parsed.status, parsed.statusText, parsed.headers);
    res.url = String(url);
    return res;
  })();
  if (signal && typeof signal.addEventListener === 'function') {
    return Promise.race([
      fetchPromise,
      new Promise(function (resolve, reject) {
        if (signal.aborted) { reject(new Error('AbortError')); return; }
        signal.addEventListener('abort', function () { reject(new Error('AbortError')); });
      })
    ]);
  }
  return fetchPromise;
}

// --- Cheerio (ponte jsoup; bindings __cheerio*) ---
function Cheerio(handle) { this._h = handle; }
Cheerio.prototype.find = function (sel) { return new Cheerio(__cheerioSelect(this._h, String(sel))); };
Cheerio.prototype.text = function () { return __cheerioText(this._h); };
Cheerio.prototype.attr = function (name) {
  var r = JSON.parse(__cheerioAttr(this._h, String(name)));
  return r.present ? r.value : undefined;
};
Cheerio.prototype.html = function () { return __cheerioHtml(this._h); };
Cheerio.prototype.first = function () { return new Cheerio(__cheerioElement(this._h, 0)); };
Cheerio.prototype.eq = function (i) { return new Cheerio(__cheerioElement(this._h, i)); };
Cheerio.prototype.children = function () { return new Cheerio(__cheerioChildren(this._h)); };
Cheerio.prototype.parent = function () { return new Cheerio(__cheerioParent(this._h)); };
Cheerio.prototype.filter = function (sel) {
  if (typeof sel === 'function') {
    var handles = [];
    var n = __cheerioCount(this._h);
    for (var i = 0; i < n; i++) {
      var el = new Cheerio(__cheerioElement(this._h, i));
      if (sel.call(el, i, el)) handles.push(el._h);
    }
    return new Cheerio(__cheerioCombine(JSON.stringify(handles)));
  }
  return new Cheerio(__cheerioFilter(this._h, String(sel)));
};
Cheerio.prototype.each = function (fn) {
  var n = __cheerioCount(this._h);
  for (var i = 0; i < n; i++) {
    var el = new Cheerio(__cheerioElement(this._h, i));
    if (fn.call(el, i, el) === false) break;
  }
  return this;
};
Cheerio.prototype.map = function (fn) {
  var results = [];
  var n = __cheerioCount(this._h);
  for (var i = 0; i < n; i++) {
    var el = new Cheerio(__cheerioElement(this._h, i));
    results.push(fn.call(el, i, el));
  }
  return new CheerioArray(results);
};
Cheerio.prototype.get = function (i) {
  if (i === undefined) { var all = []; var n = __cheerioCount(this._h); for (var j = 0; j < n; j++) all.push(new Cheerio(__cheerioElement(this._h, j))); return all; }
  return new Cheerio(__cheerioElement(this._h, i));
};
Cheerio.prototype.toArray = function () { return this.get(); };
Object.defineProperty(Cheerio.prototype, 'length', { get: function () { return __cheerioCount(this._h); } });

function CheerioArray(items) {
  this._items = items;
  for (var i = 0; i < items.length; i++) this[i] = items[i];
}
CheerioArray.prototype.get = function () { return this._items; };
CheerioArray.prototype.toArray = function () { return this._items; };
Object.defineProperty(CheerioArray.prototype, 'length', { get: function () { return this._items.length; } });

function load(html) {
  var docHandle = __cheerioLoad(String(html));
  var doc = new Cheerio(docHandle);
  function dollar(sel) {
    if (sel instanceof Cheerio) return sel;
    if (typeof sel === 'string') {
      if (sel.charAt(0) === '<') return new Cheerio(__cheerioParseFragment(sel));
      return new Cheerio(__cheerioSelect(docHandle, sel));
    }
    if (sel && typeof sel === 'object' && sel._h !== undefined) return new Cheerio(sel._h);
    return new Cheerio(0);
  }
  return dollar;
}

// --- require ---
function require(name) {
  if (name === 'cheerio' || name === 'cheerio-without-node-native') return { load: load };
  if (name === 'crypto-js') {
    if (typeof globalThis.CryptoJS === 'undefined') throw new Error('CryptoJS ainda não carregou');
    return globalThis.CryptoJS;
  }
  throw new Error('require não suportado: ' + name);
}

// --- Patch do CryptoJS: modo GCM não existe no crypto-js 4.2.0 ---
// Chamado pelo motor depois de avaliar o asset do CryptoJS.
function __patchCryptoJS() {
  var C = globalThis.CryptoJS;
  if (!C || !C.AES || !C.AES.decrypt) return;
  var originalDecrypt = C.AES.decrypt;
  C.AES.decrypt = function (cipherParams, key, cfg) {
    cfg = cfg || {};
    if (cfg.mode === C.mode.GCM) {
      var ct = cipherParams.ciphertext ? cipherParams.ciphertext.toString(C.enc.Base64) : '';
      var k = key ? key.toString(C.enc.Base64) : '';
      var iv = cfg.iv ? cfg.iv.toString(C.enc.Base64) : '';
      var plain = __aesGcmDecrypt(ct, k, iv);
      return C.enc.Utf8.parse(plain);
    }
    return originalDecrypt.call(C.AES, cipherParams, key, cfg);
  };
}
"""

    /** Código avaliado depois do asset do CryptoJS (expõe o global + aplica o patch GCM). */
    const val POST_CRYPTOJS = "globalThis.CryptoJS = module.exports; module.exports = {}; __patchCryptoJS(); 'ok';"

    fun build(tmdbApiKey: String?): String {
        val keyJson = tmdbApiKey?.takeIf { it.isNotBlank() }?.let { JSONObject.quote(it) } ?: "undefined"
        return PRELUDE.replace("__TMDB_API_KEY_JSON__", keyJson)
    }

    /**
     * Driver que chama getStreams do provider já avaliado e devolve o resultado
     * como JSON string. O 5º argumento é o mapa de configurações do plugin
     * (do onSettings()); providers que só esperam 4 ignoram o extra.
     */
    fun getStreamsDriver(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        settingsJson: String,
    ): String {
        val seasonArg = season?.toString() ?: "null"
        val episodeArg = episode?.toString() ?: "null"
        // v1.0.6 — o evaluate do QuickJS alpha12 devolve a string "Promise"
        // em vez do valor resolvido do IIFE (provado na v1.0.5 pelo Testar
        // rede). Por isso o driver NÃO devolve nada: grava o JSON no global
        // __aotvStreamsResult (e o erro em __aotvStreamsError) e o Kotlin lê
        // por polling — mesmo padrão do testNetworkFetch.
        return """
            globalThis.__aotvStreamsResult = '';
            globalThis.__aotvStreamsError = '';
            (async function () {
              try {
                var exp = (typeof module !== 'undefined' && module.exports) ? module.exports : null;
                var fn = (exp && exp.getStreams) || (typeof exp === 'function' ? exp : null) || globalThis.getStreams;
                if (typeof fn !== 'function') { globalThis.__aotvStreamsResult = '[]'; return; }
                var out = await fn($tmdbId, '$mediaType', $seasonArg, $episodeArg, $settingsJson);
                if (!out) { globalThis.__aotvStreamsResult = '[]'; return; }
                globalThis.__aotvStreamsResult = JSON.stringify(out);
              } catch (e) {
                var msg = String(e && e.message || e);
                globalThis.__aotvStreamsError = msg;
                if (typeof console !== 'undefined' && console.error) console.error(msg);
                globalThis.__aotvStreamsResult = '[]';
              }
            })()
        """.trimIndent()
    }

    // v1.0.6 — mesmo padrão do getStreamsDriver: o resultado vai para o
    // global __aotvSettingsResult (erro em __aotvSettingsError) e o Kotlin
    // lê por polling, porque o evaluate devolve "Promise" em vez do valor.
    const val ON_SETTINGS_DRIVER = """
        globalThis.__aotvSettingsResult = '';
        globalThis.__aotvSettingsError = '';
        (async function () {
          try {
            var exp = (typeof module !== 'undefined' && module.exports) ? module.exports : null;
            var fn = (exp && exp.onSettings) || globalThis.onSettings;
            if (typeof fn !== 'function') { globalThis.__aotvSettingsResult = '[]'; return; }
            var out = await fn();
            if (!out) { globalThis.__aotvSettingsResult = '[]'; return; }
            globalThis.__aotvSettingsResult = JSON.stringify(out);
          } catch (e) {
            var msg = String(e && e.message || e);
            globalThis.__aotvSettingsError = msg;
            if (typeof console !== 'undefined' && console.error) console.error(msg);
            globalThis.__aotvSettingsResult = '[]';
          }
        })()
    """
}
