# Implementation Plan — Direct Combined Video+Audio & Fast-Start Playback

## 1. Problem Analysis & Root Cause
- **Audio Attachment Failure (`video/x-vnd.on2.vp9 + audio/mp4a-latm`):**
  When downloading Instagram or social links, format resolution was falling back to split adaptive streams (separate VP9 video stream + separate AAC audio stream). Android's native `MediaMuxer` cannot mux VP9 into MP4 containers or AAC into WebM containers, throwing `Failed to attach audio track to video` and cancelling the download.
- **User Directives:**
  1. Do not use post-download mixing or splitting ("bad mein mix karne wala solution mat dekhna"). Download video and audio together as a direct combined stream right from the server.
  2. For Instagram, Facebook, Twitter, and other platforms, always download direct progressive MP4 streams that already contain audio from the server.
  3. Single audio download must download the direct audio stream with the video thumbnail embedded.
  4. Reduce buffering and prevent freezing when pasting links one after another in the Network tab.

---

## 2. Proposed Changes

### A. Pre-Combined Progressive Format Specification (`YtdlpDownloader.kt` & `DownloadManager.kt`)
- Update `DEFAULT_SINGLE_FILE_FORMAT` to strictly prioritize pre-combined progressive streams (`hasaud`):
  `"best[ext=mp4][acodec!=none][vcodec!=none]/best[acodec!=none][vcodec!=none]/best[vcodec!=none][acodec!=none]/b"`
- Pass `--format-sort "hasaud,res,fps,codec:h264"` to yt-dlp arguments so yt-dlp always prefers streams that already have multiplexed audio from the server, eliminating any need to split or merge streams.
- For social domains (Instagram, Facebook, Twitter/X, TikTok, Reddit, etc.), completely bypass any multi-stream split format generation and force direct combined progressive download.

### B. Format Probing & Quality Selection (`YtdlpProbe.kt` & `DownloadOptionsSheet.kt`)
- In `YtdlpProbe.kt`:
  - When probing formats for Instagram or social media sites, only include streams that have `hasAudio == true` (`acodec != none` and `vcodec != none`).
  - Never pair a VP9 video stream with an AAC audio stream. Ensure codec compatibility (AVC/H264 + AAC, or VP9 + Opus).
  - In `DownloadOptionsSheet.kt`, ensure that for Instagram and social links, all offered qualities are single pre-combined files where audio is already attached from the server.

### C. Direct Stream Fast-Start Playback (`NetworkStreamResolver.kt` & `YtdlpProbe.kt`)
- Optimize `resolveForPlayback`:
  - Extract direct progressive MP4 stream URLs (`best[ext=mp4][acodec!=none]`) directly.
  - Cache direct playback URLs with instant lookup, avoiding repeated extraction overhead.
  - Pass the direct URL directly to the media player to achieve instant playback with minimal buffering, even when pasting sequential video links.

### D. Audio-Only Direct Download & Cover Attachment
- For audio-only mode, target `bestaudio[ext=m4a]/bestaudio[ext=mp3]/ba[vcodec=none]`.
- Directly download the audio file without video, and embed the video thumbnail using `MediaTaggingEngine.embedAudioCover`.

---

## 3. Verification & Testing
- Compile applet with `compile_applet`.
- Verify format specifications across social links and YouTube links.
- Confirm that `Failed to attach audio track to video` is eliminated because pre-combined streams are fetched directly without post-mix failures.
