package com.absinthe.mediacodecat.manager

import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object SurfaceRegistry {
    private const val TAG = "MediaCodecat"
    private val map = ConcurrentHashMap<Surface, Item>()

    fun hasSurface(surface: Surface) = map.keys.contains(surface)

    fun register(surface: Surface, parent: View) {
        map.getOrPut(surface) { Item(parent = parent) }.parent = parent
    }

    fun register(surface: Surface, parent: View, surfaceView: SurfaceView) {
        map.getOrPut(surface) { Item(parent = parent, surfaceView = surfaceView) }.apply {
            this.parent = parent
            this.surfaceView = surfaceView
        }
    }

    fun addTextView(surface: Surface, view: TextView) {
        map.getOrPut(surface) { Item(textView = view) }.textView = view
    }

    fun addTextViewToAll(view: TextView) {
        map.keys.forEach {
            Log.d(TAG, "SurfaceRegistry: addTextViewToAll, surface=$it")
            addTextView(it, view)
        }
    }

    fun addContent(surface: Surface, content: String) {
        Log.d(TAG, "SurfaceRegistry: addContent, surface=$surface, content=$content")
        map.getOrPut(surface) { Item(content = content) }.content = content
        Log.d(TAG, "SurfaceRegistry: content=${map[surface]?.content}")

    }

    fun unregister(surface: Surface) {
        findTextView(surface)?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        map.remove(surface)
    }

    fun findParentView(surface: Surface): View? = map[surface]?.parent
    fun findSurfaceView(surface: Surface): SurfaceView? = map[surface]?.surfaceView

    fun findTextView(surface: Surface): TextView? = map[surface]?.textView
    fun findTextView(): TextView? = map.values.firstOrNull()?.textView

    fun findContent(surface: Surface): String? = map[surface]?.content
}

data class Item(
    var parent: View? = null,
    var surfaceView: SurfaceView? = null,
    var textView: TextView? = null,
    var content: String = ""
)
