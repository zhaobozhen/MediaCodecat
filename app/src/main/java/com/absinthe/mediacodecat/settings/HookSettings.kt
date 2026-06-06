package com.absinthe.mediacodecat.settings

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.absinthe.mediacodecat.BuildConfig

object HookSettings {
    const val METHOD_GET_BOOLEAN = "get_boolean"
    const val EXTRA_VALUE = "value"

    private val authority = "${BuildConfig.APPLICATION_ID}.settings"
    private const val PREFERENCES_NAME = "hook_settings"
    const val KEY_NATIVE_MEDIANDK_INLINE_HOOK = "native_mediandk_inline_hook"

    val contentUri: Uri = Uri.parse("content://$authority")

    fun isNativeMediaNdkInlineHookEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NATIVE_MEDIANDK_INLINE_HOOK, false)
    }

    fun setNativeMediaNdkInlineHookEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NATIVE_MEDIANDK_INLINE_HOOK, enabled)
            .apply()
    }

    fun queryNativeMediaNdkInlineHookEnabled(context: Context): Boolean? {
        return runCatching {
            context.contentResolver.call(
                contentUri,
                METHOD_GET_BOOLEAN,
                KEY_NATIVE_MEDIANDK_INLINE_HOOK,
                null
            )?.takeIf { it.containsKey(EXTRA_VALUE) }
                ?.getBoolean(EXTRA_VALUE)
        }.getOrNull()
    }

    fun buildBooleanResult(value: Boolean): Bundle {
        return Bundle().apply {
            putBoolean(EXTRA_VALUE, value)
        }
    }
}
