import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    // Runtime Python dos addons Kodi (v1.1, Fase 0 — spike). Chaquopy 17.2.0.
    id("com.chaquo.python")
}

android {
    namespace   = "com.aotv.app"
    compileSdk  = 35
    defaultConfig {
        applicationId  = "com.aotv.app"
        // v1.1: Chaquopy >= 16 exige minSdk 24 (perde o Android 6.0).
        minSdk         = 24
        targetSdk      = 35
        versionCode    = 23
        versionName    = "1.2.0"
        // v1.1: só arm64-v8a (o celular do Davi é arm64; o APK fica ~20 MB menor).
        ndk { abiFilters += listOf("arm64-v8a") }
        // Chave TMDB lida de gradle property (local.properties / ~/.gradle/gradle.properties),
        // fora do VCS. Sem ela, fica vazia e o usuário informa a própria chave em Configurações.
        val tmdbKey = (project.findProperty("TMDB_API_KEY") as String?) ?: ""
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbKey\"")
        // Trakt (trakt.tv/oauth/applications). Lido do local.properties, que não vai para o git.
        // Vazio deixa o Trakt desligado e escondido nas Configurações.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${local.getProperty("TRAKT_CLIENT_ID", "")}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${local.getProperty("TRAKT_CLIENT_SECRET", "")}\"")
    }
    // Assinatura de release com keystore próprio (fora do VCS).
    // keystore.properties vive um nível acima da raiz do projeto
    // (~/workspace/nuvio-kodi/aotv/keystore.properties) e NÃO vai para o git.
    // Sem ele, o release fica sem assinatura (o build gera APK não assinado).
    val keystoreProps = Properties().apply {
        rootProject.file("../keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    // Assinatura de DEBUG com keystore FIXO (app/debug.keystore, no repo).
    // Sem isso, cada run do GitHub Actions gera uma chave debug diferente
    // (runner efêmero) e o Android recusa instalar a nova versão por cima
    // da antiga (INSTALL_FAILED_UPDATE_INCOMPATIBLE) — o usuário precisa
    // desinstalar e perde os dados do app. A chave debug usa as credenciais
    // padrão do Android Studio (android/android); NÃO é a chave de release.
    // O binário não vai para o repo (o GitHub App não envia binário): vai o
    // Base64 em app/debug.keystore.b64, decodificado aqui quando falta o .keystore.
    val debugKeystoreFile = file("debug.keystore")
    if (!debugKeystoreFile.exists()) {
        val b64 = file("debug.keystore.b64")
        if (b64.exists()) {
            debugKeystoreFile.writeBytes(Base64.getDecoder().decode(b64.readText().trim()))
        }
    }
    // O AGP já cria uma SigningConfig "debug" por padrão; criar outra com
    // create("debug") quebra a configuração ("already exists") — por isso
    // se configura a existente com getByName.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    if (keystoreProps.containsKey("storeFile")) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Release assinado com o keystore próprio (aotv-release.keystore).
            if (keystoreProps.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    lint {
        // AGP 8.7.0 (lint-tools 31.7.0) é binariamente incompatível com a Analysis API
        // do Kotlin 2.1.20 e crasha (IncompatibleClassChangeError) em vários detectores
        // UAST de Compose/LiveData durante o lint-vital, bloqueando o APK de release.
        // Solução definitiva (follow-up): subir o AGP para 8.8+/8.9+.
        disable += setOf(
            "NullSafeMutableLiveData", "FrequentlyChangingValue",
            "RememberInComposition", "AutoboxingStateCreation",
        )
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Nativo do torrent comprimido no APK (sem isso o APK cresce uns 27 MB).
    packaging { jniLibs { useLegacyPackaging = true } }
}

// Runtime Python dos addons Kodi (v1.1, Fase 0 — spike). Chaquopy 17.2.0.
// Python 3.11 casa exato com o CPython 3.11 do Kodi Omega (xbmc.python 3.0.0).
// buildPython("python3.11") exige o Python 3.11 de build no CI — o passo
// actions/setup-python 3.11 no workflow (.github/workflows/build.yml) é do
// Davi via web; sem ele o build do CI falha (o python3 do runner é 3.12+).
chaquopy {
    defaultConfig {
        version = "3.11"
        buildPython("python3.11")
        pip {
            // Fase 1: requests (Vulgata + BrazucaPlay), beautifulsoup4
            // (Vulgata) e six (BrazucaPlay). kodi-six NÃO está no PyPI —
            // vai vendored (app/src/main/python/kodi_six.py). simplejson
            // não tem wheel Android — vai via shim p/ json (aotv_kodi.py).
            install("requests")
            install("beautifulsoup4")
            install("six")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.tv:tv-foundation:1.0.0")
    implementation("androidx.tv:tv-material:1.0.0")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // DNS-over-HTTPS (Cloudflare 1.1.1.1): contorna bloqueio de DNS do provedor.
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    // Capa animada (GIF) das pastas das coleções, igual ao Nuvio. Mesma versão do Coil.
    implementation("io.coil-kt:coil-gif:2.7.0")
    // QR code da configuração de addon na TV (abre a página no celular).
    implementation("com.google.zxing:core:3.5.3")

    // Google Fonts (downloadable fonts via GMS)
    implementation("androidx.compose.ui:ui-text-google-fonts")

    // Trailer YouTube — player oficial (IFrame API) p/ trailers embeddáveis.
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
    // Extração do stream do YouTube (toca trailers com EMBED BLOQUEADO no nosso ExoPlayer).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
    implementation("org.mozilla:rhino:1.7.14")   // deciframento de assinatura (JS) do YouTube
    implementation("org.jsoup:jsoup:1.17.2")
    // Motor JS dos plugins do Nuvio (QuickJS, mesmo lineage do Nuvio; async/await nativo,
    // bindings Kotlin). Rhino não parseia async/await (testado). Versão 1.0.0-alpha12
    // DE PROPÓSITO: o projeto usa Kotlin 2.1.20 e toda a linha 1.0.x estável foi
    // compilada com Kotlin 2.4 — o compilador 2.1.20 rejeita os metadados 2.4 e o
    // build quebra (derrubou a primeira v1.0.2). A alpha12 (Kotlin 2.0.0) tem a
    // mesma API usada pelo motor; a única falta é o timeout nativo de avaliação
    // (o timeout é aplicado na coroutine — ver PluginEngine).
    implementation("io.github.dokar3:quickjs-kt-android:1.0.0-alpha12")

    // Media3 (ExoPlayer HLS)
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.0")
    implementation("androidx.media3:media3-ui:1.6.0")
    // DataSource via OkHttp → ExoPlayer usa o MESMO DNS-over-HTTPS dos streams.
    implementation("androidx.media3:media3-datasource-okhttp:1.6.0")
    // MediaSession — controle pela tela bloqueada, fone Bluetooth e Google Assistant.
    implementation("androidx.media3:media3-session:1.6.0")
    // Cast (transmitir p/ Chromecast) — botão + sessão
    implementation("androidx.media3:media3-cast:1.6.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.appcompat:appcompat:1.7.0") // tema p/ MediaRouteButton do Cast

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Fontes P2P dos addons (torrent). Nativo ARM pra celular e TV box, x86_64 só no debug (emulador).
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39")
    debugImplementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-39")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation("junit:junit:4.13.2")
    // org.json de verdade nos testes de JVM (o do Android é só casca e lança "not mocked").
    testImplementation("org.json:json:20240303")
    // XmlPullParser de verdade para testar o guia XMLTV fora do Android.
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    // Nativo do Windows só pro teste do motor de torrent rodar no PC.
    testImplementation("org.libtorrent4j:libtorrent4j-windows:2.1.0-39")
    // SQLite de verdade no teste da migração do banco (roda no PC, sem emulador).
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")
}
