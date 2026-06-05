package com.absinthe.mediacodecat.hook

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.absinthe.mediacodecat.manager.SurfaceHolderRegistry
import com.absinthe.mediacodecat.manager.SurfaceRegistry
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog

object SurfaceViewHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp {
            SurfaceView::class.resolve().firstMethod {
                name = "onAttachedToWindow"
            }.hook {
                after {
                    val view = instance<SurfaceView>()
                    val holder = view.holder

                    SurfaceHolderRegistry.register(holder, view)

                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            val parentView = view.parent as? View ?: return
                            SurfaceRegistry.register(holder.surface, parentView)
                            YLog.debug("SurfaceRegistry: surface=${holder.surface}, parent=$parentView")
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            SurfaceRegistry.unregister(holder.surface)
                            SurfaceHolderRegistry.unregister(holder)
                        }
                    })
                }
            }
        }
    }
}