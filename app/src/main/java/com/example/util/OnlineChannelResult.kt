package com.example.util

/**
 * A channel/artist result for Online Music / Audio.
 */
data class OnlineChannelResult(
    val channelUrl: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCountText: String?,
    val videoCountText: String?,
    val description: String? = null
)
