# PROJECT BRAIN — Lumora

> Single source of truth for any AI tool (or human) working on this codebase.
> Read this file fully before changing anything. This file was rewritten from
> scratch during a full-project audit (see "Cleanup Log" at the bottom) — the
> old brain.md described folders and files that no longer existed and was a
> root cause of past confusion/bugs. Trust only this file, not memory of an
> older version of this project.

---

## 1. What this project is

- **Name:** Lumora — a native Android video + audio player.
- **applicationId:** `god.kartik.lumora`
- **Gradle namespace (R-class root):** `com.example` — internal Gradle id only,
  different from applicationId. All Kotlin lives under `package com.example...`.
  **Never change this** — it is wired into `AndroidManifest.xml`, every R
  reference, and the FileProvider authority string.
- **UI:** 100% Jetpack Compose. No XML screen layouts.
- **Playback engine:** `libmpv` via the prebuilt `mpv-android-lib` AAR.
  **There is no ExoPlayer/Media3 anywhere in this app** — it was removed
  because it was a second, unused player stack. Do not re-add media3
  dependencies "to fix a playback bug"; the fix belongs in the mpv code path
  (`player/MpvPlayerController.kt`).
- **Subtitle rendering:** a custom pipeline, not a stock library. It supports
  ASS/SSA (with a hand-written style/override engine), SRT, VTT, SAMI, LRC,
  MPL2, TTML/DFXP, STL, MicroDVD — see Section 4, "player/", for the exact
  files. **This subtitle stack is a core, actively-used feature — treat every
  file listed under it as load-bearing, not legacy.**
- **Online music:** in-app YouTube Music / online audio search & playback via
  NewPipeExtractor (no official YouTube API key used) + LRCLIB for lyrics.
- **Distribution:** GitHub Actions (`.github/workflows/build-apk.yml`) builds
  4 ABI-split release APKs + 1 universal APK on every push to main/master.
  No Play Store integration, no backend server, no Firebase/Gemini/Analytics
  code anywhere in the app (see Section 6).
- **Origin tooling:** scaffolded via Google AI Studio's Android "Build" /
  APK-export feature. `metadata.json` and `README.md` at the repo root are
  AI Studio's own bookkeeping files, not consumed by Gradle — leave them as
  historical context, don't treat them as documentation of current behavior.

---

## 2. Repo root map (accurate as of this cleanup)

```
Lumora/ (repo root)
├── brain.md                     # ← this file
├── README.md                    # AI Studio's auto-generated readme (informational only)
├── metadata.json                # AI Studio app metadata (name/description) — not read by Gradle
├── build.gradle.kts             # root Gradle config (plugin versions only)
├── settings.gradle.kts          # module include (:app)
├── gradle.properties            # Gradle/AndroidX flags
├── gradle/libs.versions.toml    # version catalog — every dependency version lives here
├── gradle/wrapper/              # Gradle wrapper jar + properties
├── gradlew / gradlew.bat        # Gradle wrapper scripts
├── .github/workflows/build-apk.yml   # CI: downloads GoNotoCurrent-Regular.ttf, then assembleRelease
├── branding/
│   ├── ic_homeicon.svg                  # source-of-truth SVG for the launcher icon
│   └── ic_homeicon_full_reference.xml   # a Compose-vector copy of the same icon, kept ONLY as a
│                                         # visual reference for editing — NOT used by the app at
│                                         # runtime (moved out of app/src/main/res in this cleanup;
│                                         # see Cleanup Log). The real launcher icon drawables are
│                                         # ic_launcher_background.xml / ic_launcher_foreground.xml /
│                                         # ic_launcher_monochrome.xml under app/src/main/res/drawable/.
└── app/                          # the one Gradle module
    ├── build.gradle.kts          # applicationId, SDK versions, ABI splits, signing, dependencies
    ├── proguard-rules.pro        # release-build keep rules
    └── src/
        ├── main/  → Section 3
        ├── test/  → Section 5 (JVM unit tests)
        └── androidTest/          # EMPTY on purpose after this cleanup — the only file that used
                                   # to be here was a boilerplate template test that checked nothing
                                   # about Lumora. Add real instrumented UI tests here if you write any.
```

**Important:** `GoNotoCurrent-Regular.ttf` (the multi-script subtitle fallback
font referenced by `player/SubtitleFontManager.kt`) is **intentionally not in
this zip/repo**. It's a large third-party font, so the CI workflow downloads
it fresh into `app/src/main/assets/fonts/` on every build (see the "Fetch
bundled subtitle font" step in `build-apk.yml`). Its absence here is not a bug
— don't "fix" it by adding a copy of the font into the repo.

---

## 3. `app/src/main/` map

```
app/src/main/
├── AndroidManifest.xml     # permissions, MainActivity + all intent-filters, AudioNotificationReceiver,
│                           # AudioPlaybackService (foreground media playback), FileProvider (share feature)
├── assets/fonts/LTWave-Bold.otf     # the app's UI display font (see ui/theme/Type.kt) — ships in the APK
├── java/com/example/
│   ├── MainActivity.kt              # entry point: permissions, PiP, intent handling, HOME↔PLAYER nav
│   ├── ai/                          # (5 files) optional AI features: subtitle translation via a
│   │   │                           #   user-supplied API key (BYO key — no bundled/managed key).
│   │   ├── AiFeatureModels.kt          # AiProvider enum, model info, translation status models
│   │   ├── AiFeaturesSettingsStore.kt  # persists provider choice + (encrypted) API key
│   │   ├── AiProviderService.kt        # HTTP calls to the chosen AI provider
│   │   ├── ApiKeyCrypto.kt             # Android Keystore-backed encryption for the stored API key
│   │   └── SubtitleTranslationService.kt  # parses SRT/ASS/SMI, protects timing/tags, sends text for translation
│   ├── player/                      # (14 files) — CRITICAL, see Section 4
│   ├── ui/
│   │   ├── components/              # (35 files) reusable Compose widgets — sheets, dialogs, cards, panels
│   │   ├── screens/                 # (12 files) full-screen / full-tab composables
│   │   ├── state/                   # (3 files) AppConfig (settings/strings by language), PlayerSettings,
│   │   │                            #   PlayerLayoutModels (customizable on-screen control layout)
│   │   └── theme/                   # (3 files) Color.kt, Theme.kt, Type.kt — Compose Material theme,
│   │                                #   built from the project's OWN palette + LTWave-Bold custom font.
│   │                                #   Never swap in Material's default colors/typography — see Section 7.
│   └── util/                        # (27 files) file IO, caching, playback/watch history, metadata
│                                     #   extraction, private vault, playlists, block-lists, MKV muxing
├── res/
│   ├── drawable/            # 130 vector icon XMLs, flat folder (no drawable-nodpi/xxxhdpi split).
│   │                        # Every icon here is namespaced `lumora_*.xml` except the 3 launcher-icon
│   │                        # layer files (ic_launcher_background/foreground/monochrome) and
│   │                        # ic_abouticon.xml (used by HeaderBar.kt + AboutSettingsPanel.kt).
│   │                        # RULE: before adding a new icon here, grep the codebase for its filename
│   │                        # stem — if you're about to add one that duplicates an existing lumora_*
│   │                        # icon's shape, reuse the existing one instead of creating a near-duplicate.
│   ├── font/ltwave_bold.otf # Compose `Font(R.font.ltwave_bold)` reference to assets/fonts/LTWave-Bold.otf
│   ├── mipmap-anydpi-v26/   # adaptive launcher icon XML (references the 3 ic_launcher_* drawable layers)
│   ├── values/
│   │   ├── strings.xml      # ONLY app_name lives here — all other UI text is hardcoded per-language
│   │   │                    #   inside ui/state/AppConfig.kt (see Section 7), not in strings.xml.
│   │   └── themes.xml       # single bare `Theme.MyApplication` style — actual colors come from
│   │                        #   ui/theme/Theme.kt at runtime, not from this file.
│   └── xml/                 # backup_rules.xml, data_extraction_rules.xml, file_paths.xml (FileProvider paths)
```

---

## 4. `player/` — CRITICAL, do not remove or "simplify" without asking

Every file here backs a real, currently-working feature. If you're asked to
fix a bug, fix it *inside* the relevant file below — don't route around it by
adding a second implementation.

| File | What it does |
|---|---|
| `MpvPlayerController.kt` (3.3k lines) | The mpv engine wrapper: load/seek/speed/loop, track selection, video filters, hwdec, ASS track binding. The single largest and most central file in the app. |
| `VideoPlayerMpvView.kt` | The Compose `AndroidView` surface that hosts libmpv's native rendering surface. |
| `UniversalSubtitleEngine.kt` | Parses/generates WebVTT, SAMI, LRC, MPL2, TTML/DFXP → converts everything to ASS for mpv to render. |
| `AdvancedAssStyleEngine.kt` | Deep ASS/SSA style-line parsing & rewriting (per-style font, color, alignment, outline, etc). |
| `SubtitleFontManager.kt` | Resolves subtitle font names → actual font files: video-embedded fonts (MKV attachments), user-uploaded fonts, and the `GoNotoCurrent-Regular.ttf` multi-script fallback (see Section 2 note — fetched by CI, not bundled here). |
| `AudioMetadataExtractor.kt` | Reads ID3/Vorbis/MP4 tags incl. embedded lyrics (SYLT/USLT/TXXX frames). |
| `AudioPlaybackManager.kt` (1.7k lines) | Full audio-player state machine: queue, audio FX, online-track prefetch/caching, media session integration. |
| `AudioPlaybackService.kt` / `AudioNotificationManager.kt` / `AudioNotificationReceiver.kt` | Foreground service + notification for background audio playback. |
| `OnlineAudioCache.kt` | Disk cache for streamed online audio. |
| `ChapterSkipMarker.kt` | Intro/outro/recap chapter detection by title keyword, for the "skip" button. |
| `AudioSettingsModels.kt` / `VideoFilterModels.kt` | Data models for audio EQ/presets and video filter presets. |

`util/MkvManagerEngine.kt` (3.4k lines, in `util/` not `player/`) is the other
half of the subtitle/media story: a raw EBML/MKV reader+writer used to extract
and re-mux subtitle tracks, chapters, and attachments without a full
transcode. Equally load-bearing — do not remove.

---

## 5. Tests (`app/src/test`, `app/src/androidTest`)

- `app/src/test/java/com/example/SubtitleFontAndSettingsXmlTest.kt` — renamed
  in this cleanup from the generic template name `ExampleUnitTest.kt`. Despite
  the old name, this file contains **real** tests: embedded-font preservation
  in ASS sanitization, missing-font fallback, and settings export/import
  round-trip via `LumoraSettingsXmlManager`. Keep it, keep adding to it.
- `app/src/test/java/com/example/AppStringResourceTest.kt` — renamed from
  `ExampleRobolectricTest.kt`. Real test: confirms `R.string.app_name` resolves
  to "Lumora" via Robolectric.
- `app/src/androidTest/` — now empty. The only file that used to be here
  (`ExampleInstrumentedTest.kt`) just asserted the package name and tested
  nothing about the app; it was removed in this cleanup (see Cleanup Log).
- The Roborazzi screenshot-testing plugin/dependencies are still wired up in
  `app/build.gradle.kts` but **no test currently uses them** (no
  `captureRoboImage` calls anywhere). That's intentional headroom, not a bug —
  a stray leftover screenshot file that had no test behind it was removed in
  this cleanup.

---

## 6. Dependency / build notes

- `isMinifyEnabled = true` and `isShrinkResources = true` for the **release**
  build type only (`app/build.gradle.kts`). That means unused Kotlin code and
  unused resources are already stripped from a proper `assembleRelease` build
  by R8. If an APK you're producing (e.g. via AI Studio's own build button)
  is noticeably larger than a GitHub Actions release build, you are likely
  looking at a **debug** or unshrunk build — the fix is to build the release
  variant, not to hand-delete more source files.
- Several dependencies are deliberately **commented out, not deleted**, in
  `app/build.gradle.kts` (Room, Retrofit/Moshi/OkHttp-logging, CameraX,
  Accompanist permissions, Coil, Navigation-Compose, media3). Each has a
  one-line comment explaining it was verified unused. Leave them commented
  (easy to re-enable) rather than deleting the lines outright.
- Firebase/Gemini: `metadata.json` mentions `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`
  because that's AI Studio's default scaffold metadata — there is genuinely no
  Firebase/Gemini SDK or call anywhere in `app/src`. Don't "wire it up" based
  on that file; it's unrelated to what the app actually does.

---

## 7. UI/branding rule — read before touching any screen or component

This app has its own design language: **liquid-glass panels
(`ui/components/LiquidGlassComponents.kt`), its own color palette
(`ui/theme/Color.kt`/`Theme.kt`), and its own custom display font
(LTWave-Bold via `ui/theme/Type.kt`)**. All in-app text for every supported
language lives in `ui/state/AppConfig.kt` (not `strings.xml` — that file only
holds the Android app label).

**Rule for any AI tool editing this project:** when asked to add or fix UI —
- Reuse the existing gradient styles, glass components, corner radii, and
  color tokens already defined in `ui/theme/` and `ui/components/`.
- Reuse an existing `lumora_*` icon from `res/drawable/` where one already
  fits; only add a new SVG→vector icon when nothing existing is close.
- **Never** fall back to plain Material default colors, default `Icons.*`
  (Material's built-in icon set) for anything user-facing, or a default
  `Box`/plain rectangle where the app already has a styled equivalent. Match
  the existing look exactly — don't introduce a visibly different style for
  one new screen/component.

---

## 8. Cleanup Log (this pass)

Full audit method: every drawable name was grepped against all Kotlin/XML/
manifest files; every top-level Kotlin `class`/`object`/`fun` was grepped
against the rest of the codebase for cross-file usage; every asset/resource
was checked for a real consumer before being touched. Nothing was removed on
guesswork — see the reasoning column.

| Removed / moved | Why |
|---|---|
| `res/drawable/lumora_clipboard.xml`, `lumora_filters.xml`, `lumora_internet_play.xml`, `lumora_maximize_square_minimalistic.xml`, `lumora_star_shine.xml`, `lumora_stars.xml`, `lumora_support.xml`, `lumora_tuning_3.xml`, `lumora_tuning_square.xml`, `lumora_tuning_square_2.xml`, `lumora_video_frame_cut_2.xml` (11 files) | Zero references anywhere in Kotlin, XML, or the manifest. Not used by any icon-name lookup either. |
| `ui/components/CameraShutterApertureIcon.kt` | A complete unused `@Composable` — its only symbol, `CameraShutterApertureIcon`, is never called or imported anywhere. |
| `ui/components/OnlineBottomSelectionBar.kt` | Both symbols in the file (`OnlineBottomSelectionBar`, `OnlineSelectionItem`) are never referenced anywhere else. |
| `app/src/test/screenshots/greeting.png` | Orphaned Roborazzi screenshot with no test that captures/compares it (see Section 5) — leftover from a template "Greeting" test that no longer exists. |
| `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` | Pure Android Studio template boilerplate; only asserted the package name, tested nothing about Lumora. |
| `res/drawable/ic_homeicon.xml` → moved to `branding/ic_homeicon_full_reference.xml` | Never referenced by `R.drawable.ic_homeicon` anywhere — it was a full-icon reference copy of `branding/ic_homeicon.svg`, sitting inside the shipped resource folder for no runtime reason. Moved next to its SVG source instead, where it belongs as reference art, not app resource. |
| `app/src/test/java/com/example/ExampleUnitTest.kt` → renamed `SubtitleFontAndSettingsXmlTest.kt` | File contained real, valuable tests but the generic template name made it look disposable/dead. Renamed only — no logic changed. |
| `app/src/test/java/com/example/ExampleRobolectricTest.kt` → renamed `AppStringResourceTest.kt` | Same reason as above. Renamed only — no logic changed. |

**Nothing under `player/`, `util/` (incl. `MkvManagerEngine.kt`), `ai/`, or any
actively-referenced UI component/screen was touched.** No dependency, no
build.gradle.kts logic, and no CI workflow file was changed.

### How to keep this file honest going forward
Any time you add a new file, delete one, or move one: update the relevant
table/tree above in the same change. If you're an AI tool and you're not sure
whether something is dead, grep the whole `app/src/main` tree for its exact
name before deleting — don't assume unfamiliarity means unused (see Section 4
for a list of files that look large/complex but are all live).
