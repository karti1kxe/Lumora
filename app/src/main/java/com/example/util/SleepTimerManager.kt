package com.example.util

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide Sleep Timer (one timer shared by the video player, the offline audio player and the
 * online video / audio players).
 *
 * The timer lives here (not inside a screen) so it keeps running when the user leaves the
 * player, minimises the audio player or turns the screen off. When it reaches zero it emits
 * [expired]; MainActivity and VideoPlayerScreen react to that event (close the video with the
 * normal exit animation, stop audio, step back out of the folders and close the app).
 */
object SleepTimerManager {
    const val MAX_DURATION_MS: Long = 12L * 60L * 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private var endAtElapsedMs: Long = 0L

    private val _remainingMs = MutableStateFlow(0L)
    /** Milliseconds left on the running timer (0 when no timer is running). */
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits once every time a running timer reaches zero. */
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    fun start(durationMs: Long) {
        val clamped = durationMs.coerceIn(0L, MAX_DURATION_MS)
        if (clamped <= 0L) {
            cancel()
            return
        }
        endAtElapsedMs = SystemClock.elapsedRealtime() + clamped
        _remainingMs.value = clamped
        _isActive.value = true
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                val left = endAtElapsedMs - SystemClock.elapsedRealtime()
                if (left <= 0L) break
                _remainingMs.value = left
                delay(minOf(left, 250L))
            }
            _remainingMs.value = 0L
            _isActive.value = false
            tickJob = null
            _expired.tryEmit(Unit)
        }
    }

    /** Adds (or removes, if negative) time on a running timer. Reaching 0 cancels it. */
    fun adjust(deltaMs: Long) {
        if (!_isActive.value) return
        val left = (endAtElapsedMs - SystemClock.elapsedRealtime() + deltaMs).coerceAtMost(MAX_DURATION_MS)
        if (left <= 0L) {
            cancel()
        } else {
            endAtElapsedMs = SystemClock.elapsedRealtime() + left
            _remainingMs.value = left
        }
    }

    fun cancel() {
        tickJob?.cancel()
        tickJob = null
        _remainingMs.value = 0L
        _isActive.value = false
    }

    /** "1h 05m" / "12:05" style label used by the panel and the badges. */
    fun formatCountdown(ms: Long): String {
        val totalSec = ((ms + 999L) / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0L) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    /** Label for a chosen (not yet started) duration, e.g. "0 min", "15 min", "1 h 30 min". */
    fun formatDuration(ms: Long): String {
        val totalMin = ms / 60000L
        val h = totalMin / 60L
        val m = totalMin % 60L
        return when {
            h > 0L && m > 0L -> "$h h $m min"
            h > 0L -> "$h h"
            else -> "$m min"
        }
    }
}
