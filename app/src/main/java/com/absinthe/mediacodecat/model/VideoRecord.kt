package com.absinthe.mediacodecat.model

import android.content.ContentValues
import android.database.Cursor
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import com.absinthe.mediacodecat.data.VideoRecordContract
import java.util.Locale
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

    fun hasRequiredMetrics(): Boolean {
        return bitrateKbps?.let { it > 0 } == true &&
            frameRate?.let { it.isFinite() && it > 0f } == true
    }

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
            bitrateKbps: Int? = null,
            estimatedFrameRate: Float? = null
        ): VideoRecord {
            val rotationDegrees = format.optRotationDegrees()
            val displaySize = format.displaySize(rotationDegrees)
            val recordBitrateKbps = bitrateKbps.validPositive()
                ?: format.optBitrateKbps(MediaFormat.KEY_BIT_RATE)
            val recordFrameRate = format.optPositiveFloat(MediaFormat.KEY_FRAME_RATE)
                ?: estimatedFrameRate.validFrameRate()
            val mime = format.optString(MediaFormat.KEY_MIME).orEmpty()
            val codecProfileLevel = format.codecProfileLevel(mime)
            val recordProfile = codecProfileLevel?.first
                ?: format.optCodecProfileInt(mime, MediaFormat.KEY_PROFILE)
            val recordLevel = codecProfileLevel?.second
                ?: format.optCodecProfileInt(mime, MediaFormat.KEY_LEVEL)

            return VideoRecord(
                sessionId = sessionId,
                packageName = packageName,
                processName = processName,
                codecName = codecName,
                mime = mime,
                width = displaySize?.first,
                height = displaySize?.second,
                frameRate = recordFrameRate,
                rotationDegrees = rotationDegrees,
                colorFormat = format.optPositiveInt(MediaFormat.KEY_COLOR_FORMAT),
                colorStandard = format.optPositiveInt(MediaFormat.KEY_COLOR_STANDARD),
                colorRange = format.optPositiveInt(MediaFormat.KEY_COLOR_RANGE),
                colorTransfer = format.optPositiveInt(MediaFormat.KEY_COLOR_TRANSFER),
                profile = recordProfile,
                level = recordLevel,
                bitrateKbps = recordBitrateKbps,
                surfaceId = surfaceId,
                mediaFormat = format.toStableJson(
                    displayWidth = displaySize?.first,
                    displayHeight = displaySize?.second,
                    bitrateKbps = recordBitrateKbps,
                    frameRate = recordFrameRate
                ),
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

        private fun MediaFormat.optPositiveInt(key: String): Int? = optInt(key).validPositive()

        private fun MediaFormat.optCodecProfileInt(mime: String, key: String): Int? {
            if (mime.normalizedMime() == MediaFormat.MIMETYPE_VIDEO_RAW) return null
            return optPositiveInt(key)
        }

        private fun Int?.validPositive(): Int? = takeIf { it != null && it > 0 }

        private fun String.normalizedMime(): String = substringBefore(';').trim().lowercase(Locale.ROOT)

        private fun MediaFormat.optFloat(key: String): Float? = runCatching {
            if (containsKey(key)) getNumber(key)?.toFloat() else null
        }.getOrNull()

        private fun MediaFormat.optPositiveFloat(key: String): Float? = optFloat(key).validFrameRate()

        private fun Float?.validFrameRate(): Float? {
            return takeIf { it != null && it.isFinite() && it > 0f && it <= 1000f }
        }

        private fun MediaFormat.optLong(key: String): Long? = runCatching {
            if (containsKey(key)) getNumber(key)?.toLong() else null
        }.getOrNull()

        private fun MediaFormat.optBitrateKbps(key: String): Int? {
            val bps = optLong(key)?.takeIf { it > 0 } ?: return null
            return (bps / 1000L).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        private fun MediaFormat.codecProfileLevel(mime: String): Pair<Int?, Int?>? {
            return when (mime.normalizedMime()) {
                MediaFormat.MIMETYPE_VIDEO_AVC -> avcProfileLevelFromCsd()
                else -> null
            }
        }

        private fun MediaFormat.avcProfileLevelFromCsd(): Pair<Int?, Int?>? {
            val bytes = optByteBufferBytes("csd-0") ?: return null
            val header = avcSpsHeader(bytes) ?: return null
            val profile = avcProfileFromIdc(header.profileIdc, header.constraints)
            val level = avcLevelFromIdc(header.levelIdc, header.constraints)
            return if (profile != null || level != null) profile to level else null
        }

        private fun MediaFormat.optByteBufferBytes(key: String): ByteArray? = runCatching {
            if (!containsKey(key)) return@runCatching null
            val buffer = getByteBuffer(key)?.duplicate() ?: return@runCatching null
            ByteArray(buffer.remaining()).also(buffer::get)
        }.getOrNull()

        private fun avcSpsHeader(bytes: ByteArray): AvcSpsHeader? {
            if (bytes.size >= 4 && bytes[0].unsigned() == 1) {
                return AvcSpsHeader(
                    profileIdc = bytes[1].unsigned(),
                    constraints = bytes[2].unsigned(),
                    levelIdc = bytes[3].unsigned()
                )
            }

            val spsPayloadOffset = bytes.avcSpsPayloadOffset() ?: return null
            if (spsPayloadOffset + 2 >= bytes.size) return null
            return AvcSpsHeader(
                profileIdc = bytes[spsPayloadOffset].unsigned(),
                constraints = bytes[spsPayloadOffset + 1].unsigned(),
                levelIdc = bytes[spsPayloadOffset + 2].unsigned()
            )
        }

        private fun ByteArray.avcSpsPayloadOffset(): Int? {
            var index = 0
            while (index < size - 4) {
                val startCodeLength = startCodeLengthAt(index)
                if (startCodeLength > 0) {
                    val nalOffset = index + startCodeLength
                    if (nalOffset < size && (this[nalOffset].unsigned() and 0x1F) == 7) {
                        return nalOffset + 1
                    }
                    index = nalOffset
                } else {
                    index++
                }
            }
            return if (isNotEmpty() && (this[0].unsigned() and 0x1F) == 7) 1 else null
        }

        private fun ByteArray.startCodeLengthAt(index: Int): Int {
            return when {
                index + 3 < size &&
                    this[index].unsigned() == 0 &&
                    this[index + 1].unsigned() == 0 &&
                    this[index + 2].unsigned() == 0 &&
                    this[index + 3].unsigned() == 1 -> 4

                index + 2 < size &&
                    this[index].unsigned() == 0 &&
                    this[index + 1].unsigned() == 0 &&
                    this[index + 2].unsigned() == 1 -> 3

                else -> 0
            }
        }

        private fun Byte.unsigned(): Int = toInt() and 0xFF

        private fun avcProfileFromIdc(profileIdc: Int, constraints: Int): Int? {
            return when (profileIdc) {
                66 -> if ((constraints and 0x40) != 0) {
                    CodecProfileLevel.AVCProfileConstrainedBaseline
                } else {
                    CodecProfileLevel.AVCProfileBaseline
                }

                77 -> CodecProfileLevel.AVCProfileMain
                88 -> CodecProfileLevel.AVCProfileExtended
                100 -> if ((constraints and 0x0C) == 0x0C) {
                    CodecProfileLevel.AVCProfileConstrainedHigh
                } else {
                    CodecProfileLevel.AVCProfileHigh
                }

                110 -> CodecProfileLevel.AVCProfileHigh10
                122 -> CodecProfileLevel.AVCProfileHigh422
                244 -> CodecProfileLevel.AVCProfileHigh444
                else -> null
            }
        }

        private fun avcLevelFromIdc(levelIdc: Int, constraints: Int): Int? {
            return when (levelIdc) {
                9 -> CodecProfileLevel.AVCLevel1b
                10 -> CodecProfileLevel.AVCLevel1
                11 -> if ((constraints and 0x10) != 0) {
                    CodecProfileLevel.AVCLevel1b
                } else {
                    CodecProfileLevel.AVCLevel11
                }

                12 -> CodecProfileLevel.AVCLevel12
                13 -> CodecProfileLevel.AVCLevel13
                20 -> CodecProfileLevel.AVCLevel2
                21 -> CodecProfileLevel.AVCLevel21
                22 -> CodecProfileLevel.AVCLevel22
                30 -> CodecProfileLevel.AVCLevel3
                31 -> CodecProfileLevel.AVCLevel31
                32 -> CodecProfileLevel.AVCLevel32
                40 -> CodecProfileLevel.AVCLevel4
                41 -> CodecProfileLevel.AVCLevel41
                42 -> CodecProfileLevel.AVCLevel42
                50 -> CodecProfileLevel.AVCLevel5
                51 -> CodecProfileLevel.AVCLevel51
                52 -> CodecProfileLevel.AVCLevel52
                60 -> CodecProfileLevel.AVCLevel6
                61 -> CodecProfileLevel.AVCLevel61
                62 -> CodecProfileLevel.AVCLevel62
                else -> null
            }
        }

        private fun MediaFormat.optRotationDegrees(): Int? {
            val rotation = optInt(MediaFormat.KEY_ROTATION) ?: return null
            return when (rotation.floorMod(360)) {
                0, 90, 180, 270 -> rotation.floorMod(360)
                else -> null
            }
        }

        private fun MediaFormat.displaySize(rotationDegrees: Int?): Pair<Int, Int>? {
            val croppedWidth = cropSize(CROP_LEFT, CROP_RIGHT)
            val croppedHeight = cropSize(CROP_TOP, CROP_BOTTOM)
            val width = croppedWidth ?: optPositiveInt(MediaFormat.KEY_WIDTH)
            val height = croppedHeight ?: optPositiveInt(MediaFormat.KEY_HEIGHT)
            if (width == null || height == null) return null

            return if (rotationDegrees == 90 || rotationDegrees == 270) {
                height to width
            } else {
                width to height
            }
        }

        private fun MediaFormat.cropSize(startKey: String, endKey: String): Int? {
            val start = optInt(startKey) ?: return null
            val end = optInt(endKey) ?: return null
            return (end - start + 1).takeIf { it > 0 }
        }

        private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

        private fun MediaFormat.toStableJson(
            displayWidth: Int?,
            displayHeight: Int?,
            bitrateKbps: Int?,
            frameRate: Float?
        ): String {
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
            json.put("_display_width", displayWidth ?: JSONObject.NULL)
            json.put("_display_height", displayHeight ?: JSONObject.NULL)
            json.put("_bitrate_kbps", bitrateKbps ?: JSONObject.NULL)
            json.put("_frame_rate", frameRate?.toDouble() ?: JSONObject.NULL)
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

        private data class AvcSpsHeader(
            val profileIdc: Int,
            val constraints: Int,
            val levelIdc: Int
        )

        private const val CROP_LEFT = "crop-left"
        private const val CROP_RIGHT = "crop-right"
        private const val CROP_TOP = "crop-top"
        private const val CROP_BOTTOM = "crop-bottom"
    }
}
