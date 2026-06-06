package com.absinthe.mediacodecat.data

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class VideoRecordReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            VideoRecordContract.Broadcast.ACTION_UPSERT -> persistRecord(context, intent)
            VideoRecordContract.Broadcast.ACTION_UPSERT_COVER -> persistCover(context, intent)
        }
    }

    private fun persistRecord(context: Context, intent: Intent) {
        val values = intent.recordValues() ?: run {
            Log.d(TAG, "Missing video record broadcast values")
            return
        }
        val sessionId = values.getAsString(VideoRecordContract.Records.SESSION_ID).orEmpty()

        runCatching {
            context.contentResolver.insert(VideoRecordContract.Records.CONTENT_URI, values)
        }.onSuccess { uri ->
            if (uri == null) {
                Log.d(TAG, "Skipped incomplete video record via receiver, session=$sessionId")
                return@onSuccess
            }
            if (loggedSessions.add(sessionId)) {
                Log.d(TAG, "Persisted video record via receiver, session=$sessionId")
            }
        }.onFailure { throwable ->
            Log.d(TAG, "Failed to persist video record via receiver, session=$sessionId", throwable)
        }
    }

    private fun persistCover(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(VideoRecordContract.Broadcast.EXTRA_COVER_SESSION_ID).orEmpty()
        val bytes = intent.getByteArrayExtra(VideoRecordContract.Broadcast.EXTRA_COVER_BYTES)
        if (sessionId.isBlank() || bytes == null || bytes.isEmpty()) {
            Log.d(TAG, "Missing video cover broadcast values")
            return
        }

        runCatching {
            VideoCoverStore.save(context, sessionId, bytes)
            context.contentResolver.notifyChange(VideoRecordContract.Records.CONTENT_URI, null)
        }.onSuccess {
            if (loggedCoverSessions.add(sessionId)) {
                Log.d(TAG, "Persisted video cover via receiver, session=$sessionId")
            }
        }.onFailure { throwable ->
            Log.d(TAG, "Failed to persist video cover via receiver, session=$sessionId", throwable)
        }
    }

    private fun Intent.recordValues(): ContentValues? {
        return getParcelableExtra(VideoRecordContract.Broadcast.EXTRA_VALUES, ContentValues::class.java)
    }

    companion object {
        private const val TAG = "MediaCodecat"

        private val loggedSessions = ConcurrentHashMap.newKeySet<String>()
        private val loggedCoverSessions = ConcurrentHashMap.newKeySet<String>()
    }
}
