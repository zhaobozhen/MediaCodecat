package com.absinthe.mediacodecat.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class HookSettingsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val appContext = context?.applicationContext ?: return null
        if (method != HookSettings.METHOD_GET_BOOLEAN) return super.call(method, arg, extras)

        return when (arg) {
            HookSettings.KEY_NATIVE_MEDIANDK_INLINE_HOOK -> HookSettings.buildBooleanResult(
                HookSettings.isNativeMediaNdkInlineHookEnabled(appContext)
            )
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
