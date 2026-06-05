package com.absinthe.mediacodecat.data

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class VideoRecordReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != VideoRecordContract.Broadcast.ACTION_UPSERT) return

        val values = intent.recordValues() ?: run {
            Log.d(TAG, "Missing video record broadcast values")
            return
        }
        val sessionId = values.getAsString(VideoRecordContract.Records.SESSION_ID).orEmpty()

        runCatching {
            context.contentResolver.insert(VideoRecordContract.Records.CONTENT_URI, values)
        }.onSuccess {
            if (loggedSessions.add(sessionId)) {
                Log.d(TAG, "Persisted video record via receiver, session=$sessionId")
            }
        }.onFailure { throwable ->
            Log.d(TAG, "Failed to persist video record via receiver, session=$sessionId", throwable)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.recordValues(): ContentValues? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(VideoRecordContract.Broadcast.EXTRA_VALUES, ContentValues::class.java)
        } else {
            getParcelableExtra(VideoRecordContract.Broadcast.EXTRA_VALUES)
        }
    }

    companion object {
        private const val TAG = "MediaCodecat"

        private val loggedSessions = ConcurrentHashMap.newKeySet<String>()
    }
}
