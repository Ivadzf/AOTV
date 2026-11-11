plugins {
    id("com.android.application")             version "8.7.0"        apply false
    id("org.jetbrains.kotlin.android")        version "2.1.20"       apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"       apply false
    id("com.google.devtools.ksp")             version "2.1.20-1.0.32" apply false
    id("com.google.dagger.hilt.android")      version "2.56.2"       apply false
    // Runtime Python dos addons Kodi (v1.1, Fase 0 — spike). MIT, Maven Central.
    id("com.chaquo.python")                   version "17.2.0"       apply false
}
