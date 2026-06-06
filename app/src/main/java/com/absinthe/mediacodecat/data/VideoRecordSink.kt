package com.absinthe.mediacodecat.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.absinthe.mediacodecat.model.VideoRecord
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object VideoRecordSink {
    fun upsert(context: Context, record: VideoRecord, requireMetrics: Boolean = true): Boolean {
        if (requireMetrics && !record.hasRequiredMetrics()) return true

        if (!useBroadcastFallback.get()) {
            val providerResult = tryProviderUpsert(context, record)
            if (providerResult.getOrDefault(false)) return true

            val throwable = providerResult.exceptionOrNull()
            if (throwable != null && !isUnknownProvider(throwable)) {
                logFailure("Failed to persist video record", record, throwable)
                return false
            }

            if (providerUnavailableLogged.compareAndSet(false, true)) {
                val message = if (throwable == null) {
                    "Provider insert returned null; falling back to explicit broadcast"
                } else {
                    "Provider is not visible from this process; falling back to explicit broadcast"
                }
                Log.d(TAG, "$message, session=${record.sessionId}", throwable)
            }
            useBroadcastFallback.set(true)
        }

        return sendFallbackBroadcast(context, record)
    }

    private const val TAG = "MediaCodecat"

    private val useBroadcastFallback = AtomicBoolean(false)
    private val providerUnavailableLogged = AtomicBoolean(false)
    private val failedSessions = ConcurrentHashMap.newKeySet<String>()

    private fun tryProviderUpsert(context: Context, record: VideoRecord): Result<Boolean> {
        return runCatching {
            context.contentResolver.insert(
                VideoRecordContract.Records.CONTENT_URI,
                record.toContentValues()
            ) != null
        }
    }

    private fun sendFallbackBroadcast(context: Context, record: VideoRecord): Boolean {
        return runCatching {
            val intent = Intent(VideoRecordContract.Broadcast.ACTION_UPSERT)
                .setComponent(
                    ComponentName(
                        VideoRecordContract.MODULE_PACKAGE,
                        VideoRecordContract.Broadcast.RECEIVER_CLASS
                    )
                )
                .putExtra(VideoRecordContract.Broadcast.EXTRA_VALUES, record.toContentValues())
            context.sendBroadcast(intent)
            true
        }.onFailure { throwable ->
            logFailure("Failed to send video record broadcast", record, throwable)
        }.getOrDefault(false)
    }

    private fun isUnknownProvider(throwable: Throwable): Boolean {
        return throwable is IllegalArgumentException &&
            throwable.message?.contains("Unknown URL") == true
    }

    private fun logFailure(message: String, record: VideoRecord, throwable: Throwable) {
        if (failedSessions.add(record.sessionId)) {
            Log.d(TAG, "$message, session=${record.sessionId}", throwable)
        }
    }
}
