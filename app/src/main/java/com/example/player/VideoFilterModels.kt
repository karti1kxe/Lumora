package com.example.player

import androidx.compose.runtime.Immutable

/**
 * 12 Rich Real-Time Video Filter Presets
 */
@Immutable
enum class VideoFilterPreset(
    val id: String,
    val displayName: String,
    val brightness: Int = 0,     // -100 to 100
    val contrast: Int = 0,       // -100 to 100
    val saturation: Int = 0,     // -100 to 100
    val gamma: Int = 0,          // -100 to 100
    val hue: Int = 0,            // -100 to 100
    val temperature: Int = 0,    // -100 to 100 (Negative = Cool, Positive = Warm)
    val tint: Int = 0,           // -100 to 100 (Negative = Green, Positive = Magenta)
    val sharpness: Int = 0,      // 0 to 100
    val deband: Boolean = false
) {
    NONE(
        id = "none",
        displayName = "None"
    ),
    VIVID(
        id = "vivid",
        displayName = "Vivid",
        contrast = 18,
        saturation = 36,
        gamma = 6,
        sharpness = 12
    ),
    WARM_TONE(
        id = "warm_tone",
        displayName = "Warm Tone",
        temperature = 32,
        tint = 6,
        saturation = 8,
        contrast = 6
    ),
    COOL_TONE(
        id = "cool_tone",
        displayName = "Cool Tone",
        temperature = -32,
        tint = -6,
        contrast = 6,
        saturation = 6
    ),
    SOFT_PASTEL(
        id = "soft_pastel",
        displayName = "Soft Pastel",
        brightness = 6,
        contrast = -12,
        saturation = -18,
        gamma = 16
    ),
    CINEMATIC(
        id = "cinematic",
        displayName = "Cinematic",
        contrast = 24,
        saturation = -8,
        gamma = -12,
        temperature = 12,
        sharpness = 12
    ),
    DRAMATIC(
        id = "dramatic",
        displayName = "Dramatic",
        brightness = -6,
        contrast = 38,
        saturation = 14,
        gamma = -18,
        sharpness = 12
    ),
    NIGHT_MODE(
        id = "night_mode",
        displayName = "Night Mode",
        brightness = -14,
        contrast = 8,
        gamma = 22,
        temperature = -16
    ),
    NOSTALGIC(
        id = "nostalgic",
        displayName = "Nostalgic",
        contrast = -6,
        saturation = -22,
        gamma = 12,
        temperature = 32,
        tint = 12
    ),
    GHIBLI_STYLE(
        id = "ghibli_style",
        displayName = "Ghibli Style",
        contrast = 14,
        saturation = 24,
        gamma = 10,
        temperature = 20,
        tint = 8,
        sharpness = 8
    ),
    NEON_POP(
        id = "neon_pop",
        displayName = "Neon Pop",
        contrast = 28,
        saturation = 48,
        gamma = -6,
        sharpness = 22
    ),
    DEEP_BLACK(
        id = "deep_black",
        displayName = "Deep Black",
        brightness = -12,
        contrast = 32,
        gamma = -22,
        deband = true
    );

    companion object {
        fun fromId(id: String?): VideoFilterPreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
        }
    }
}

/**
 * Manual fine-grained video adjustments
 */
@Immutable
data class ManualVideoAdjustments(
    val brightness: Int = 0,     // -100 to 100 (Default: 0)
    val contrast: Int = 0,       // -100 to 100 (Default: 0)
    val saturation: Int = 0,     // -100 to 100 (Default: 0)
    val gamma: Int = 0,          // -100 to 100 (Default: 0)
    val sharpness: Int = 0,      // 0 to 100 (Default: 0)
    val hue: Int = 0,            // -100 to 100 (Default: 0)
    val temperature: Int = 0,    // -100 to 100 (Default: 0)
    val tint: Int = 0,           // -100 to 100 (Default: 0)
    val deband: Boolean = false  // Banding reduction filter
) {
    fun isNeutral(): Boolean =
        brightness == 0 && contrast == 0 && saturation == 0 && gamma == 0 &&
        sharpness == 0 && hue == 0 && temperature == 0 && tint == 0 && !deband
}
