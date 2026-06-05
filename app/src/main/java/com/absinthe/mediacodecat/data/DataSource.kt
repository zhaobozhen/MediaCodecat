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
            "${VideoRecordContract.Records.LAST_SEEN_AT_MS} DESC"
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(VideoRecord.fromCursor(cursor))
                }
            }
        }.orEmpty()
    }
}
