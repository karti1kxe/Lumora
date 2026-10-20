# Ported from mpvRx (AGPL-3.0-or-later), adapted for Lumora.
cmake_minimum_required(VERSION 3.22.1)

project("lumora_native" C CXX)

# Release-only native optimization shared by Lumora-owned JNI/helper targets.
# Keep this scoped to our small native targets; the prebuilt mpvlib AARs are
# optimized separately when their mpv/FFmpeg toolchain is built.
set(LUMORA_NATIVE_RELEASE_COMPILE_OPTIONS
    "$<$<CONFIG:Release>:-ffunction-sections>"
    "$<$<CONFIG:Release>:-fdata-sections>"
    "$<$<CONFIG:Release>:-fvisibility=hidden>"
)

set(LUMORA_NATIVE_LINK_OPTIONS
    "-Wl,-z,max-page-size=16384"
    "$<$<CONFIG:Release>:-Wl,-O1>"
    "$<$<CONFIG:Release>:-Wl,--gc-sections>"
    "$<$<CONFIG:Release>:-Wl,--icf=safe>"
)

function(lumora_optimize_native_target target)
    target_compile_options(${target} PRIVATE ${LUMORA_NATIVE_RELEASE_COMPILE_OPTIONS})
    target_link_options(${target} PRIVATE ${LUMORA_NATIVE_LINK_OPTIONS})
endfunction()

# Both targets exist only for yt-dlp. Gradle turns this off with -PenableYtdlp=false.
option(LUMORA_ENABLE_YTDLP "Build the yt-dlp helper executables" ON)
if(NOT LUMORA_ENABLE_YTDLP)
    return()
endif()

# YTDLP execution wrapper for SDK 29+ bypass
add_executable(ytdl_wrapper ytdl_wrapper.c)
set_target_properties(ytdl_wrapper PROPERTIES OUTPUT_NAME "ytdl")
set_target_properties(ytdl_wrapper PROPERTIES PREFIX "lib" SUFFIX ".so")
lumora_optimize_native_target(ytdl_wrapper)

# QuickJS-NG CLI used by current yt-dlp releases for YouTube n/sig challenges.
# Package it as an executable .so beside libytdl.so so Android permits subprocess execution.
# The QuickJS-NG sources are not committed: .github/workflows/build-apk.yml downloads the pinned release into
# third_party/quickjs before Gradle runs. Without them (a build outside CI) only libytdl.so is produced.
set(QUICKJS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/quickjs")
if(NOT EXISTS "${QUICKJS_DIR}/quickjs.c")
    message(WARNING "QuickJS-NG sources not found in ${QUICKJS_DIR}: libqjs.so is not built (CI fetches them).")
    return()
endif()
add_executable(
    qjs_runtime
    "${QUICKJS_DIR}/dtoa.c"
    "${QUICKJS_DIR}/gen/repl.c"
    "${QUICKJS_DIR}/gen/standalone.c"
    "${QUICKJS_DIR}/libregexp.c"
    "${QUICKJS_DIR}/libunicode.c"
    "${QUICKJS_DIR}/qjs.c"
    "${QUICKJS_DIR}/quickjs-libc.c"
    "${QUICKJS_DIR}/quickjs.c"
)
set_target_properties(
    qjs_runtime
    PROPERTIES
    OUTPUT_NAME "qjs"
    PREFIX "lib"
    SUFFIX ".so"
    C_STANDARD 11
    C_STANDARD_REQUIRED ON
    C_EXTENSIONS ON
    ENABLE_EXPORTS TRUE
)
target_include_directories(qjs_runtime PRIVATE "${QUICKJS_DIR}")
target_compile_definitions(qjs_runtime PRIVATE _GNU_SOURCE QUICKJS_NG_BUILD)
target_compile_options(qjs_runtime PRIVATE -funsigned-char)
target_link_libraries(qjs_runtime dl m)
lumora_optimize_native_target(qjs_runtime)
