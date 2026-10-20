
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

// yt-dlp (link play / link download / Settings > Network update). Bundled by default; -PenableYtdlp=false or
// ENABLE_YTDLP=false builds without it (no native helpers, no python assets).
val enableYtdlp: Boolean =
  (providers.gradleProperty("enableYtdlp").orNull ?: System.getenv("ENABLE_YTDLP") ?: "true").toBoolean()

android {
  namespace = "com.example"
  // needed by the yt-dlp helper executables (libytdl.so / libqjs.so, src/main/cpp)
  ndkVersion = "27.3.13750724"
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
    buildConfigField("boolean", "ENABLE_YTDLP", enableYtdlp.toString())
    externalNativeBuild {
      cmake {
        arguments += "-DLUMORA_ENABLE_YTDLP=${if (enableYtdlp) "ON" else "OFF"}"
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
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
      jniLibs.setSrcDirs(listOf("src/main/jniLibs") + if (enableYtdlp) listOf("src/ytdlp/jniLibs") else emptyList())
      // ytdl/{setup.py,python313.zip,wrapper} + cacert.pem (see .github/workflows/build-apk.yml)
      if (enableYtdlp) assets.srcDir("src/ytdlp/assets")
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
      // CloudStream runtime libraries (cloudstream-lib) bring multi-release jars with identical entries
      pickFirsts += listOf("META-INF/versions/**", "META-INF/*.kotlin_module")
      excludes += listOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module.bak")
    }
    jniLibs {
      pickFirsts += "**/libc++_shared.so"
      // native libraries are extracted at install time (kept as it was before yt-dlp was removed)
      useLegacyPackaging = true
    }
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
  // Torrent streaming / download (ported from mpvRx): libtorrent4j session + one native lib per ABI, and the
  // loopback byte-range server that feeds the selected file to mpv.
  implementation(libs.libtorrent4j)
  implementation(libs.libtorrent4j.android.arm64)
  implementation(libs.libtorrent4j.android.arm)
  implementation(libs.libtorrent4j.android.x86)
  implementation(libs.libtorrent4j.android.x8664)
  implementation(libs.nanohttpd)
  // CloudStream extension runtime (.cs3 providers, Network > Media > CloudStream repositories)
  implementation(project(":cloudstream-lib"))
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

// Some CloudStream library dependencies pull in the standalone "org.json:json" jar, whose JSONException is a RuntimeException.
// Android already has its own org.json (JSONException is a checked Exception), so the mix made the app fail to start with a
// VerifyError. Always use the Android one.
configurations.configureEach {
  exclude(group = "org.json", module = "json")
}
