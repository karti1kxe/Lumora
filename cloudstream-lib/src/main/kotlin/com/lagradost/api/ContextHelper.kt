package com.lagradost.api

import android.content.Context
import java.lang.ref.WeakReference

private var contextRef: WeakReference<Context>? = null

/**
 * Helper function for Android specific context. Not usable in JVM.
 * Do not use this unless absolutely necessary.
 */
fun getContext(): Any? = contextRef?.get()

fun setContext(context: Any?) {
    contextRef = (context as? Context)?.let { WeakReference(it) }
}
