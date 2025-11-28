package com.absinthe.mediacodecat.hook

import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.view.Surface
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog

object MediaCodecHook : YukiBaseHooker() {
    override fun onHook() {
        MediaCodec::class.resolve().firstMethod {
            name = "configure"
            parameters(
                MediaFormat::class,
                Surface::class,
                MediaCrypto::class,
                Class.forName("android.os.IHwBinder"),
                Int::class
            )
        }.hook {
            before {
                val format = args[0]
                val surface = args[1]
                (format as MediaFormat).apply {
                    YLog.info("format.keys=$keys")
                    YLog.info("format.features=${this.features}")
                }
                YLog.info("format=$format, surface=$surface")
            }
        }
    }
}