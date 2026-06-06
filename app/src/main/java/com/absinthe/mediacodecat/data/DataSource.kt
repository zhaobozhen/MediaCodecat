package com.absinthe.mediacodecat.data

import android.content.Context
import com.absinthe.mediacodecat.model.VideoRecord

object DataSource {

    fun queryVideoRecords(context: Context): List<VideoRecord> {
        return context.contentResolver.query(
            VideoRecordContract.Records.CONTENT_URI,
            null,
            null,
            null,
            VIDEO_RECORD_SORT_ORDER
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(VideoRecord.fromCursor(cursor))
                }
            }
        }.orEmpty()
    }

    fun queryVideoRecordsPage(
        context: Context,
        limit: Int,
        offset: Int
    ): List<VideoRecord> {
        require(limit > 0) { "limit must be positive" }
        require(offset >= 0) { "offset must be non-negative" }

        val uri = VideoRecordContract.Records.CONTENT_URI.buildUpon()
            .appendQueryParameter(VideoRecordContract.Records.QUERY_PARAMETER_LIMIT, limit.toString())
            .appendQueryParameter(VideoRecordContract.Records.QUERY_PARAMETER_OFFSET, offset.toString())
            .build()

        return context.contentResolver.query(
            uri,
            null,
            null,
            null,
            VIDEO_RECORD_SORT_ORDER
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(VideoRecord.fromCursor(cursor))
                }
            }
        }.orEmpty()
    }

    fun queryVideoRecordCount(context: Context): Int {
        return context.contentResolver.query(
            VideoRecordContract.Records.CONTENT_URI,
            arrayOf("COUNT(*) AS $COUNT_COLUMN"),
            null,
            null,
            ""
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } ?: 0
    }

    fun deleteVideoRecord(
        context: Context,
        sessionId: String
    ): Boolean {
        return context.contentResolver.delete(
            videoRecordUri(sessionId),
            null,
            null
        ) > 0
    }

    fun restoreVideoRecord(
        context: Context,
        record: VideoRecord
    ): Boolean {
        return context.contentResolver.insert(
            VideoRecordContract.Records.CONTENT_URI,
            record.toContentValues()
        ) != null
    }

    private fun videoRecordUri(sessionId: String) =
        VideoRecordContract.Records.CONTENT_URI.buildUpon()
            .appendPath(sessionId)
            .build()

    private const val COUNT_COLUMN = "record_count"

    private const val VIDEO_RECORD_SORT_ORDER =
        "${VideoRecordContract.Records.LAST_SEEN_AT_MS} DESC"
}
