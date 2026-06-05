package com.absinthe.mediacodecat

import com.absinthe.mediacodecat.hook.MediaCodecHook
import com.absinthe.mediacodecat.hook.MediaPlayerHook
import com.absinthe.mediacodecat.hook.SurfaceViewHook
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = BuildConfig.DEBUG
    }

    override fun onHook() = YukiHookAPI.encase {
        YLog.debug("onHook: processName=$processName, mainProcessName=$mainProcessName, packageName=$packageName")
        if (processName == packageName || packageName == "android" || packageName == "com.android.webview"||true) {
            loadApp(isExcludeSelf = true, MediaCodecHook)
            //loadApp(isExcludeSelf = true, SurfaceViewHook)
        }

//        loadApp(isExcludeSelf = true, MediaPlayerHook)
    }
}