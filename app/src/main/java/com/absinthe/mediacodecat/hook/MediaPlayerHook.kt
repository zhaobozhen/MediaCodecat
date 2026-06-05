package com.absinthe.mediacodecat.hook

import android.media.MediaPlayer
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog

object MediaPlayerHook : YukiBaseHooker() {
    override fun onHook() {
        loadApp {
            MediaPlayer::class.resolve().firstMethod {
                name = "setDataSource"
                parameters(
                    String::class,
                    Array<String>::class,
                    Array<String>::class,
                    List::class
                )
            }.hook {
                before {
                    val path = args[0]
                    val keys = args[1]
                    val values = args[2]
                    YLog.info("path=$path")
                    if (keys is Array<*> && keys.isArrayOf<String>()) {
                        YLog.info("keys=${keys.contentToString()}")
                    }
                    if (values is Array<*> && values.isArrayOf<String>()) {
                        YLog.info("values=${values.contentToString()}")
                    }
                }
            }
        }
    }
}