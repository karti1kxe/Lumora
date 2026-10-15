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


# yt-dlp for Android (Network tab)
# R8 (release build) used to rename / strip parts of the library, so yt-dlp "could not start" with an obfuscated
# exception name. Everything the library touches (its own packages, the zip / io helpers it uses to unpack Python and
# yt-dlp, and Jackson for the JSON it parses) is therefore kept completely.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
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
