package com.absinthe.mediacodecat

import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication

class App: ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        YLog.Configs.tag = "MediaCodecat"
    }
}