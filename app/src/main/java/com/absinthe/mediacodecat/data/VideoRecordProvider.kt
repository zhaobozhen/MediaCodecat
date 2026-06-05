package com.absinthe.mediacodecat.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.absinthe.mediacodecat.model.VideoRecord

class VideoRecordProvider : ContentProvider() {

    private lateinit var database: VideoRecordDatabase

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        database = VideoRecordDatabase(appContext)
        return true
    }

    override fun getType(uri: Uri): String? = when (MATCHER.match(uri)) {
        MATCH_RECORDS -> VideoRecordContract.Records.CONTENT_TYPE
        MATCH_RECORD -> VideoRecordContract.Records.CONTENT_ITEM_TYPE
        else -> null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val db = database.readableDatabase
        val cursor = when (MATCHER.match(uri)) {
            MATCH_RECORDS -> db.query(
                VideoRecordContract.Records.TABLE,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder ?: DEFAULT_SORT_ORDER
            )

            MATCH_RECORD -> db.query(
                VideoRecordContract.Records.TABLE,
                projection,
                "${VideoRecordContract.Records.SESSION_ID} = ?",
                arrayOf(requireSessionId(uri)),
                null,
                null,
                sortOrder ?: DEFAULT_SORT_ORDER
            )

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (MATCHER.match(uri) != MATCH_RECORDS) {
            throw IllegalArgumentException("Unsupported insert URI: $uri")
        }

        val record = VideoRecord.fromContentValues(requireNotNull(values))
        upsert(record)
        return notifyAndBuildRecordUri(record.sessionId)
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val incoming = requireNotNull(values)
        val record = when (MATCHER.match(uri)) {
            MATCH_RECORDS -> VideoRecord.fromContentValues(incoming)
            MATCH_RECORD -> VideoRecord.fromContentValues(
                ContentValues(incoming).apply {
                    put(VideoRecordContract.Records.SESSION_ID, requireSessionId(uri))
                }
            )

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }

        upsert(record)
        notify(uri)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = database.writableDatabase
        val count = when (MATCHER.match(uri)) {
            MATCH_RECORDS -> db.delete(VideoRecordContract.Records.TABLE, selection, selectionArgs)
            MATCH_RECORD -> db.delete(
                VideoRecordContract.Records.TABLE,
                "${VideoRecordContract.Records.SESSION_ID} = ?",
                arrayOf(requireSessionId(uri))
            )

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        if (count > 0) notify(uri)
        return count
    }

    private fun upsert(record: VideoRecord) {
        database.writableDatabase.insertWithOnConflict(
            VideoRecordContract.Records.TABLE,
            null,
            record.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun notifyAndBuildRecordUri(sessionId: String): Uri {
        val uri = VideoRecordContract.Records.CONTENT_URI.buildUpon()
            .appendPath(sessionId)
            .build()
        notify(VideoRecordContract.Records.CONTENT_URI)
        notify(uri)
        return uri
    }

    private fun notify(uri: Uri) {
        context?.contentResolver?.notifyChange(uri, null)
    }

    private fun requireSessionId(uri: Uri): String = requireNotNull(uri.lastPathSegment) {
        "Missing record session id in URI: $uri"
    }

    companion object {
        private const val MATCH_RECORDS = 1
        private const val MATCH_RECORD = 2

        private const val DEFAULT_SORT_ORDER =
            "${VideoRecordContract.Records.LAST_SEEN_AT_MS} DESC"

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(VideoRecordContract.AUTHORITY, VideoRecordContract.Records.PATH, MATCH_RECORDS)
            addURI(VideoRecordContract.AUTHORITY, "${VideoRecordContract.Records.PATH}/*", MATCH_RECORD)
        }
    }
}
