package com.absinthe.mediacodecat.hook

import androidx.annotation.Keep

@Keep
object NativeMediaCodecBridge {
    @JvmStatic
    fun mediaNdkInlineHookState(): Int {
        return MediaCodecHook.mediaNdkInlineHookState()
    }

    @JvmStatic
    fun refreshMediaNdkInlineHooks() {
        runCatching {
            refreshMediaNdkInlineHooksNative()
        }
    }

    @JvmStatic
    private external fun refreshMediaNdkInlineHooksNative()

    @JvmStatic
    fun onConfigured(
        codecPtr: Long,
        codecName: String?,
        mime: String?,
        width: Int,
        height: Int,
        flags: Int,
        nativeWindowPtr: Long
    ) {
        MediaCodecHook.onNativeConfigured(
            codecPtr = codecPtr,
            codecName = codecName,
            mime = mime,
            width = width,
            height = height,
            flags = flags,
            nativeWindowPtr = nativeWindowPtr
        )
    }

    @JvmStatic
    fun onInputBufferQueued(codecPtr: Long, size: Int, presentationTimeUs: Long) {
        MediaCodecHook.onNativeInputBufferQueued(
            codecPtr = codecPtr,
            size = size,
            presentationTimeUs = presentationTimeUs
        )
    }

    @JvmStatic
    fun onOutputBufferReleased(codecPtr: Long, render: Boolean) {
        MediaCodecHook.onNativeOutputBufferReleased(
            codecPtr = codecPtr,
            render = render
        )
    }

    @JvmStatic
    fun onDeleted(codecPtr: Long) {
        MediaCodecHook.onNativeDeleted(codecPtr)
    }
}
