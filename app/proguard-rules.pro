# ============================================================================
# AOTV — Regras de manutenção para o release minificado (R8).
# Sem estas regras, o R8 remove/ofusca os DTOs do Gson parseados por reflexão
# e o app de release crasha (NPE) na primeira chamada de rede.
# ============================================================================

# Atributos necessários para reflexão do Gson (genéricos/TypeToken, anotações).
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# ── DTOs do Gson (data/remote/dto/**) — parseados por reflexão (nomes de campo) ──
-keep class com.aotv.app.data.remote.dto.** { *; }
-keepclassmembers class com.aotv.app.data.remote.dto.** { <fields>; <init>(...); }

# TmdbExternalIds fica fora do pacote dto (em data/remote/TmdbApiService.kt).
-keep class com.aotv.app.data.remote.TmdbExternalIds { *; }

# Modelos de domínio que podem ser usados em (de)serialização direta (precaução).
-keep class com.aotv.app.domain.model.** { *; }

# Reforço genérico: qualquer campo @SerializedName em qualquer pacote.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# CRÍTICO: mantém a CLASSE INTEIRA de qualquer DTO Gson (com @SerializedName).
# Sem isto, o R8 REMOVE/FUNDE classes parseadas por Gson manual que ficam fora do
# pacote .dto (ex.: StremioStreamDto/StremioStreamResponse em data.remote) → o Gson
# não desserializa → fontes Stremio (FenixFlix) somem no release.
-keepclasseswithmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Classes do StremioAddonService (Gson manual, fora de .dto).
-keep class com.aotv.app.data.remote.StremioStream { *; }
-keep class com.aotv.app.data.remote.StremioStreamDto { *; }
-keep class com.aotv.app.data.remote.StremioStreamResponse { *; }

# ── Room entities ──
-keep class com.aotv.app.data.local.entity.** { *; }

# ── Reforço Retrofit (consumer-rules já vêm embutidas; isto é defesa extra) ──
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Google Cast — provider instanciado por reflexão (nome no AndroidManifest).
-keep class com.aotv.app.CastOptionsProvider { *; }

# Suprime warnings de dependências opcionais conhecidas.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Conversão de JavaBeans e descoberta de engines são recursos de JVM do Rhino.
# O extrator de trailers usa Context diretamente no Android.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.script.ScriptEngineFactory

# libtorrent4j (fontes P2P). O nativo chama classes e métodos Java pelo nome (JNI e SWIG).
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
