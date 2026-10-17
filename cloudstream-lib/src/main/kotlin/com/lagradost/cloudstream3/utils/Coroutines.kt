package com.lagradost.cloudstream3.utils

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.*

@SuppressLint("ThreadConstraint") // mainLooper.isCurrentThread does not switch the context
@AnyThread
fun runOnMainThreadNative(@MainThread work: (() -> Unit)) {
    val mainLooper = Looper.getMainLooper()
    if (mainLooper.isCurrentThread) {
        work()
    } else {
        Handler(mainLooper).post(work)
    }
}

val workerDispatcher: CoroutineDispatcher = Dispatchers.IO
internal typealias WorkerThread = androidx.annotation.WorkerThread

object Coroutines {
    @AnyThread
    fun <T> T.main(@MainThread work: suspend ((T) -> Unit)): Job {
        val value = this
        return CoroutineScope(Dispatchers.Main).launchSafe {
            work(value)
        }
    }

    @AnyThread
    fun <T> T.ioSafe(@WorkerThread work: suspend (CoroutineScope.(T) -> Unit)): Job {
        val value = this
        return CoroutineScope(workerDispatcher).launchSafe {
            work(value)
        }
    }

    @AnyThread
    suspend fun <T, V> V.ioWorkSafe(@WorkerThread work: suspend (CoroutineScope.(V) -> T)): T? {
        val value = this
        return withContext(workerDispatcher) {
            try {
                work(value)
            } catch (e: Exception) {
                logError(e)
                null
            }
        }
    }

    @AnyThread
    suspend fun <T, V> V.ioWork(@WorkerThread work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(workerDispatcher) {
            work(value)
        }
    }

    @AnyThread
    suspend fun <T, V> V.mainWork(@MainThread work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(Dispatchers.Main) {
            work(value)
        }
    }

    @AnyThread
    fun runOnMainThread(@MainThread work: (() -> Unit)) {
        runOnMainThreadNative(work)
    }

    /**
     * Safe to add and remove how you want
     * If you want to iterate over the list then you need to do:
     * list.withLock { code here }
     */
    fun <T> atomicListOf(vararg items: T): AtomicMutableList<T> {
        return AtomicMutableList(items.toMutableList())
    }

    @Deprecated(
        message = "Use atomicListOf() instead.",
        replaceWith = ReplaceWith("atomicListOf(*items)"),
        level = DeprecationLevel.WARNING,
    )
    fun <T> threadSafeListOf(vararg items: T): MutableList<T> = atomicListOf(*items)
}
