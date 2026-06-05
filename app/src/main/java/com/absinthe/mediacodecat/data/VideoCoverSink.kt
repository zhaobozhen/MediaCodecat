package com.absinthe.mediacodecat.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object VideoCoverSink {
    fun upsert(
        context: Context,
        sessionId: String,
        bytes: ByteArray,
        mimeType: String,
        width: Int,
        height: Int,
        capturedAtMs: Long = System.currentTimeMillis()
    ): Boolean {
        return runCatching {
            val intent = Intent(VideoRecordContract.Broadcast.ACTION_UPSERT_COVER)
                .setComponent(
                    ComponentName(
                        VideoRecordContract.MODULE_PACKAGE,
                        VideoRecordContract.Broadcast.RECEIVER_CLASS
                    )
                )
                .putExtra(VideoRecordContract.Broadcast.EXTRA_COVER_SESSION_ID, sessionId)
                .putExtra(VideoRecordContract.Broadcast.EXTRA_COVER_BYTES, bytes)
                .putExtra(VideoRecordContract.Broadcast.EXTRA_COVER_MIME_TYPE, mimeType)
                .putExtra(VideoRecordContract.Broadcast.EXTRA_COVER_WIDTH, width)
                .putExtra(VideoRecordContract.Broadcast.EXTRA_COVER_HEIGHT, height)
                .putExtra(VideoRecordContract.Broadcast.EXTRA_COVER_CAPTURED_AT_MS, capturedAtMs)
            context.sendBroadcast(intent)
            true
        }.onFailure { throwable ->
            Log.d(TAG, "Failed to send video cover broadcast, session=$sessionId", throwable)
        }.getOrDefault(false)
    }

    private const val TAG = "MediaCodecat"
}
