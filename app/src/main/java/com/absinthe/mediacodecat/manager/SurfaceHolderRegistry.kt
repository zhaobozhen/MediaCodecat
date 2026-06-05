package com.absinthe.mediacodecat.manager

import android.view.Surface
import android.view.View

object SurfaceRegistry {
    private val map = mutableMapOf<Surface, View>()

    fun register(surface: Surface, view: View) {
        map[surface] = view
    }

    fun unregister(surface: Surface) {
        map.remove(surface)
    }

    fun findView(surface: Surface): View? = map[surface]
}