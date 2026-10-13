package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A channel the user subscribed to inside the app (local only - nothing is sent to YouTube). */
data class SubscribedChannel(
    val channelUrl: String,
    val name: String,
    val avatarUrl: String?,
    val subscribedAt: Long
)

/**
 * Local "Subscribe" list for Online channels. The Subscribe button on the channel card / channel screen writes
 * here, and [OnlineVideoRecommender] reads it to put the newest uploads of subscribed channels in the Online feed.
 */
object SubscribedChannelsManager {
    private const val PREFS = "lumora_subscribed_channels"
    private const val KEY_LIST = "channels"
    private const val MAX_CHANNELS = 200

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(c: Context): List<SubscribedChannel> {
        val arr = try { JSONArray(prefs(c).getString(KEY_LIST, "[]") ?: "[]") } catch (_: Throwable) { JSONArray() }
        val out = ArrayList<SubscribedChannel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            out.add(
                SubscribedChannel(
                    channelUrl = url,
                    name = o.optString("name"),
                    avatarUrl = o.optString("avatar").takeIf { it.isNotBlank() },
                    subscribedAt = o.optLong("at", 0L)
                )
            )
        }
        return out
    }

    fun isSubscribed(c: Context, channelUrl: String): Boolean =
        channelUrl.isNotBlank() && getAll(c).any { it.channelUrl == channelUrl }

    @Synchronized
    fun setSubscribed(c: Context, channelUrl: String, name: String, avatarUrl: String?, subscribed: Boolean) {
        if (channelUrl.isBlank()) return
        val list = getAll(c).filter { it.channelUrl != channelUrl }.toMutableList()
        if (subscribed) {
            list.add(0, SubscribedChannel(channelUrl, name, avatarUrl, System.currentTimeMillis()))
        }
        val arr = JSONArray()
        list.take(MAX_CHANNELS).forEach {
            arr.put(
                JSONObject()
                    .put("url", it.channelUrl)
                    .put("name", it.name)
                    .put("avatar", it.avatarUrl ?: "")
                    .put("at", it.subscribedAt)
            )
        }
        prefs(c).edit().putString(KEY_LIST, arr.toString()).apply()
    }
}
