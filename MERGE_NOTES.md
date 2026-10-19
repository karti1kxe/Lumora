# Lumora merge notes

This workspace was produced by overlaying `lumora-changes.zip` onto `lumora-full.zip`.
The full project structure, including `cloudstream-lib`, tests, resources, and existing Lumora modules, was retained. Files present in the changes archive replace the corresponding files in the full project; new files are added.

## Large dependencies and repository size

The Gradle dependencies (including libtorrent4j, NanoHTTPD, and the mpv Android library) are declared as Maven dependencies and are resolved during the build rather than committed as binary files in this repository. This keeps the repository smaller while packaging the resolved libraries into the APK.

The QuickJS source used by the native CMake build is currently kept under `app/src/main/cpp/third_party/quickjs`; it is source code, not a prebuilt binary. It was retained to avoid making native builds fail due to a missing source tree.

## Important yt-dlp build limitation

The changes archive includes `python313.zip` and the yt-dlp setup files, but does **not** include `libpython.so` for any ABI under `app/src/ytdlp/jniLibs/<abi>/`. The workflow only warns when these files are missing. Therefore, the yt-dlp runtime is not complete until compatible Python 3.13 `libpython.so` binaries are supplied for the target ABIs or the integration is changed to a compatible maintained Android yt-dlp library. Do not treat the current yt-dlp path as verified working.

No APK build or runtime tests were run as part of this merge.
