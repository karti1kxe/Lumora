// CloudStream extension runtime (Network > Media > CloudStream repositories).
//
// The sources under src/main/kotlin/com/lagradost are the CloudStream library (github.com/recloudstream/cloudstream,
// `library` module, GPL-3.0) so that .cs3 extensions - which are compiled against exactly these classes - can be loaded
// by the app. Kotlin-multiplatform expect/actual pairs were merged into plain Android code, and the few app-side classes
// extensions refer to (Plugin, CloudStreamApp, DataStore ...) were rebuilt. Lumora's own code only talks to
// com.lagradost.lumora.CloudStreamEngine (JSON in / JSON out).
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.lagradost.api"
  compileSdk = 36

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-opt-in=com.lagradost.cloudstream3.InternalAPI",
      "-opt-in=com.lagradost.cloudstream3.Prerelease",
      // some CloudStream dependencies are built with a newer Kotlin than this project uses
      "-Xskip-metadata-version-check",
      "-Xskip-prerelease-check",
      // kotlin.time.Instant / Clock and kotlin.uuid.Uuid are only stable from Kotlin 2.3
      "-opt-in=kotlin.time.ExperimentalTime",
      "-opt-in=kotlin.uuid.ExperimentalUuidApi"
    )
  }
}

dependencies {
  // versions follow CloudStream's own gradle/libs.versions.toml
  implementation("androidx.annotation:annotation:1.9.1")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
  implementation("org.jsoup:jsoup:1.22.1")
  implementation("org.jetbrains.kotlinx:atomicfu:0.33.0")
  implementation(libs.kotlinx.coroutines.core)
  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
  implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
  implementation("com.fleeksoft.ksoup:ksoup:0.2.6")
  implementation("io.ktor:ktor-http:3.5.0")
  implementation("com.github.Blatzar:NiceHttp:0.4.18")
  implementation("org.mozilla:rhino:1.8.1")
  implementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
  implementation("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")
  implementation("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
  implementation(libs.newpipe.extractor)
}
