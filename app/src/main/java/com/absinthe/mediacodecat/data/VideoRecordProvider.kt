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
        pruneIncompleteRecords()
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
        val limit = uri.limitClause()
        val cursor = when (MATCHER.match(uri)) {
            MATCH_RECORDS -> db.query(
                VideoRecordContract.Records.TABLE,
                projection,
                visibleRecordSelection(selection),
                selectionArgs,
                null,
                null,
                sortOrder ?: DEFAULT_SORT_ORDER,
                limit
            )

            MATCH_RECORD -> db.query(
                VideoRecordContract.Records.TABLE,
                projection,
                visibleRecordSelection("${VideoRecordContract.Records.SESSION_ID} = ?"),
                arrayOf(requireSessionId(uri)),
                null,
                null,
                sortOrder ?: DEFAULT_SORT_ORDER,
                limit
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
        if (!record.isVisibleRecord()) return null

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

        if (!record.isVisibleRecord()) return 0

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

    private fun pruneIncompleteRecords() {
        database.writableDatabase.delete(
            VideoRecordContract.Records.TABLE,
            INCOMPLETE_METRICS_SELECTION,
            null
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

        private const val COMPLETE_METRICS_SELECTION =
            "${VideoRecordContract.Records.BITRATE_KBPS} > 0 AND ${VideoRecordContract.Records.FRAME_RATE} > 0"

        private const val FALLBACK_SURFACE_SELECTION =
            "${VideoRecordContract.Records.MIME} = '${VideoRecordContract.Records.FALLBACK_SURFACE_MIME}' AND " +
                "${VideoRecordContract.Records.WIDTH} > 0 AND ${VideoRecordContract.Records.HEIGHT} > 0"

        private const val NATIVE_CODEC_SELECTION =
            "${VideoRecordContract.Records.CODEC_NAME} LIKE '${VideoRecordContract.Records.NATIVE_CODEC_NAME_PREFIX}%' AND " +
                "${VideoRecordContract.Records.MIME} LIKE 'video/%' AND " +
                "${VideoRecordContract.Records.WIDTH} > 0 AND ${VideoRecordContract.Records.HEIGHT} > 0"

        private const val VISIBLE_RECORD_SELECTION =
            "($COMPLETE_METRICS_SELECTION) OR ($FALLBACK_SURFACE_SELECTION) OR ($NATIVE_CODEC_SELECTION)"

        private const val INCOMPLETE_METRICS_SELECTION =
            "NOT ($VISIBLE_RECORD_SELECTION)"

        private fun visibleRecordSelection(selection: String?): String {
            return if (selection.isNullOrBlank()) {
                VISIBLE_RECORD_SELECTION
            } else {
                "($selection) AND ($VISIBLE_RECORD_SELECTION)"
            }
        }

        private fun VideoRecord.isVisibleRecord(): Boolean {
            return hasRequiredMetrics() ||
                mime == VideoRecordContract.Records.FALLBACK_SURFACE_MIME &&
                width?.let { it > 0 } == true &&
                height?.let { it > 0 } == true ||
                codecName?.startsWith(VideoRecordContract.Records.NATIVE_CODEC_NAME_PREFIX) == true &&
                mime.startsWith("video/") &&
                width?.let { it > 0 } == true &&
                height?.let { it > 0 } == true
        }

        private fun Uri.limitClause(): String? {
            val rawLimit = getQueryParameter(VideoRecordContract.Records.QUERY_PARAMETER_LIMIT) ?: return null
            val limit = rawLimit.toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Invalid record query limit: $rawLimit")
            val rawOffset = getQueryParameter(VideoRecordContract.Records.QUERY_PARAMETER_OFFSET)
            val offset = rawOffset?.toIntOrNull()?.takeIf { it >= 0 }
                ?: if (rawOffset == null) 0
                else throw IllegalArgumentException("Invalid record query offset: $rawOffset")

            return if (offset > 0) "$limit OFFSET $offset" else limit.toString()
        }

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(VideoRecordContract.AUTHORITY, VideoRecordContract.Records.PATH, MATCH_RECORDS)
            addURI(VideoRecordContract.AUTHORITY, "${VideoRecordContract.Records.PATH}/*", MATCH_RECORD)
        }
    }
}
