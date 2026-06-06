package com.absinthe.mediacodecat

import android.util.Log
import androidx.annotation.Keep
import com.absinthe.mediacodecat.hook.MediaCodecHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.atomic.AtomicBoolean

@Keep
class HookEntry : XposedModule() {
    private val hookInstalled = AtomicBoolean(false)
    private var processName: String = "unknown"

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        log(Log.INFO, App.TAG, "module loaded, process=$processName")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val packageName = param.packageName
        if (packageName == BuildConfig.APPLICATION_ID) return
        if (!hookInstalled.compareAndSet(false, true)) return

        MediaCodecHook.install(
            module = this,
            packageName = packageName,
            processName = processName
        )
    }
}
