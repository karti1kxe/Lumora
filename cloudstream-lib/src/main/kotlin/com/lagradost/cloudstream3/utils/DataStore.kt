package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.mapper

/** Key / value store used by extensions (same API as CloudStream's DataStore), kept in its own SharedPreferences file. */
object DataStore {
    private const val PREFERENCES_NAME = "cloudstream_ext_store"

    fun Context.getSharedPrefs(): SharedPreferences =
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    fun Context.getKeys(folder: String): List<String> {
        val fixedFolder = folder.trimEnd('/') + "/"
        return getSharedPrefs().all.keys.filter { it.startsWith(fixedFolder) }
    }

    fun Context.removeKey(folder: String, path: String) = removeKey(getFolderName(folder, path))

    fun Context.containsKey(folder: String, path: String): Boolean = containsKey(getFolderName(folder, path))

    fun Context.containsKey(path: String): Boolean = getSharedPrefs().contains(path)

    fun Context.removeKey(path: String) {
        try {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) prefs.edit().remove(path).apply()
        } catch (_: Throwable) {
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        try {
            getSharedPrefs().edit().also { e -> keys.forEach { e.remove(it) } }.apply()
            return keys.size
        } catch (_: Throwable) {
            return 0
        }
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            getSharedPrefs().edit().putString(path, mapper.writeValueAsString(value)).apply()
        } catch (_: Throwable) {
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) = setKey(getFolderName(folder, path), value)

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? {
        return try {
            val json: String = getSharedPrefs().getString(path, null) ?: return null
            mapper.readValue(json, valueType)
        } catch (_: Throwable) {
            null
        }
    }

    inline fun <reified T : Any> String.toKotlinObject(): T = mapper.readValue(this, T::class.java)

    fun <T : Any> String.toKotlinObject(valueType: Class<T>): T = mapper.readValue(this, valueType)

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        return try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            json.toKotlinObject()
        } catch (_: Throwable) {
            defVal
        }
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? = getKey(path, null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? =
        getKey(getFolderName(folder, path), null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKey(getFolderName(folder, path), defVal) ?: defVal
}
