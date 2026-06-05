package com.absinthe.mediacodecat.manager

import android.view.SurfaceHolder
import android.view.SurfaceView

object SurfaceHolderRegistry {
    private val map = mutableMapOf<SurfaceHolder, SurfaceView>()

    fun register(holder: SurfaceHolder, surface: SurfaceView) {
        map[holder] = surface
    }

    fun unregister(holder: SurfaceHolder) {
        map.remove(holder)
    }

    fun findView(holder: SurfaceHolder): SurfaceView? = map[holder]
}