package com.absinthe.mediacodecat.manager

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

object SurfaceRegistry {
    private val map = ConcurrentHashMap<Surface, Item>()
    private val surfaceHolders = ConcurrentHashMap<SurfaceHolder, WeakReference<SurfaceView>>()
    private val surfaceTextures = ConcurrentHashMap<SurfaceTexture, TextureItem>()

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

    fun register(surface: Surface, parent: View, textureView: TextureView) {
        map.getOrPut(surface) { Item(parent = parent, textureView = textureView) }.apply {
            this.parent = parent
            this.textureView = textureView
        }
    }

    fun register(holder: SurfaceHolder, surfaceView: SurfaceView) {
        surfaceHolders[holder] = WeakReference(surfaceView)
    }

    fun register(surfaceTexture: SurfaceTexture, textureView: TextureView) {
        val item = surfaceTextures.getOrPut(surfaceTexture) { TextureItem(textureView = textureView) }
        item.textureView = textureView
        map.values.forEach { surfaceItem ->
            if (surfaceItem.surfaceTexture === surfaceTexture) {
                surfaceItem.textureView = textureView
            }
        }
    }

    fun register(surface: Surface, surfaceTexture: SurfaceTexture) {
        val textureItem = surfaceTextures.getOrPut(surfaceTexture) { TextureItem() }
        map.getOrPut(surface) { Item(surfaceTexture = surfaceTexture) }.apply {
            this.surfaceTexture = surfaceTexture
            textureItem.textureView?.let { this.textureView = it }
        }
    }

    fun setSurfaceTextureSize(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        surfaceTextures.getOrPut(surfaceTexture) { TextureItem() }.size = width to height
    }

    fun addTextView(surface: Surface, view: TextView) {
        map.getOrPut(surface) { Item(textView = view) }.textView = view
    }

    fun addTextViewToAll(view: TextView) {
        map.keys.forEach {
            addTextView(it, view)
        }
    }

    fun addContent(surface: Surface, content: String): Boolean {
        val item = map.getOrPut(surface) { Item(content = content) }
        val changed = item.content != content
        item.content = content
        return changed
    }

    fun unregister(surface: Surface) {
        findTextView(surface)?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        map.remove(surface)
    }

    fun unregister(surfaceTexture: SurfaceTexture) {
        surfaceTextures.remove(surfaceTexture)
        map.entries.removeIf { it.value.surfaceTexture === surfaceTexture }
    }

    fun unregister(surfaceView: SurfaceView) {
        surfaceHolders.entries.removeIf { it.value.get() === surfaceView }
        map.entries.removeIf { it.value.surfaceView === surfaceView }
    }

    fun unregister(textureView: TextureView) {
        surfaceTextures.entries.removeIf { it.value.textureView === textureView }
        map.values.forEach { item ->
            if (item.textureView === textureView) {
                item.textureView = null
            }
        }
    }

    fun findParentView(surface: Surface): View? = map[surface]?.parent
    fun findSurfaceView(surface: Surface): SurfaceView? = map[surface]?.surfaceView
    fun findSurfaceView(holder: SurfaceHolder): SurfaceView? = surfaceHolders[holder]?.get()

    fun findTextureView(surface: Surface): TextureView? {
        val item = map[surface] ?: return null
        return item.textureView ?: item.surfaceTexture?.let { surfaceTextures[it]?.textureView }
    }

    fun findSurfaceTextureSize(surface: Surface): Pair<Int, Int>? {
        val surfaceTexture = map[surface]?.surfaceTexture ?: return null
        return surfaceTextures[surfaceTexture]?.size
    }

    fun findTextView(surface: Surface): TextView? = map[surface]?.textView
    fun findTextView(): TextView? = map.values.firstNotNullOfOrNull { it.textView }

    fun findContent(surface: Surface): String? = map[surface]?.content
}

data class Item(
    var parent: View? = null,
    var surfaceView: SurfaceView? = null,
    var textureView: TextureView? = null,
    var surfaceTexture: SurfaceTexture? = null,
    var textView: TextView? = null,
    var content: String = ""
)

data class TextureItem(
    var textureView: TextureView? = null,
    var size: Pair<Int, Int>? = null
)
