package com.absinthe.mediacodecat.model

import android.content.ContentValues
import android.database.Cursor
import android.media.MediaFormat
import com.absinthe.mediacodecat.data.VideoRecordContract
import org.json.JSONObject

data class VideoRecord(
    val schemaVersion: Int = SCHEMA_VERSION,
    val sessionId: String,
    val packageName: String,
    val processName: String,
    val codecName: String?,
    val mime: String,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val rotationDegrees: Int?,
    val colorFormat: Int?,
    val colorStandard: Int?,
    val colorRange: Int?,
    val colorTransfer: Int?,
    val profile: Int?,
    val level: Int?,
    val bitrateKbps: Int?,
    val surfaceId: String?,
    val mediaFormat: String,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long
) {

    fun toContentValues(): ContentValues = ContentValues().apply {
        put(VideoRecordContract.Records.SCHEMA_VERSION, schemaVersion)
        put(VideoRecordContract.Records.SESSION_ID, sessionId)
        put(VideoRecordContract.Records.PACKAGE_NAME, packageName)
        put(VideoRecordContract.Records.PROCESS_NAME, processName)
        put(VideoRecordContract.Records.CODEC_NAME, codecName)
        put(VideoRecordContract.Records.MIME, mime)
        put(VideoRecordContract.Records.WIDTH, width)
        put(VideoRecordContract.Records.HEIGHT, height)
        put(VideoRecordContract.Records.FRAME_RATE, frameRate)
        put(VideoRecordContract.Records.ROTATION_DEGREES, rotationDegrees)
        put(VideoRecordContract.Records.COLOR_FORMAT, colorFormat)
        put(VideoRecordContract.Records.COLOR_STANDARD, colorStandard)
        put(VideoRecordContract.Records.COLOR_RANGE, colorRange)
        put(VideoRecordContract.Records.COLOR_TRANSFER, colorTransfer)
        put(VideoRecordContract.Records.PROFILE, profile)
        put(VideoRecordContract.Records.LEVEL, level)
        put(VideoRecordContract.Records.BITRATE_KBPS, bitrateKbps)
        put(VideoRecordContract.Records.SURFACE_ID, surfaceId)
        put(VideoRecordContract.Records.MEDIA_FORMAT, mediaFormat)
        put(VideoRecordContract.Records.FIRST_SEEN_AT_MS, firstSeenAtMs)
        put(VideoRecordContract.Records.LAST_SEEN_AT_MS, lastSeenAtMs)
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun fromMediaFormat(
            sessionId: String,
            packageName: String,
            processName: String,
            codecName: String?,
            surfaceId: String?,
            format: MediaFormat,
            firstSeenAtMs: Long,
            lastSeenAtMs: Long = firstSeenAtMs,
            bitrateKbps: Int? = null
        ): VideoRecord {
            return VideoRecord(
                sessionId = sessionId,
                packageName = packageName,
                processName = processName,
                codecName = codecName,
                mime = format.optString(MediaFormat.KEY_MIME).orEmpty(),
                width = format.optInt(MediaFormat.KEY_WIDTH),
                height = format.optInt(MediaFormat.KEY_HEIGHT),
                frameRate = format.optFloat(MediaFormat.KEY_FRAME_RATE),
                rotationDegrees = format.optInt(MediaFormat.KEY_ROTATION),
                colorFormat = format.optInt(MediaFormat.KEY_COLOR_FORMAT),
                colorStandard = format.optInt(MediaFormat.KEY_COLOR_STANDARD),
                colorRange = format.optInt(MediaFormat.KEY_COLOR_RANGE),
                colorTransfer = format.optInt(MediaFormat.KEY_COLOR_TRANSFER),
                profile = format.optInt(MediaFormat.KEY_PROFILE),
                level = format.optInt(MediaFormat.KEY_LEVEL),
                bitrateKbps = bitrateKbps,
                surfaceId = surfaceId,
                mediaFormat = format.toStableJson(),
                firstSeenAtMs = firstSeenAtMs,
                lastSeenAtMs = lastSeenAtMs
            )
        }

        fun fromContentValues(values: ContentValues): VideoRecord = VideoRecord(
            schemaVersion = values.getAsInteger(VideoRecordContract.Records.SCHEMA_VERSION) ?: SCHEMA_VERSION,
            sessionId = requireNotNull(values.getAsString(VideoRecordContract.Records.SESSION_ID)),
            packageName = values.getAsString(VideoRecordContract.Records.PACKAGE_NAME).orEmpty(),
            processName = values.getAsString(VideoRecordContract.Records.PROCESS_NAME).orEmpty(),
            codecName = values.getAsString(VideoRecordContract.Records.CODEC_NAME),
            mime = values.getAsString(VideoRecordContract.Records.MIME).orEmpty(),
            width = values.getAsInteger(VideoRecordContract.Records.WIDTH),
            height = values.getAsInteger(VideoRecordContract.Records.HEIGHT),
            frameRate = values.getAsFloat(VideoRecordContract.Records.FRAME_RATE),
            rotationDegrees = values.getAsInteger(VideoRecordContract.Records.ROTATION_DEGREES),
            colorFormat = values.getAsInteger(VideoRecordContract.Records.COLOR_FORMAT),
            colorStandard = values.getAsInteger(VideoRecordContract.Records.COLOR_STANDARD),
            colorRange = values.getAsInteger(VideoRecordContract.Records.COLOR_RANGE),
            colorTransfer = values.getAsInteger(VideoRecordContract.Records.COLOR_TRANSFER),
            profile = values.getAsInteger(VideoRecordContract.Records.PROFILE),
            level = values.getAsInteger(VideoRecordContract.Records.LEVEL),
            bitrateKbps = values.getAsInteger(VideoRecordContract.Records.BITRATE_KBPS),
            surfaceId = values.getAsString(VideoRecordContract.Records.SURFACE_ID),
            mediaFormat = values.getAsString(VideoRecordContract.Records.MEDIA_FORMAT).orEmpty(),
            firstSeenAtMs = values.getAsLong(VideoRecordContract.Records.FIRST_SEEN_AT_MS) ?: 0L,
            lastSeenAtMs = values.getAsLong(VideoRecordContract.Records.LAST_SEEN_AT_MS) ?: 0L
        )

        fun fromCursor(cursor: Cursor): VideoRecord = VideoRecord(
            schemaVersion = cursor.getIntOrNull(VideoRecordContract.Records.SCHEMA_VERSION) ?: SCHEMA_VERSION,
            sessionId = cursor.getStringOrEmpty(VideoRecordContract.Records.SESSION_ID),
            packageName = cursor.getStringOrEmpty(VideoRecordContract.Records.PACKAGE_NAME),
            processName = cursor.getStringOrEmpty(VideoRecordContract.Records.PROCESS_NAME),
            codecName = cursor.getStringOrNull(VideoRecordContract.Records.CODEC_NAME),
            mime = cursor.getStringOrEmpty(VideoRecordContract.Records.MIME),
            width = cursor.getIntOrNull(VideoRecordContract.Records.WIDTH),
            height = cursor.getIntOrNull(VideoRecordContract.Records.HEIGHT),
            frameRate = cursor.getFloatOrNull(VideoRecordContract.Records.FRAME_RATE),
            rotationDegrees = cursor.getIntOrNull(VideoRecordContract.Records.ROTATION_DEGREES),
            colorFormat = cursor.getIntOrNull(VideoRecordContract.Records.COLOR_FORMAT),
            colorStandard = cursor.getIntOrNull(VideoRecordContract.Records.COLOR_STANDARD),
            colorRange = cursor.getIntOrNull(VideoRecordContract.Records.COLOR_RANGE),
            colorTransfer = cursor.getIntOrNull(VideoRecordContract.Records.COLOR_TRANSFER),
            profile = cursor.getIntOrNull(VideoRecordContract.Records.PROFILE),
            level = cursor.getIntOrNull(VideoRecordContract.Records.LEVEL),
            bitrateKbps = cursor.getIntOrNull(VideoRecordContract.Records.BITRATE_KBPS),
            surfaceId = cursor.getStringOrNull(VideoRecordContract.Records.SURFACE_ID),
            mediaFormat = cursor.getStringOrEmpty(VideoRecordContract.Records.MEDIA_FORMAT),
            firstSeenAtMs = cursor.getLongOrNull(VideoRecordContract.Records.FIRST_SEEN_AT_MS) ?: 0L,
            lastSeenAtMs = cursor.getLongOrNull(VideoRecordContract.Records.LAST_SEEN_AT_MS) ?: 0L
        )

        private fun MediaFormat.optString(key: String): String? = runCatching {
            if (containsKey(key)) getString(key) else null
        }.getOrNull()

        private fun MediaFormat.optInt(key: String): Int? = runCatching {
            if (containsKey(key)) getNumber(key)?.toInt() else null
        }.getOrNull()

        private fun MediaFormat.optFloat(key: String): Float? = runCatching {
            if (containsKey(key)) getNumber(key)?.toFloat() else null
        }.getOrNull()

        private fun MediaFormat.toStableJson(): String {
            val json = JSONObject()
            keys.sorted().forEach { key ->
                val value = runCatching {
                    when (getValueTypeForKey(key)) {
                        MediaFormat.TYPE_BYTE_BUFFER -> getByteBuffer(key)?.let { "ByteBuffer(${it.remaining()} bytes)" }
                        MediaFormat.TYPE_FLOAT -> getFloat(key).toDouble()
                        MediaFormat.TYPE_INTEGER -> getInteger(key)
                        MediaFormat.TYPE_LONG -> getLong(key)
                        MediaFormat.TYPE_NULL -> JSONObject.NULL
                        MediaFormat.TYPE_STRING -> getString(key)
                        else -> null
                    }
                }.getOrNull()
                json.put(key, value ?: JSONObject.NULL)
            }
            json.put("_raw", toString())
            return json.toString()
        }

        private fun Cursor.getStringOrNull(column: String): String? {
            val index = getColumnIndex(column)
            return if (index >= 0 && !isNull(index)) getString(index) else null
        }

        private fun Cursor.getStringOrEmpty(column: String): String = getStringOrNull(column).orEmpty()

        private fun Cursor.getIntOrNull(column: String): Int? {
            val index = getColumnIndex(column)
            return if (index >= 0 && !isNull(index)) getInt(index) else null
        }

        private fun Cursor.getLongOrNull(column: String): Long? {
            val index = getColumnIndex(column)
            return if (index >= 0 && !isNull(index)) getLong(index) else null
        }

        private fun Cursor.getFloatOrNull(column: String): Float? {
            val index = getColumnIndex(column)
            return if (index >= 0 && !isNull(index)) getFloat(index) else null
        }
    }
}
