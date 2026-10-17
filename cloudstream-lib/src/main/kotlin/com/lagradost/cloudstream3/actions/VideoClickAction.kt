package com.lagradost.cloudstream3.actions

/**
 * Lumora has no external-player actions. This minimal class only exists so extensions that register one still load
 * (the registered action is simply never shown).
 */
abstract class VideoClickAction {
    open val name: Any? = null
    open val oneSource: Boolean = false
    open val isPlayer: Boolean = false
    var sourcePlugin: String? = null
}

object VideoClickActionHolder {
    val allVideoClickActions: MutableList<VideoClickAction> = java.util.concurrent.CopyOnWriteArrayList()
}
