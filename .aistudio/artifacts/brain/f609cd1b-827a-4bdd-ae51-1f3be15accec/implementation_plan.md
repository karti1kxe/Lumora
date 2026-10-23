# Lumora Media Player & High-Speed Multi-Source Downloader Rebuild

A comprehensive architectural overhaul for Lumora's network playback and download systems. This upgrade replaces fragmented stubs with an advanced multi-threaded media downloader supporting pause/resume, stream health pre-validation for 18,000+ sites, multi-track audio selection with MKV muxing, lyrics/chapter extraction, server probe verification for Cloudstream/Vega plugins, and unified multi-select download management.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions have been incorporated based on your clarification responses:

- **Confirmed Decision 1 (yt-dlp Engine Deployment)**: yt-dlp will be pre-configured with embedded assets and offline fallback, coupled with a one-tap updater and engine health status inside the Network Settings dashboard.
- **Confirmed Decision 2 (Container Packaging)**: Multi-audio track downloads and multi-language subtitle tracks will be automatically packaged into standard MKV (Matroska) containers with tagged track metadata, ensuring full fidelity and player compatibility without quality loss. Single track downloads will match the native source container (MP4/WebM/MKV).
- **Confirmed Decision 3 (Storage Hierarchy)**: Completed files will be organized under the dedicated `Lumora/` folder on external shared storage with designated subfolders: `Lumora/Videos`, `Lumora/Music`, and `Lumora/Media`.
- **Confirmed Decision 4 (Playback Gate & Zero Empty Player)**: Network link inputs will undergo silent background probe and format resolution before launching the player. Invalid or unreachable streams will cleanly display "This video can't play right now" via a snackbar/toast without opening an empty or black player window.

---

### 1. Overview & Core Concept

Lumora is an all-in-one media player designed for Android. This rebuild transforms the media retrieval and playback pipeline into a robust, high-performance ecosystem:

- **Universal Stream Player (Network Tab)**: Direct playback for standard HTTP/HLS streams and 18,000+ online video/audio platforms powered by an enhanced yt-dlp integration. Pre-flight URL probing ensures the MPV video player opens only when media chunks and metadata are verified and ready.
- **High-Speed Multi-Part Downloader**: A fully managed download coordinator providing multi-threaded chunk downloads, aggressive socket throughput optimization, network error recovery, and byte-range HTTP resume across phone reboots and app restarts.
- **Audio & Subtitle Track Selector**: Advanced download sheets that detect all available stream qualities (144p to 4K/8K 60fps), accurate file sizes, original thumbnails, chapter markers, embedded/external subtitles, and multi-language audio tracks (Original vs translated languages).
- **Media Section (Plugin) Downloader**: Provider server verification that tests mirror links before initiating transfers, retrieves authentic file sizes, eliminates post-processing crashes, and guarantees persistent state.
- **Bulk Media Management**: Full lifecycle controls (play, pause, stop, resume, retry, single delete, and multi-select bulk delete) across all downloads in a single unified interface.

---

### 2. User Experience & Visual Design

#### Key User Flows

1. **Network Tab Online Play**:
   - User pastes a URL (YouTube, Instagram, TikTok, Twitter, Vimeo, or direct video/audio stream).
   - User taps **Play**. The UI displays an elegant inline glass pulse state ("Verifying stream...").
   - If the stream is invalid, expired, or blocked, a clear error banner appears: *"This video cannot be played. Please check the link or site status."* The player view remains closed.
   - If valid, the player launches instantly with the video pre-buffered. The MPV player 3-dot (⋮) menu exposes accessible video quality tracks, audio streams, and subtitle tracks.

2. **Online Video Download Flow**:
   - User taps or swipes **Download** on any video.
   - The app fetches the video stream manifest and displays the **Download Glass Sheet**.
   - The user sees actual resolutions (144p, 360p, 720p, 1080p, 1440p, 2160p60) paired with precise file sizes calculated from stream bitrates.
   - User chooses audio tracks: "Original", language translations, or "All Audio Tracks (MKV)".
   - Subtitles and chapter markers are previewed with toggle switches.
   - Tapping **Start Download** queues the item immediately; a persistent progress bar shows real-time throughput (MB/s), downloaded megabytes, and remaining time.

3. **Online Audio & Music Download Flow**:
   - Displays real audio bitrate tiers (e.g., 128 kbps, 256 kbps, 320 kbps, Lossless FLAC).
   - Automatically attaches high-res artwork, artist/album tags, and synchronizes LRC/text lyrics.

4. **Media Section (Plugins) Flow**:
   - User selects an episode or movie from Cloudstream/Vega providers.
   - The engine queries mirror hosts, filters dead links, extracts genuine sizes, and starts download without pre-transfer stall.
   - Incomplete downloads automatically resume upon app relaunch without restarting from byte 0.

5. **Downloads Tab Management**:
   - Top action bar features an edit icon enabling **Selection Mode**.
   - Checkboxes allow selecting multiple items with a bottom floating action pill: **Select All**, **Resume All**, **Pause All**, and **Delete Selected**.

```
┌──────────────────────────────────────────────────────────────┐
│  Lumora Downloads                         [Edit] [Filter ▼]  │
├──────────────────────────────────────────────────────────────┤
│  [✓] Cyberpunk Edgerunners - Ep 04       720 MB / 1.1 GB    │
│      ├── High-Speed HTTP • 8.4 MB/s • ETA: 45s              │
│      └── [ Pause ]  [ Cancel ]                              │
├──────────────────────────────────────────────────────────────┤
│  [✓] Interstellar (Main Theme) FLAC       42 MB / 42 MB      │
│      └── Completed • Audio with Lyrics & HD Art             │
├──────────────────────────────────────────────────────────────┤
│  [ Bottom Bar: 2 Selected ]         [ Delete (2) ]  [ Pause ]│
└──────────────────────────────────────────────────────────────┘
```

#### Visual Identity & Styling
- **Theme**: Premium Deep Space Dark Glass (`#0B0F17`) accented with vibrant Cyan/Sky Blue (`#38BDF8`) and Lumora Sunset Violet (`#A855F7`).
- **Surfaces**: Frosted glass cards (`tonalElevation = 4.dp`, subtle border `Color.White.copy(alpha = 0.08f)`).
- **Typography**: Display typography using bundled Lumora font for headers with clean Inter/Roboto system fonts for precise data metrics (MB, speed, duration).
- **Feedback & Motion**: Spring-damped pull-to-refresh, micro-haptic ticks on track selection, and smooth progress transitions.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Direct Network Pre-Flight Validation**
  - *Chosen Approach*: Validate URLs through a non-blocking OkHttp HEAD/GET probe or yt-dlp metadata probe (`--dump-json` / format inspection) with a strict 4-second timeout before invoking MPV.
  - *Why*: Eliminates the black/stuck screen experience where the player opens only to show an immediate decode failure.
  - *Trade-off*: Adds a brief ~300ms verification step that pays off with instant playback once the player opens.

- **Decision 2: Chunked Multi-Connection Downloader with Resume Cache**
  - *Chosen Approach*: Custom chunked download engine utilizing HTTP `Range: bytes=X-Y` headers and local temporary `.part` files saved to app cache before atomic commit to `Lumora/`.
  - *Why*: Maximizes bandwidth utilization (up to 4 parallel sockets per file), supports instant pause/resume, and prevents corrupt partial files.
  - *Trade-off*: Requires managing temporary chunk allocation and merging for non-sequential transfers.

- **Decision 3: Server Probing for Plugin Extractors**
  - *Chosen Approach*: Query extractor mirrors asynchronously, run lightweight HTTP HEAD requests to verify status codes `200 OK` or `206 Partial Content` and extract `Content-Length`, discarding unresponsive mirrors before presenting the list to the user.
  - *Why*: Prevents users from selecting broken mirrors and eliminates unexpected download stalls.

- **Decision 4: Fixing Compiler Incompatibilities**
  - *Chosen Approach*: Replace the broken `DownloadOptionsSheet.kt` implementation with a unified `DownloadGlassSheet` component that provides all required parameters for `TorrentPickerSheet.kt`, `MediaDetailScreen.kt`, and the Network tab.
  - *Why*: Resolves compiler breakage immediately while centralizing download configuration in one responsive sheet.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                            │
│   NetworkTabContent     MediaDetailScreen     DownloadsTabContent      │
│          │                      │                      │               │
│   [Stream Validator]    [DownloadGlassSheet]   [Multi-Select Bar]      │
└──────────┬──────────────────────┬──────────────────────┬───────────────┘
           │                      │                      │
           ▼                      ▼                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                            CORE ENGINES                                │
│   ┌────────────────────┐ ┌───────────────────┐ ┌───────────────────┐   │
│   │ NetworkStream-     │ │ YtdlpManager &    │ │ CloudStreamEngine │   │
│   │ Resolver (Probe)   │ │ Engine Updater    │ │ & Mirror Prober   │   │
│   └─────────┬──────────┘ └─────────┬─────────┘ └─────────┬─────────┘   │
└─────────────┼──────────────────────┼─────────────────────┼─────────────┘
              │                      │                     │
              ▼                      ▼                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      DOWNLOAD COORDINATOR LAYER                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │                      DownloadManager                           │   │
│   │   • Parallel Chunk Engine (OkHttp + Range Headers)             │   │
│   │   • Multi-Format Muxer (Video + Audio Tracks + Subtitles)      │   │
│   │   • Persistent Task Registry (DownloadStore + Recovery)        │   │
│   │   • Lifecycle Controls (Pause, Resume, Stop, Multi-Delete)     │   │
│   └────────────────────────────────┬───────────────────────────────┘   │
└────────────────────────────────────┼───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           STORAGE TARGETS                              │
│   /storage/emulated/0/Lumora/                                          │
│   ├── Videos/     (MKV / MP4 with tagged audio & subs)                 │
│   ├── Music/      (MP3 / M4A / FLAC + embedded artwork + .lrc)         │
│   └── Media/      (Movies & Series episodes from providers)            │
└────────────────────────────────────────────────────────────────────────┘
```

#### Core Data Entities

- `DownloadTask`:
  - `id`: Unique task identifier.
  - `sourceUrl`: Remote media or stream URL.
  - `title`, `artist`, `thumbnailUrl`: Visual metadata.
  - `targetFolder`: Subfolder category (`Videos`, `Music`, `Media`).
  - `targetFileName`: Formatted output name.
  - `status`: `QUEUED`, `PROBING`, `DOWNLOADING`, `PAUSED`, `PROCESSING`, `COMPLETED`, `FAILED`.
  - `downloadedBytes`, `totalBytes`, `speedBps`, `etaSeconds`.
  - `selectedAudioTracks`: List of audio track indices / language tags.
  - `selectedSubtitles`: Subtitle languages and formats.
  - `hasLyrics`: Boolean flag indicating synchronized lyrics extraction.
  - `sourceType`: `YTDLP`, `DIRECT_STREAM`, `PROVIDER_PLUGIN`, `TORRENT`.

#### Implementation Steps
1. **Compilation & Core Interface Restoration**: Rebuild `DownloadOptionsSheet.kt` to export `DownloadGlassSheet` with comprehensive quality picker, audio track selector, and subtitle toggles.
2. **DownloadManager Overhaul**: Upgrade `DownloadManager.kt` from torrent-only to a unified engine supporting HTTP range-based multi-threading, yt-dlp media extraction, provider stream downloads, and persistent resume.
3. **Storage Organizer**: Implement `AppStorageManager` routines that create and write to `/storage/emulated/0/Lumora/{Videos,Music,Media}` with scoped storage compatibility.
4. **Network Tab Stream Verification**: Enhance `NetworkTabContent.kt` and `NetworkStreamResolver.kt` with fast background stream pre-flight probing and user-friendly error banners.
5. **Downloads Screen Upgrade**: Update `DownloadsTabContent.kt` with multi-select deletion, batch controls (Pause All / Resume All), and accurate progress indicators.
6. **Provider Mirror Probing**: Enhance `MediaDetailScreen.kt` to verify provider links, fetch authentic content lengths, and feed them into the unified download pipeline.
7. **Verification**: Run `compile_applet` to verify clean compilation across all modules and validate stability.
