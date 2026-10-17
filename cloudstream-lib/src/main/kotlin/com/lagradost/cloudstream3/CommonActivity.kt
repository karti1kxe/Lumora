package com.lagradost.cloudstream3

import android.app.Activity
import android.widget.Toast
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import java.lang.ref.WeakReference

/** Stand-in for CloudStream's `CommonActivity` companion that extensions call (activity, showToast). */
class CommonActivity {
    companion object {
        private var _activity: WeakReference<Activity>? = null
        var activity: Activity?
            get() = _activity?.get()
            set(value) {
                _activity = value?.let { WeakReference(it) }
            }

        fun showToast(message: String?, duration: Int? = null) {
            if (message == null) return
            val ctx = CloudStreamApp.context ?: return
            runOnMainThread {
                try {
                    Toast.makeText(ctx, message, duration ?: Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {
                }
            }
        }

        fun showToast(activity: Activity?, message: String?, duration: Int? = null) = showToast(message, duration)
    }
}
