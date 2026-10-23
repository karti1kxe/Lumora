# Skip R8 optimization passes to reduce build time; shrinking and obfuscation remain active
-dontoptimize

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Native methods and MPV Android Library JNI callbacks
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class is.xyz.mpv.** { *; }
-dontwarn is.xyz.mpv.**

# Room Database (currently unused — see app/build.gradle.kts; kept commented for easy restore)
# -keep class * extends androidx.room.RoomDatabase
# -keep class * extends androidx.room.Entity
# -dontwarn androidx.room.paging.**

# OkHttp, Okio, and optional TLS security providers
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# NewPipeExtractor & Rhino (Mozilla JavaScript engine) & Jsoup
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.jsoup.**
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.schabi.newpipe.extractor.** { *; }

# App models / settings persistence
-keep class com.example.ui.state.** { *; }
-keep class com.example.player.AudioSettings** { *; }
-keep class com.example.player.Track** { *; }


# commons / Jackson / readable exception names (kept as before)
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class kotlin.Metadata { *; }
# readable exception names in the "Could not play this link: ..." message
-keepnames class * extends java.lang.Throwable
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**

# Media plugins (Network > Media): WebView JavaScript bridge methods are called by name from plugin_runtime.js
-keepattributes JavascriptInterface
-keep class com.example.util.media.PluginJsEngine$Bridge { *; }


# CloudStream extensions (.cs3, Network > Media > CloudStream repositories)
# An extension is compiled against the CloudStream library classes (cloudstream-lib) and the libraries it uses, and is
# loaded by class name at runtime, so none of them may be renamed or removed by R8.
-keep class com.lagradost.** { *; }
-dontwarn com.lagradost.**
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlinx.**
-keep class org.jsoup.** { *; }
-keep class com.fleeksoft.** { *; }
-dontwarn com.fleeksoft.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class dev.whyoleg.** { *; }
-dontwarn dev.whyoleg.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn org.slf4j.**
-dontwarn javax.**
-dontwarn java.lang.invoke.**

# Torrent download only: libtorrent4j is a SWIG/JNI binding (native code calls back by name).
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
-keep class com.example.torrent.** { *; }

# yt-dlp bridge (ported from mpvRx): driven by name from mpv options / child processes, never reflectively renamed.
-keep class com.example.ytdlp.** { *; }
