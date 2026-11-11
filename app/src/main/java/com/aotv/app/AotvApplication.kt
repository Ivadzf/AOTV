package com.aotv.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers

@HiltAndroidApp
class AotvApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Inicializa o Google Cast (lazy). Em aparelho sem Google Play Services (algumas TV
        // boxes), getSharedInstance lança — engolimos: o app funciona sem Cast.
        runCatching {
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
        }
    }

    // ImageLoader afinado para TV box / aparelhos fracos (Android 9+):
    // - cache de memória limitado (15%) e disco (256MB) evita OOM/jank
    // - RGB_565 corta uso de memória de bitmap pela metade (posters/backdrops)
    // - crossfade leve; sem allowHardware em telas que reusam bitmaps em listas
    override fun newImageLoader(): ImageLoader {
        // Device fraco (TV box / low-RAM): sem crossfade (anima bitmap a cada scroll = jank),
        // sem hardware bitmap (Mali antiga estoura o limite em listas) e cache menor.
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val lowRam = am.isLowRamDevice || am.memoryClass <= 128
        return ImageLoader.Builder(this)
            // As imagens seguem o resolvedor do catálogo para evitar capas vazias por falha de DNS.
            .okHttpClient { com.aotv.app.data.remote.AppDns.mediaClient }
            // v1.0.8 — no máximo 4 imagens baixando/decodificando ao mesmo tempo.
            // Sem limite, a home disparava dezenas de downloads juntos e, em
            // internet lenta, saturava a conexão e travava o app (o Davi achou
            // que o travamento era dos pôsteres carregando — era).
            .dispatcher(Dispatchers.IO.limitedParallelism(4))
            .crossfade(if (lowRam) 0 else 150)
            .allowRgb565(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRam) 0.10 else 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .apply {
                // Sem hardware bitmap em Android < 8 OU device fraco (mais estável em listas longas).
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || lowRam) allowHardware(false)
            }
            // GIF anima (capa das pastas das coleções). Android 9+ usa o decodificador do sistema.
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(coil.decode.ImageDecoderDecoder.Factory())
                else add(coil.decode.GifDecoder.Factory())
            }
            .build()
    }
}
