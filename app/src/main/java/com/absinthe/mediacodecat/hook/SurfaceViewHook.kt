package com.absinthe.mediacodecat.hook

import android.graphics.Color
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
                            SurfaceRegistry.register(holder.surface, parentView, view)
                            YLog.debug("SurfaceRegistry: surface=${holder.surface}, parent=$parentView")

                            parentView.post {
                                (parentView as ViewGroup).apply {
                                    val textView = TextView(context).apply {
                                        layoutParams = ViewGroup.MarginLayoutParams(400, 300).also {
                                            it.topMargin = 48
                                            it.marginStart = 48
                                        }
                                        setPadding(24, 24, 24, 24)
                                        setBackgroundColor(Color.parseColor("#66000000"))
                                        setTextColor(Color.WHITE)
                                    }
                                    textView.text = SurfaceRegistry.findContent(holder.surface)
                                    addView(textView)
                                    SurfaceRegistry.addTextView(holder.surface, textView)

                                    SurfaceRegistry.addTextViewToAll(textView)

                                }
                            }
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
