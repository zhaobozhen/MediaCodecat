package com.absinthe.mediacodecat.hook

import android.view.SurfaceHolder
import android.view.TextureView
import android.view.View
import com.absinthe.mediacodecat.manager.SurfaceHolderRegistry
import com.absinthe.mediacodecat.manager.SurfaceRegistry
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog

object TextureViewHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp {
            TextureView::class.resolve().firstMethod {
                name = "onAttachedToWindow"
            }.hook {
                after {
                    val view = instance<TextureView>()

                    val parentView = view.parent as? View ?: return@after
//                    SurfaceRegistry.register(view.setSurfaceTexture(), parentView)
//                    YLog.debug("SurfaceRegistry: surface=${holder.surface}, parent=$parentView")
                }
            }
        }
    }
}