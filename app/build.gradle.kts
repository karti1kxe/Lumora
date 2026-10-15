plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  // google-services plugin removed: no Firebase code exists anywhere in this app
  // (no FirebaseApp, AppCheck, or Gemini/firebase-ai calls), so the plugin, the
  // firebase-bom, firebase-ai, and firebase-appcheck-recaptcha dependencies below
  // were all unused dead weight left over from the AI Studio default template.
}

android {
  namespace = "com.example"
  // Use the stable API 36 platform that GitHub runners and AI Studio both provide.
  // Avoid requiring the 36.1 preview/minor platform just to compile the app.
  compileSdk = 36

  defaultConfig {
    applicationId = "god.kartik.vwlwas"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      isUniversalApk = true
    }
  }

  sourceSets {
    getByName("main") {
      jniLibs.setSrcDirs(listOf("src/main/jniLibs"))
    }
  }

  signingConfigs {
    val releaseKeystoreEnv = System.getenv("KEYSTORE_PATH")
    val releaseKeystoreFile = if (!releaseKeystoreEnv.isNullOrEmpty()) file(releaseKeystoreEnv) else file("${rootDir}/my-upload-key.jks")
    val hasValidReleaseKeystore = releaseKeystoreFile.exists() && !System.getenv("STORE_PASSWORD").isNullOrEmpty()

    if (hasValidReleaseKeystore) {
      create("release") {
        storeFile = releaseKeystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val hasReleaseConfig = signingConfigs.findByName("release") != null
      if (hasReleaseConfig) {
        signingConfig = signingConfigs.getByName("release")
      } else {
        logger.warn("WARNING: Release keystore or credentials not found. Falling back to debugConfig (debug-signed APK).")
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += listOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module.bak")
    }
    jniLibs {
      pickFirsts += "**/libc++_shared.so"
      // youtubedl-android runs its bundled python/yt-dlp binaries from the native-lib directory,
      // so the .so files must be extracted at install time.
      useLegacyPackaging = true
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Latest official yt-dlp (github.com/yt-dlp/yt-dlp) is downloaded at BUILD time (GitHub Actions),
// so the ~3 MB binary never has to live in the repository. It is added as the raw resource
// "ytdlp", which overrides the older copy that youtubedl-android ships, plus a string resource
// "bundled_ytdlp_version" the app uses to refresh an already-installed older copy.
// ---------------------------------------------------------------------------------------------
val ytdlpGenDir = layout.buildDirectory.dir("generated/ytdlp/res")
val downloadYtDlp by tasks.registering {
  val outDir = ytdlpGenDir
  outputs.dir(outDir)
  // always re-check so every CI build gets the newest release
  outputs.upToDateWhen { false }
  doLast {
    val res = outDir.get().asFile
    val rawFile = File(res, "raw/ytdlp")
    val valuesFile = File(res, "values/ytdlp_version.xml")
    rawFile.parentFile.mkdirs()
    valuesFile.parentFile.mkdirs()
    val tmp = File(res, "raw/ytdlp.part")
    var ok = false
    var lastError: Throwable? = null
    for (attempt in 1..3) {
      try {
        val conn = java.net.URI("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp").toURL().openConnection() as java.net.HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (tmp.length() > 1_000_000L) { ok = true; break }
      } catch (t: Throwable) {
        lastError = t
      }
    }
    if (ok) {
      tmp.copyTo(rawFile, overwrite = true)
      tmp.delete()
    } else if (!rawFile.exists()) {
      throw GradleException("Could not download yt-dlp from GitHub", lastError)
    } else {
      logger.warn("yt-dlp download failed, reusing the previously downloaded copy")
    }
    var version = "unknown"
    try {
      java.util.zip.ZipFile(rawFile).use { zf ->
        val entry = zf.getEntry("yt_dlp/version.py")
        if (entry != null) {
          val text = zf.getInputStream(entry).bufferedReader().readText()
          Regex("__version__\\s*=\\s*'([^']+)'").find(text)?.let { version = it.groupValues[1] }
        }
      }
    } catch (_: Throwable) {}
    // stop the release resource shrinker from dropping the yt-dlp binary / version (only referenced from library code)
    File(res, "raw/ytdlp_keep.xml").writeText(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources xmlns:tools=\"http://schemas.android.com/tools\" tools:keep=\"@raw/ytdlp,@string/bundled_ytdlp_version\" />\n"
    )
    valuesFile.writeText(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <string name=\"bundled_ytdlp_version\" translatable=\"false\">$version</string>\n</resources>\n"
    )
    logger.lifecycle("yt-dlp bundled: $version")
  }
}
android.sourceSets.getByName("main").res.srcDir(ytdlpGenDir)
// every task that reads the resource folders must run after the download
tasks.configureEach {
  val n = name
  if (n != "downloadYtDlp" && (n == "preBuild" || n.contains("Resources") || n.contains("ResValues") ||
      n.contains("RFile") || n.startsWith("lint") || n.contains("ShrinkRes") || n.contains("shrink") ||
      n.contains("Manifest"))) {
    dependsOn(downloadYtDlp)
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  // Room (runtime/ktx/compiler) and Retrofit/Moshi/OkHttp: verified zero usage anywhere in
  // app/src/main/java (no @Entity/@Dao/@Database, no Retrofit/okhttp3/Moshi/@Json reference —
  // see brain.md Section 14/15/17). Commented out rather than deleted, matching this project's
  // own convention above, so they're one line away from being re-enabled if ever needed.
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // Firebase (AI/Firestore/Auth/AppCheck) intentionally not included — this app
  // doesn't use any Firebase services.
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // Re-enabled for Online Music (NewPipeExtractor's Downloader impl + LRCLIB lyrics calls) —
  // see brain.md Section 17 and util/OnlineMusicService.kt / util/LyricsFetcher.kt.
  implementation(libs.okhttp)
  implementation(libs.newpipe.extractor) {
    // NewPipeExtractor pulls in its own transitive parser/utility libs; exclude nothing here,
    // this exclude list is intentionally empty and left as a hook if a future conflict appears.
  }
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  implementation(libs.androidx.media)
  implementation(libs.mpv.android.lib)
  // yt-dlp for Android (Network tab): resolves YouTube and the other sites yt-dlp supports to playable stream URLs.
  implementation(libs.youtubedl.android.library)
  // media3-exoplayer / media3-ui removed: not used anywhere in the code — the app
  // plays everything through mpv-android-lib. Keeping them only added unused APK
  // weight and a second, unused player stack.
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
