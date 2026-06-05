package com.absinthe.mediacodecat

import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.tencent.mmkv.MMKV

class App: ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        YLog.Configs.tag = TAG
        MMKV.initialize(this)
    }

    companion object {
        const val TAG = "MediaCodecat"
    }
}