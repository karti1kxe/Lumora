package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder

/** Same shape as CloudStream's app `Plugin` class: extensions (.cs3) extend this one. */
abstract class Plugin : BasePlugin() {
    /** Called when your Plugin is loaded */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        // If not overridden by an extension then try the cross-platform load()
        load()
    }

    /** Used to register VideoClickAction instances */
    fun registerVideoClickAction(element: VideoClickAction) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} VideoClickAction")
        element.sourcePlugin = this.filename
        VideoClickActionHolder.allVideoClickActions.add(element)
    }

    /** This will contain your resources if you specified requiresResources in gradle */
    var resources: Resources? = null

    /** This will add a button in the settings allowing you to add custom settings */
    var openSettings: ((context: Context) -> Unit)? = null
}
