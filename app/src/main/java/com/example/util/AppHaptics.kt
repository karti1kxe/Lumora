package com.example.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Centralized, global haptic feedback controller for Lumora.
 * Controlled by the user's "Haptic Feedback" appearance setting (Default: ON).
 */
object AppHaptics {
    @Volatile
    var isEnabled: Boolean = true

    /**
     * Performs subtle tactile tick haptic feedback (ideal for volume/brightness steps, seek scrubbing, slider dragging).
     */
    fun performTick(view: View? = null, haptic: HapticFeedback? = null) {
        if (!isEnabled) return
        try {
            if (view != null) {
                val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.SEGMENT_TICK
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
                val performed = view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                if (!performed) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                }
            } else if (haptic != null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Performs gesture threshold / activation haptic feedback (e.g. gesture locked into seek, volume, brightness, or double tap seek).
     */
    fun performGestureThreshold(view: View? = null, haptic: HapticFeedback? = null) {
        if (!isEnabled) return
        try {
            if (view != null) {
                val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
                val performed = view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                if (!performed) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                }
            } else if (haptic != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Performs gesture start haptic feedback (e.g. beginning to drag seekbar, volume, brightness).
     */
    fun performGestureStart(view: View? = null, haptic: HapticFeedback? = null) {
        if (!isEnabled) return
        try {
            if (view != null) {
                val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.GESTURE_START
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
                view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            } else if (haptic != null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Performs haptic feedback in Compose UI if globally enabled.
     */
    fun perform(haptic: HapticFeedback?, type: HapticFeedbackType = HapticFeedbackType.LongPress) {
        if (!isEnabled || haptic == null) return
        try {
            haptic.performHapticFeedback(type)
        } catch (_: Throwable) {}
    }

    /**
     * Performs haptic feedback on an Android View if globally enabled.
     */
    fun perform(view: View?, feedbackConstant: Int = HapticFeedbackConstants.LONG_PRESS) {
        if (!isEnabled || view == null) return
        try {
            view.performHapticFeedback(feedbackConstant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        } catch (_: Throwable) {}
    }
}
