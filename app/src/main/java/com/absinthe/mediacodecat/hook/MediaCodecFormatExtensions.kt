package com.absinthe.mediacodecat.hook

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.util.Locale

private const val CROP_LEFT = "crop-left"
private const val CROP_RIGHT = "crop-right"
private const val CROP_TOP = "crop-top"
private const val CROP_BOTTOM = "crop-bottom"

private val RAW_OUTPUT_CODEC_METADATA_KEYS = setOf(
    MediaFormat.KEY_MIME,
    MediaFormat.KEY_PROFILE,
    MediaFormat.KEY_LEVEL
)

internal fun MediaCodec.key(): Int = System.identityHashCode(this)

internal fun MediaCodec.safeName(): String? = runCatching { name }.getOrNull()

internal fun MediaFormat.mime(): String? = runCatching {
    if (containsKey(MediaFormat.KEY_MIME)) getString(MediaFormat.KEY_MIME) else null
}.getOrNull()

internal fun String.normalizedMime(): String = substringBefore(';').trim().lowercase(Locale.ROOT)

internal fun MediaFormat.normalizedMime(): String? = mime()?.normalizedMime()

internal fun MediaFormat.isVideoFormatLike(fallbackMime: String?): Boolean {
    val mime = (mime() ?: fallbackMime)?.normalizedMime()
    return mime?.startsWith("video/") == true
}

internal fun MediaFormat.captureDisplaySize(): Pair<Int, Int>? {
    val rotationDegrees = optRotationDegrees()
    val width = cropSize(CROP_LEFT, CROP_RIGHT) ?: optPositiveInt(MediaFormat.KEY_WIDTH)
    val height = cropSize(CROP_TOP, CROP_BOTTOM) ?: optPositiveInt(MediaFormat.KEY_HEIGHT)
    if (width == null || height == null) return null

    return if (rotationDegrees == 90 || rotationDegrees == 270) {
        height to width
    } else {
        width to height
    }
}

internal fun MediaFormat.mergeWith(
    newer: MediaFormat,
    onKeyFailure: (String, Throwable) -> Unit
): MediaFormat {
    val merged = MediaFormat(this)
    val keepEncodedCodecMetadata =
        normalizedMime() != MediaFormat.MIMETYPE_VIDEO_RAW &&
            newer.normalizedMime() == MediaFormat.MIMETYPE_VIDEO_RAW
    newer.keys.forEach { key ->
        if (keepEncodedCodecMetadata && key in RAW_OUTPUT_CODEC_METADATA_KEYS) {
            return@forEach
        }
        runCatching {
            when (newer.getValueTypeForKey(key)) {
                MediaFormat.TYPE_BYTE_BUFFER ->
                    newer.getByteBuffer(key)?.duplicate()?.let { merged.setByteBuffer(key, it) }

                MediaFormat.TYPE_FLOAT -> merged.setFloat(key, newer.getFloat(key))
                MediaFormat.TYPE_INTEGER -> merged.setInteger(key, newer.getInteger(key))
                MediaFormat.TYPE_LONG -> merged.setLong(key, newer.getLong(key))
                MediaFormat.TYPE_STRING -> newer.getString(key)?.let { merged.setString(key, it) }
                MediaFormat.TYPE_NULL -> Unit
                else -> Unit
            }
        }.onFailure {
            onKeyFailure(key, it)
        }
    }
    return merged
}

internal fun MediaCodec.CryptoInfo.totalBytes(): Int {
    val clearBytes = numBytesOfClearData?.sum() ?: 0
    val encryptedBytes = numBytesOfEncryptedData?.sum() ?: 0
    return clearBytes + encryptedBytes
}

internal fun Surface.stableId(): String {
    return "${javaClass.name}@${System.identityHashCode(this).toString(16)}"
}

private fun MediaFormat.optInt(key: String): Int? = runCatching {
    if (containsKey(key)) getNumber(key)?.toInt() else null
}.getOrNull()

private fun MediaFormat.optPositiveInt(key: String): Int? = optInt(key).takeIfPositive()

private fun MediaFormat.optRotationDegrees(): Int? {
    val rotation = optInt(MediaFormat.KEY_ROTATION) ?: return null
    return when (rotation.floorMod(360)) {
        0, 90, 180, 270 -> rotation.floorMod(360)
        else -> null
    }
}

private fun MediaFormat.cropSize(startKey: String, endKey: String): Int? {
    val start = optInt(startKey) ?: return null
    val end = optInt(endKey) ?: return null
    return (end - start + 1).takeIfPositive()
}

private fun Int?.takeIfPositive(): Int? = takeIf { it != null && it > 0 }

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
