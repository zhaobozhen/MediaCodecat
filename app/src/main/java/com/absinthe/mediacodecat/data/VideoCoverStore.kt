package com.absinthe.mediacodecat.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object VideoCoverStore {
    private const val COVER_DIR = "covers"
    private const val COVER_EXTENSION = "webp"
    private const val LEGACY_COVER_EXTENSION = "jpg"
    private val coverNameCache = ConcurrentHashMap<String, String>()

    fun coverFile(context: Context, sessionId: String): File {
        return File(coverDir(context), "${sessionId.coverName()}.$COVER_EXTENSION")
    }

    fun existingCoverFile(context: Context, sessionId: String): File? {
        val current = coverFile(context, sessionId)
        if (current.exists()) return current

        return legacyCoverFile(context, sessionId).takeIf { it.exists() }
    }

    fun save(context: Context, sessionId: String, bytes: ByteArray): File {
        val dir = coverDir(context).apply { mkdirs() }
        val target = coverFile(context, sessionId)
        val temp = File(dir, "${target.name}.tmp")

        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        legacyCoverFile(context, sessionId).takeIf { it.exists() }?.delete()
        return target
    }

    private fun coverDir(context: Context): File = File(context.filesDir, COVER_DIR)

    private fun legacyCoverFile(context: Context, sessionId: String): File {
        return File(coverDir(context), "${sessionId.coverName()}.$LEGACY_COVER_EXTENSION")
    }

    private fun String.coverName(): String = coverNameCache.getOrPut(this) { sha256Hex() }

    private fun String.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}
