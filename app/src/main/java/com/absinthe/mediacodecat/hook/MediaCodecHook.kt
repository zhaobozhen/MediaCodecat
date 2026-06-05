package com.absinthe.mediacodecat.hook

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import com.absinthe.mediacodecat.R
import com.absinthe.mediacodecat.data.VideoCoverSink
import com.absinthe.mediacodecat.data.VideoRecordContract
import com.absinthe.mediacodecat.data.VideoRecordSink
import com.absinthe.mediacodecat.manager.SurfaceRegistry
import com.absinthe.mediacodecat.model.FrameEvent
import com.absinthe.mediacodecat.model.VideoRecord
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

object MediaCodecHook : YukiBaseHooker() {

    private const val TAG = "MediaCodecat"
    private const val WINDOW_MS = 1000L
    private const val STALE_SESSION_MS = 60_000L
    private const val COVER_MIN_ELAPSED_MS = 800L
    private const val COVER_MAX_LONG_EDGE = 384
    private const val COVER_JPEG_QUALITY = 72
    private const val COVER_MIME_TYPE = "image/jpeg"
    private const val COVER_DARK_LUMA_THRESHOLD = 6f
    private const val COVER_RETRY_COOLDOWN_MS = 1_200L
    private const val CROP_LEFT = "crop-left"
    private const val CROP_RIGHT = "crop-right"
    private const val CROP_TOP = "crop-top"
    private const val CROP_BOTTOM = "crop-bottom"

    private val COVER_FRAME_THRESHOLDS = intArrayOf(12, 30, 60)

    private val sessions = ConcurrentHashMap<Int, CodecSession>()
    private val renderMissingSessionKeys = ConcurrentHashMap.newKeySet<Int>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onHook() {
        loadApp {
            MediaCodec::class.resolve().apply {
                firstMethod {
                    name = "configure"
                    parameters(
                        MediaFormat::class,
                        Surface::class,
                        MediaCrypto::class,
                        Class.forName("android.os.IHwBinder"),
                        Int::class
                    )
                }.hook {
                    after {
                        val codec = instance<MediaCodec>()
                        val format = args[0] as? MediaFormat ?: return@after
                        val mime = format.mime() ?: return@after

                        closeSession(codec)

                        if (!mime.startsWith("video/")) return@after

                        val context = currentApplicationContext() ?: run {
                            YLog.debug("MediaCodecHook: skip video record, app context is null")
                            return@after
                        }
                        val surface = args[1] as? Surface
                        val session = CodecSession(
                            codecKey = codec.key(),
                            sessionId = newSessionId(context, codec),
                            context = context,
                            packageName = currentPackageName(context),
                            processName = currentProcessName(),
                            codecName = codec.safeName(),
                            format = MediaFormat(format),
                            surface = surface,
                            firstSeenAtMs = System.currentTimeMillis()
                        )

                        sessions[session.codecKey] = session
                        YLog.info(
                            "MediaCodecHook: configured video codec=${session.codecName}, " +
                                "format=$format, surface=$surface, session=${session.sessionId}"
                        )

                        publishRecord(session)
                        updateSurfaceContent(session)
                        startBackgroundWorker(session)
                    }
                }

                firstMethod {
                    name = "setOutputSurface"
                    parameters(Surface::class)
                }.hook {
                    after {
                        val codec = instance<MediaCodec>()
                        val surface = args[0] as? Surface
                        val session = sessions[codec.key()] ?: return@after

                        session.surface = surface
                        session.surfaceId = surface?.stableId()
                        session.lastSeenAtMs = System.currentTimeMillis()

                        publishRecord(session)
                        updateSurfaceContent(session)
                    }
                }

                firstMethod {
                    name = "start"
                    emptyParameters()
                }.hook {
                    after {
                        val codec = instance<MediaCodec>()
                        refreshCodecFormats(codec, "start")
                    }
                }

                firstMethod {
                    name = "getInputFormat"
                    emptyParameters()
                }.hook {
                    after {
                        val codec = instance<MediaCodec>()
                        val format = result as? MediaFormat ?: return@after
                        mergeCodecFormat(codec, format, "getInputFormat")
                    }
                }

                firstMethod {
                    name = "getOutputFormat"
                    emptyParameters()
                }.hook {
                    after {
                        val codec = instance<MediaCodec>()
                        val format = result as? MediaFormat ?: return@after
                        mergeCodecFormat(codec, format, "getOutputFormat")
                    }
                }

                firstMethod {
                    name = "getOutputFormat"
                    parameters(Int::class)
                }.hook {
                    after {
                        val codec = instance<MediaCodec>()
                        val format = result as? MediaFormat ?: return@after
                        mergeCodecFormat(codec, format, "getOutputFormat(index)")
                    }
                }

                firstMethod {
                    name = "queueInputBuffer"
                    parameters(
                        Int::class,
                        Int::class,
                        Int::class,
                        Long::class,
                        Int::class
                    )
                }.hook {
                    after {
                        val size = args[2] as Int
                        if (size <= 0) return@after
                        val presentationTimeUs = args[3] as? Long

                        sessions[instance<MediaCodec>().key()]?.let { session ->
                            val frameCount = session.offerInput(size, presentationTimeUs)
                            maybeCaptureCover(session, frameCount, "queueInputBuffer")
                        }
                    }
                }

                firstMethod {
                    name = "queueSecureInputBuffer"
                    parameters(
                        Int::class,
                        Int::class,
                        MediaCodec.CryptoInfo::class,
                        Long::class,
                        Int::class
                    )
                }.hook {
                    after {
                        val size = (args[2] as? MediaCodec.CryptoInfo)?.totalBytes() ?: 0
                        if (size <= 0) return@after
                        val presentationTimeUs = args[3] as? Long

                        sessions[instance<MediaCodec>().key()]?.let { session ->
                            val frameCount = session.offerInput(size, presentationTimeUs)
                            maybeCaptureCover(session, frameCount, "queueSecureInputBuffer")
                        }
                    }
                }

                firstMethod {
                    name = "dequeueOutputBuffer"
                    parameters(MediaCodec.BufferInfo::class, Long::class)
                }.hook {
                    after {
                        val codec = instance<MediaCodec>()
                        when (result as? Int) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> refreshCodecFormats(
                                codec,
                                "dequeueOutputBuffer(format-changed)"
                            )

                            null -> Unit
                            else -> Unit
                        }
                    }
                }

                firstMethod {
                    name = "releaseOutputBuffer"
                    parameters(Int::class, Boolean::class)
                }.hook {
                    after {
                        val render = args[1] as? Boolean ?: return@after
                        onReleasedOutputBuffer(instance<MediaCodec>(), render, "releaseOutputBuffer(render)")
                    }
                }

                firstMethod {
                    name = "releaseOutputBuffer"
                    parameters(Int::class, Long::class)
                }.hook {
                    after {
                        onRenderedFrame(instance<MediaCodec>(), "releaseOutputBuffer(timestamp)")
                    }
                }

                firstMethod {
                    name = "releaseOutputBufferInternal"
                    parameters(Int::class, Boolean::class, Boolean::class, Long::class)
                }.hook {
                    after {
                        val render = args[1] as? Boolean ?: return@after
                        onReleasedOutputBuffer(instance<MediaCodec>(), render, "releaseOutputBufferInternal")
                    }
                }

                firstMethod {
                    name = "releaseOutputBuffer"
                    parameters(Int::class, Boolean::class, Boolean::class, Long::class)
                }.hook {
                    after {
                        val render = args[1] as? Boolean ?: return@after
                        onReleasedOutputBuffer(instance<MediaCodec>(), render, "releaseOutputBuffer(native)")
                    }
                }

                firstMethod {
                    name = "stop"
                }.hook {
                    before {
                        closeSession(instance<MediaCodec>())
                    }
                }

                firstMethod {
                    name = "reset"
                }.hook {
                    before {
                        closeSession(instance<MediaCodec>())
                    }
                }

                firstMethod {
                    name = "release"
                }.hook {
                    before {
                        closeSession(instance<MediaCodec>())
                    }
                }
            }
        }
    }

    private fun startBackgroundWorker(session: CodecSession) {
        if (!session.workerStarted.compareAndSet(false, true)) return

        thread(start = true, name = "CodecRateWorker-${session.codecKey}") {
            val window = ArrayDeque<FrameEvent>()
            val outputWindow = ArrayDeque<Long>()
            var smoothKbps = 0.0
            var smoothInputFps = 0.0
            var smoothFps = 0.0
            val alpha = 0.2

            while (!session.isClosed) {
                var event = session.events.poll()
                while (event != null) {
                    window.addLast(event)
                    event = session.events.poll()
                }

                var outputEvent = session.renderedFrameEvents.poll()
                while (outputEvent != null) {
                    outputWindow.addLast(outputEvent)
                    outputEvent = session.renderedFrameEvents.poll()
                }

                val nowElapsedMs = SystemClock.elapsedRealtime()
                val cutoff = nowElapsedMs - WINDOW_MS
                while (window.isNotEmpty() && window.first().pts < cutoff) {
                    window.removeFirst()
                }
                while (outputWindow.isNotEmpty() && outputWindow.first() < cutoff) {
                    outputWindow.removeFirst()
                }

                if (window.isNotEmpty()) {
                    var totalBytes = 0L
                    for (frameEvent in window) totalBytes += frameEvent.size

                    val kbps = (totalBytes * 8.0) / 1000.0
                    smoothKbps = if (smoothKbps == 0.0) {
                        kbps
                    } else {
                        smoothKbps * (1 - alpha) + kbps * alpha
                    }

                    session.bitrateKbps = smoothKbps.roundToInt()
                    session.lastSeenAtMs = System.currentTimeMillis()
                    publishRecord(session)
                    updateSurfaceContent(session)
                }

                val inputFps = window.estimateFrameRate()
                if (inputFps != null) {
                    smoothInputFps = if (smoothInputFps == 0.0) {
                        inputFps
                    } else {
                        smoothInputFps * (1 - alpha) + inputFps * alpha
                    }

                    session.estimatedFrameRate = smoothInputFps.toFloat()
                    session.lastSeenAtMs = System.currentTimeMillis()
                    publishRecord(session)
                    updateSurfaceContent(session)
                }

                if (outputWindow.isNotEmpty()) {
                    val fps = outputWindow.size * 1000.0 / WINDOW_MS
                    smoothFps = if (smoothFps == 0.0) {
                        fps
                    } else {
                        smoothFps * (1 - alpha) + fps * alpha
                    }

                    session.estimatedFrameRate = smoothFps.toFloat()
                    session.lastSeenAtMs = System.currentTimeMillis()
                    publishRecord(session)
                    updateSurfaceContent(session)
                }

                val lastInputAtElapsedMs = session.lastInputAtElapsedMs
                if (lastInputAtElapsedMs > 0 && nowElapsedMs - lastInputAtElapsedMs > STALE_SESSION_MS) {
                    closeSession(session.codecKey, session)
                }

                Thread.sleep(WINDOW_MS)
            }

            publishRecord(session)
            updateSurfaceContent(session)
            YLog.debug("CodecRateWorker: stop, session=${session.sessionId}")
        }
    }

    private fun ArrayDeque<FrameEvent>.estimateFrameRate(): Double? {
        if (size < 2) return null
        val firstPtsUs = first().presentationTimeUs ?: return null
        val lastPtsUs = last().presentationTimeUs ?: return null
        val durationUs = lastPtsUs - firstPtsUs
        if (durationUs <= 0) return null

        val fps = (size - 1) * 1_000_000.0 / durationUs
        return fps.takeIf { it.isFinite() && it > 0.0 && it <= 1000.0 }
    }

    private fun refreshCodecFormats(codec: MediaCodec, source: String) {
        runCatching { codec.inputFormat }.getOrNull()?.let {
            mergeCodecFormat(codec, it, "$source/input")
        }
        runCatching { codec.outputFormat }.getOrNull()?.let {
            mergeCodecFormat(codec, it, "$source/output")
        }
    }

    private fun mergeCodecFormat(codec: MediaCodec, format: MediaFormat, source: String) {
        val session = sessions[codec.key()] ?: return
        if (!format.isVideoFormatLike(session.formatMime)) return

        session.mergeFormat(format)
        session.lastSeenAtMs = System.currentTimeMillis()
        YLog.debug(
            "MediaCodecHook: merged $source format, session=${session.sessionId}, format=$format"
        )
        publishRecord(session)
        updateSurfaceContent(session)
    }

    private fun publishRecord(session: CodecSession) {
        if (!VideoRecordSink.upsert(session.context, session.toRecord()) &&
            session.persistFailureLogged.compareAndSet(false, true)
        ) {
            YLog.debug("MediaCodecHook: failed to persist video record, session=${session.sessionId}")
        }
    }

    private fun updateSurfaceContent(session: CodecSession) {
        val surface = session.surface ?: return
        val record = session.toRecord()
        val content = record.toOverlayText(session.context)

        SurfaceRegistry.addContent(surface, content)
        if (SurfaceRegistry.findTextView(surface) == null) {
            SurfaceRegistry.findTextView()?.let { SurfaceRegistry.addTextView(surface, it) }
        }
        SurfaceRegistry.findTextView(surface)?.let { textView ->
            textView.post {
                textView.text = content
            }
        }
    }

    private fun onRenderedFrame(codec: MediaCodec, source: String) {
        val codecKey = codec.key()
        val session = sessions[codecKey] ?: run {
            if (renderMissingSessionKeys.add(codecKey)) {
                Log.d(TAG, "MediaCodecHook: rendered frame has no video session, source=$source, codecKey=$codecKey")
            }
            return
        }
        val frameCount = session.offerRenderedFrame()
        if (session.coverFrameLogged.compareAndSet(false, true)) {
            Log.d(
                TAG,
                "MediaCodecHook: rendered video frame source=$source, " +
                    "surface=${session.surface}, session=${session.sessionId}"
            )
        }
        maybeCaptureCover(session, frameCount, source)
    }

    private fun onReleasedOutputBuffer(codec: MediaCodec, render: Boolean, source: String) {
        if (render) {
            onRenderedFrame(codec, source)
            return
        }

        sessions[codec.key()]?.let { session ->
            val frameCount = session.offerNonRenderedOutput()
            if (session.coverNonRenderLogged.compareAndSet(false, true)) {
                Log.d(
                    TAG,
                    "MediaCodecHook: non-render output release source=$source, " +
                        "session=${session.sessionId}"
                )
            }
            maybeCaptureCover(session, frameCount, source)
        }
    }

    private fun maybeCaptureCover(session: CodecSession, frameCount: Int, source: String) {
        if (session.coverSaved.get()) return
        val attempt = session.coverAttempts.get()
        if (attempt >= COVER_FRAME_THRESHOLDS.size) return
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (nowElapsedMs - session.firstSeenAtElapsedMs < COVER_MIN_ELAPSED_MS) return
        if (nowElapsedMs - session.lastCoverAttemptAtElapsedMs.get() < COVER_RETRY_COOLDOWN_MS) return
        if (frameCount < COVER_FRAME_THRESHOLDS[attempt]) return
        if (!session.coverCaptureInFlight.compareAndSet(false, true)) return
        session.lastCoverAttemptAtElapsedMs.set(nowElapsedMs)

        captureCover(session, source)
    }

    private fun captureCover(session: CodecSession, source: String) {
        val surface = session.surface ?: run {
            cancelCoverCapture(session, "source=$source, missing surface")
            return
        }
        val surfaceView = SurfaceRegistry.findSurfaceView(surface)
        val (coverWidth, coverHeight) = session.coverCaptureSize(surfaceView) ?: run {
            cancelCoverCapture(session, "source=$source, missing cover size")
            return
        }
        val bitmap = Bitmap.createBitmap(coverWidth, coverHeight, Bitmap.Config.ARGB_8888)

        mainHandler.post {
            if (!surface.isValid) {
                bitmap.recycle()
                cancelCoverCapture(session, "source=$source, invalid surface")
                return@post
            }

            runCatching {
                if (surfaceView != null && surfaceView.isAttachedToWindow && surfaceView.holder.surface.isValid) {
                    PixelCopy.request(
                        surfaceView,
                        bitmap,
                        { result -> handleCoverCopyResult(session, bitmap, result, source) },
                        mainHandler
                    )
                } else {
                    PixelCopy.request(
                        surface,
                        bitmap,
                        { result -> handleCoverCopyResult(session, bitmap, result, source) },
                        mainHandler
                    )
                }
            }.onFailure { throwable ->
                bitmap.recycle()
                finishCoverAttempt(
                    session,
                    success = false,
                    reason = "source=$source, PixelCopy request failed: ${throwable.message}"
                )
            }
        }
    }

    private fun cancelCoverCapture(session: CodecSession, reason: String) {
        session.coverCaptureInFlight.set(false)
        if (session.coverSkipLogged.compareAndSet(false, true)) {
            Log.d(TAG, "MediaCodecHook: cover capture skipped, session=${session.sessionId}, $reason")
        }
        YLog.debug("MediaCodecHook: cover capture skipped, session=${session.sessionId}, $reason")
    }

    private fun handleCoverCopyResult(session: CodecSession, bitmap: Bitmap, result: Int, source: String) {
        if (result != PixelCopy.SUCCESS) {
            bitmap.recycle()
            finishCoverAttempt(session, success = false, reason = "source=$source, PixelCopy result=$result")
            return
        }

        thread(start = true, name = "CoverCompress-${session.codecKey}") {
            val averageLuma = bitmap.averageLuma()
            if (averageLuma < COVER_DARK_LUMA_THRESHOLD &&
                session.coverAttempts.get() < COVER_FRAME_THRESHOLDS.lastIndex
            ) {
                bitmap.recycle()
                finishCoverAttempt(session, success = false, reason = "source=$source, cover too dark, luma=$averageLuma")
                return@thread
            }

            val bytes = bitmap.toJpegBytes()
            val width = bitmap.width
            val height = bitmap.height
            bitmap.recycle()

            val sent = VideoCoverSink.upsert(
                context = session.context,
                sessionId = session.sessionId,
                bytes = bytes,
                mimeType = COVER_MIME_TYPE,
                width = width,
                height = height
            )
            finishCoverAttempt(
                session = session,
                success = sent,
                reason = if (sent) {
                    "source=$source, saved cover ${width}x$height"
                } else {
                    "source=$source, cover broadcast failed"
                }
            )
        }
    }

    private fun finishCoverAttempt(session: CodecSession, success: Boolean, reason: String) {
        if (success) {
            session.coverSaved.set(true)
        } else {
            session.coverAttempts.incrementAndGet()
        }
        session.coverCaptureInFlight.set(false)
        Log.d(
            TAG,
            "MediaCodecHook: cover capture ${if (success) "success" else "retry"}, " +
                "session=${session.sessionId}, $reason"
        )
        YLog.debug("MediaCodecHook: cover capture ${if (success) "success" else "retry"}, session=${session.sessionId}, $reason")
    }

    private fun closeSession(codec: MediaCodec) {
        closeSession(codec.key(), sessions[codec.key()])
    }

    private fun closeSession(codecKey: Int, session: CodecSession?) {
        if (session == null) return
        sessions.remove(codecKey, session)
        session.close()
        publishRecord(session)
        updateSurfaceContent(session)
    }

    private fun VideoRecord.toOverlayText(context: Context): String {
        val strings = context.overlayStrings()
        return buildString {
            append(mime)
            if (width != null && height != null) append(" ${width}x$height")
            codecName?.let {
                appendLine()
                append(strings.codecFormat.formatLocalized(it))
            }
            appendLine()
            append(strings.packageFormat.formatLocalized(packageName))
            appendLine()
            val bitrate = bitrateKbps
                ?.let { strings.bitrateKbpsFormat.formatLocalized(it) }
                ?: strings.emptyValue
            append(strings.bitrateFormat.formatLocalized(bitrate))
        }
    }

    private fun Context.overlayStrings(): OverlayStrings {
        val moduleContext = runCatching {
            createPackageContext(VideoRecordContract.MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull()

        fun stringResource(id: Int, fallback: String): String =
            moduleContext?.runCatching { getString(id) }?.getOrNull() ?: fallback

        return OverlayStrings(
            codecFormat = stringResource(R.string.video_record_overlay_codec_format, "%1\$s"),
            packageFormat = stringResource(R.string.video_record_overlay_package_format, "%1\$s"),
            bitrateFormat = stringResource(R.string.video_record_overlay_bitrate_format, "%1\$s"),
            bitrateKbpsFormat = stringResource(R.string.video_record_bitrate_kbps_format, "%1\$d kbps"),
            emptyValue = stringResource(R.string.video_record_empty_value, "-")
        )
    }

    private data class OverlayStrings(
        val codecFormat: String,
        val packageFormat: String,
        val bitrateFormat: String,
        val bitrateKbpsFormat: String,
        val emptyValue: String
    )

    private fun String.formatLocalized(vararg args: Any): String =
        String.format(Locale.getDefault(), this, *args)

    private fun MediaCodec.key(): Int = System.identityHashCode(this)

    private fun MediaCodec.safeName(): String? = runCatching { name }.getOrNull()

    private fun MediaFormat.mime(): String? = runCatching {
        if (containsKey(MediaFormat.KEY_MIME)) getString(MediaFormat.KEY_MIME) else null
    }.getOrNull()

    private fun MediaFormat.isVideoFormatLike(fallbackMime: String?): Boolean {
        val mime = mime() ?: fallbackMime
        return mime?.startsWith("video/") == true
    }

    private fun MediaFormat.captureDisplaySize(): Pair<Int, Int>? {
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

    private fun MediaFormat.mergeWith(newer: MediaFormat): MediaFormat {
        val merged = MediaFormat(this)
        newer.keys.forEach { key ->
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
                YLog.debug("MediaCodecHook: skip format key=$key while merging", it)
            }
        }
        return merged
    }

    private fun MediaCodec.CryptoInfo.totalBytes(): Int {
        val clearBytes = numBytesOfClearData?.sum() ?: 0
        val encryptedBytes = numBytesOfEncryptedData?.sum() ?: 0
        return clearBytes + encryptedBytes
    }

    private fun Surface.stableId(): String {
        return "${javaClass.name}@${System.identityHashCode(this).toString(16)}"
    }

    private fun Pair<Int, Int>.scaledCoverSize(): Pair<Int, Int> {
        val sourceWidth = first
        val sourceHeight = second
        val scale = (COVER_MAX_LONG_EDGE.toFloat() / maxOf(sourceWidth, sourceHeight)).coerceAtMost(1f)
        val targetWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        return targetWidth to targetHeight
    }

    private fun Bitmap.toJpegBytes(): ByteArray {
        return ByteArrayOutputStream().use { output ->
            compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, output)
            output.toByteArray()
        }
    }

    private fun Bitmap.averageLuma(): Float {
        val stepX = (width / 32).coerceAtLeast(1)
        val stepY = (height / 32).coerceAtLeast(1)
        var total = 0f
        var count = 0

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = getPixel(x, y)
                total += Color.red(pixel) * 0.2126f +
                    Color.green(pixel) * 0.7152f +
                    Color.blue(pixel) * 0.0722f
                count++
                x += stepX
            }
            y += stepY
        }

        return if (count == 0) 0f else total / count
    }

    private fun newSessionId(context: Context, codec: MediaCodec): String {
        return "${context.packageName}:${codec.key()}:${UUID.randomUUID()}"
    }

    private fun currentApplicationContext(): Context? {
        val xposedApplication = runCatching {
            Class.forName("android.app.AndroidAppHelper")
                .getDeclaredMethod("currentApplication")
                .invoke(null) as? Application
        }.getOrNull()
        val activityThreadApplication = runCatching {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentApplication")
                .invoke(null) as? Application
        }.getOrNull()

        return (xposedApplication ?: activityThreadApplication)?.applicationContext
    }

    private fun currentPackageName(context: Context): String {
        return runCatching {
            Class.forName("android.app.AndroidAppHelper")
                .getDeclaredMethod("currentPackageName")
                .invoke(null) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: context.packageName
    }

    private fun currentProcessName(): String {
        return runCatching {
            Class.forName("android.app.AndroidAppHelper")
                .getDeclaredMethod("currentProcessName")
                .invoke(null) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Application.getProcessName().takeIf { it.isNotBlank() }
            ?: "unknown"
    }

    private class CodecSession(
        val codecKey: Int,
        val sessionId: String,
        val context: Context,
        val packageName: String,
        val processName: String,
        val codecName: String?,
        format: MediaFormat,
        @Volatile var surface: Surface?,
        val firstSeenAtMs: Long
    ) {
        private val formatLock = Any()
        private var format: MediaFormat = MediaFormat(format)

        val formatMime: String? = format.mime()
        val events = ConcurrentLinkedQueue<FrameEvent>()
        val renderedFrameEvents = ConcurrentLinkedQueue<Long>()
        val workerStarted = AtomicBoolean(false)
        val persistFailureLogged = AtomicBoolean(false)
        val coverCaptureInFlight = AtomicBoolean(false)
        val coverSaved = AtomicBoolean(false)
        val coverFrameLogged = AtomicBoolean(false)
        val coverSkipLogged = AtomicBoolean(false)
        val coverNonRenderLogged = AtomicBoolean(false)
        val coverAttempts = AtomicInteger(0)
        val lastCoverAttemptAtElapsedMs = AtomicLong(0L)

        @Volatile var surfaceId: String? = surface?.stableId()
        @Volatile var bitrateKbps: Int? = null
        @Volatile var estimatedFrameRate: Float? = null
        @Volatile var lastSeenAtMs: Long = firstSeenAtMs
        @Volatile var lastInputAtElapsedMs: Long = 0L
        val firstSeenAtElapsedMs: Long = SystemClock.elapsedRealtime()

        private val closed = AtomicBoolean(false)
        val isClosed: Boolean get() = closed.get()

        fun mergeFormat(newer: MediaFormat) {
            synchronized(formatLock) {
                format = format.mergeWith(newer)
            }
        }

        fun offerInput(size: Int, presentationTimeUs: Long?): Int {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            events.offer(
                FrameEvent(
                    pts = nowElapsedMs,
                    size = size,
                    presentationTimeUs = presentationTimeUs
                )
            )
            lastInputAtElapsedMs = nowElapsedMs
            lastSeenAtMs = System.currentTimeMillis()
            return inputFrameCount.incrementAndGet()
        }

        fun offerRenderedFrame(): Int {
            renderedFrameEvents.offer(SystemClock.elapsedRealtime())
            lastSeenAtMs = System.currentTimeMillis()
            return renderedFrameCount.incrementAndGet()
        }

        fun offerNonRenderedOutput(): Int {
            lastSeenAtMs = System.currentTimeMillis()
            return nonRenderedOutputCount.incrementAndGet()
        }

        fun coverCaptureSize(surfaceView: SurfaceView?): Pair<Int, Int>? {
            val formatSize = synchronized(formatLock) { format.captureDisplaySize() }
            val viewSize = surfaceView
                ?.takeIf { it.width > 0 && it.height > 0 }
                ?.let { it.width to it.height }
            return (formatSize ?: viewSize)?.scaledCoverSize()
        }

        fun close() {
            closed.compareAndSet(false, true)
            lastSeenAtMs = System.currentTimeMillis()
        }

        fun toRecord(): VideoRecord = VideoRecord.fromMediaFormat(
            sessionId = sessionId,
            packageName = packageName,
            processName = processName,
            codecName = codecName,
            surfaceId = surfaceId,
            format = synchronized(formatLock) { MediaFormat(format) },
            firstSeenAtMs = firstSeenAtMs,
            lastSeenAtMs = lastSeenAtMs,
            bitrateKbps = bitrateKbps,
            estimatedFrameRate = estimatedFrameRate
        )

        private val renderedFrameCount = AtomicInteger(0)
        private val inputFrameCount = AtomicInteger(0)
        private val nonRenderedOutputCount = AtomicInteger(0)
    }
}
