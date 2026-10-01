package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.PixelCopy
import android.view.SurfaceHolder
import `is`.xyz.mpv.BaseMPVView
import java.io.File

class VideoPlayerMpvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {

    var onSurfaceCreatedCallback: (() -> Unit)? = null
    var onSurfaceDestroyedCallback: (() -> Unit)? = null
    var onSurfaceChangedCallback: (() -> Unit)? = null

    init {
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceCreatedCallback?.invoke()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                onSurfaceChangedCallback?.invoke()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceDestroyedCallback?.invoke()
            }
        })
    }

    override fun initOptions() {
        mpv.setOptionString("profile", "fast")
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("hwdec", "mediacodec")
        mpv.setOptionString("hwdec-codecs", "all")
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("audio-channels", "auto-safe")
        mpv.setOptionString("video-sync", "audio")
        // Responsive 0.20s audio buffer ensures instantaneous audio setting updates (<50ms) while maintaining rock-solid playback
        mpv.setOptionString("audio-buffer", "0.20")
        mpv.setOptionString("audio-stream-silence", "yes")
        mpv.setOptionString("audio-pitch-correction", "yes")
        mpv.setOptionString("framedrop", "vo")
        mpv.setOptionString("hr-seek-framedrop", "yes")
        mpv.setOptionString("vd-lavc-threads", "4")
        mpv.setOptionString("vd-lavc-dr", "yes")
        mpv.setOptionString("keepaspect", "yes")
        mpv.setOptionString("force-window", "yes")
        mpv.setOptionString("input-default-bindings", "yes")
        mpv.setOptionString("volume-max", "200")
        mpv.setOptionString("audio-pitch-correction", "yes")

        // Subtitle & ASS Aegisub Rendering Options (libass full support for all subtitle formats: SRT, VTT, SUB, SSA, ASS, PGS/SUP)
        mpv.setOptionString("sid", "no")
        mpv.setOptionString("secondary-sid", "no")
        mpv.setOptionString("sub-visibility", "yes")
        mpv.setOptionString("sub-ass", "yes")
        // Use libass/fontconfig so missing ASS fonts and missing individual glyphs can
        // fall back to the bundled universal subtitle font instead of disappearing.
        mpv.setOptionString("sub-font-provider", "fontconfig")
        mpv.setOptionString("sub-ass-override", "no")
        mpv.setOptionString("sub-ass-force-margins", "no")
        mpv.setOptionString("sub-use-margins", "yes")
        mpv.setOptionString("sub-margin-y", "0")
        mpv.setOptionString("sub-margin-x", "0")
        mpv.setOptionString("sub-pos", "100")
        mpv.setOptionString("blend-subtitles", "no")
        // Bundled multi-script fallback font (Devanagari + most other scripts),
        // not a generic name that may not resolve to anything with real glyph
        // coverage on a given device.
        mpv.setOptionString("sub-font", SubtitleFontManager.BUNDLED_FALLBACK_FONT_FAMILY)
        mpv.setOptionString("sub-font-size", "52")
        mpv.setOptionString("sub-color", "1.0/1.0/1.0/1.0")
        mpv.setOptionString("sub-border-color", "0.0/0.0/0.0/1.0")
        mpv.setOptionString("sub-border-size", "3.0")
        mpv.setOptionString("sub-shadow-offset", "1.5")
        mpv.setOptionString("sub-shadow-color", "0.0/0.0/0.0/0.75")
        mpv.setOptionString("sub-scale-with-window", "yes")
        mpv.setOptionString("sub-ass-scale-with-window", "no")
        mpv.setOptionString("sub-ass-hinting", "none")
        mpv.setOptionString("sub-ass-shaper", "complex")
        mpv.setOptionString("sub-ass-vsfilter-aspect-compat", "yes")
        mpv.setOptionString("sub-ass-vsfilter-blur-compat", "yes")
        mpv.setOptionString("sub-ass-vsfilter-color-compat", "full")
        mpv.setOptionString("sub-fix-timing", "no")
        mpv.setOptionString("sub-codepage", "auto:utf-8")
        mpv.setOptionString("sub-auto", "all")
        mpv.setOptionString("embeddedfonts", "yes")

        // Custom Fonts Directory & Pre-initialized Fallback Fonts (libass font loader)
        val fontsDir = SubtitleFontManager.initializeFontEnvironment(context)
        mpv.setOptionString("sub-fonts-dir", fontsDir.absolutePath)
    }

    override fun postInitOptions() {
        mpv.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        // No-op, polling is used for state updates
    }

    /**
     * Captures the live hardware surface buffer synchronously or via PixelCopy.
     * Used for seamless frame freeze during screen orientation rotation.
     */
    fun captureSurfaceFrame(onResult: (Bitmap?) -> Unit) {
        val surface = holder.surface
        if (surface == null || !surface.isValid || width <= 0 || height <= 0) {
            onResult(null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(
                    this,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            onResult(bitmap)
                        } else {
                            onResult(null)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Throwable) {
                onResult(null)
            }
        } else {
            onResult(null)
        }
    }
}
