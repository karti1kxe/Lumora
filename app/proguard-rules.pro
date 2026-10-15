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
-keep class com.yausername.youtubedl_android.** { *; }
-dontwarn com.yausername.youtubedl_android.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.io.**

# yt-dlp for Android: the library unzips Python/yt-dlp with commons-compress/xz/commons-io and maps JSON with Jackson.
# R8 must not rename/strip them, otherwise yt-dlp "could not start" in the release APK.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.io.** { *; }
-dontwarn org.tukaani.xz.**
-keep class org.tukaani.xz.** { *; }
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
# readable exception names in error messages
-keepnames class * extends java.lang.Throwable
# never fail the release build on optional classes referenced by these libraries
-ignorewarnings
