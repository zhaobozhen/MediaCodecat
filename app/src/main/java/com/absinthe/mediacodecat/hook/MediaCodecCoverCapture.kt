package com.absinthe.mediacodecat.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import com.absinthe.mediacodecat.data.VideoCoverSink
import com.absinthe.mediacodecat.manager.SurfaceRegistry
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

private const val COVER_MIN_ELAPSED_MS = 800L
private const val COVER_DELAYED_CAPTURE_MS = 2_000L
private const val COVER_MAX_LONG_EDGE = 960
private const val COVER_ASPECT_MATCH_TOLERANCE = 0.03f
private const val COVER_WEBP_QUALITY = 90
private const val COVER_MIME_TYPE = "image/webp"
private const val COVER_DARK_LUMA_THRESHOLD = 6f
private const val COVER_RETRY_COOLDOWN_MS = 1_200L

private val COVER_FRAME_THRESHOLDS = intArrayOf(12, 30, 60)

internal class MediaCodecCoverCapture(
    private val mainHandler: Handler,
    private val currentWindowProvider: () -> Window?,
    private val logDebug: (String) -> Unit
) {
    fun maybeCapture(session: CodecSession, frameCount: Int, source: String) {
        if (session.coverSaved.get()) return
        val attempt = session.coverAttempts.get()
        if (attempt >= COVER_FRAME_THRESHOLDS.size) return
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (nowElapsedMs - session.firstSeenAtElapsedMs < COVER_MIN_ELAPSED_MS) return
        if (nowElapsedMs - session.lastCoverAttemptAtElapsedMs.get() < COVER_RETRY_COOLDOWN_MS) return
        if (frameCount < COVER_FRAME_THRESHOLDS[attempt]) return
        if (!session.coverCaptureInFlight.compareAndSet(false, true)) return
        session.lastCoverAttemptAtElapsedMs.set(nowElapsedMs)

        capture(session, source)
    }

    fun scheduleDelayedCapture(session: CodecSession, source: String) {
        if (!session.coverDelayedCaptureScheduled.compareAndSet(false, true)) return

        mainHandler.postDelayed({
            if (session.isClosed || session.coverSaved.get()) return@postDelayed
            if (session.coverAttempts.get() >= COVER_FRAME_THRESHOLDS.size) return@postDelayed
            if (!session.coverCaptureInFlight.compareAndSet(false, true)) return@postDelayed

            session.lastCoverAttemptAtElapsedMs.set(SystemClock.elapsedRealtime())
            capture(session, "$source/delayed")
        }, COVER_DELAYED_CAPTURE_MS)
    }

    private fun capture(session: CodecSession, source: String) {
        val surface = session.surface

        mainHandler.post {
            val targets = coverCaptureTargets(surface)
            if (targets.isEmpty()) {
                cancelCoverCapture(session, "source=$source, no available cover target")
                return@post
            }

            val targetSizeHint = targets.firstNotNullOfOrNull { it.sizeHint }
            val displaySize = session.coverDisplaySize(targetSizeHint) ?: run {
                cancelCoverCapture(session, "source=$source, missing cover size")
                return@post
            }
            logDebug(
                "MediaCodecHook: cover capture targets=${targets.joinToString { it.description(displaySize) }}"
            )
            val (coverWidth, coverHeight) = displaySize.scaledCoverSize()
            val bitmap = Bitmap.createBitmap(coverWidth, coverHeight, Bitmap.Config.ARGB_8888)

            requestCoverFromTarget(session, bitmap, source, targets, index = 0, displaySize = displaySize)
        }
    }

    private fun coverCaptureTargets(surface: Surface?): List<CoverTarget> {
        val targets = mutableListOf<CoverTarget>()
        val seenViews = mutableSetOf<Int>()

        fun addTarget(target: CoverTarget) {
            val view = target.surfaceView ?: target.textureView
            if (view != null && !seenViews.add(System.identityHashCode(view))) return
            targets += target
        }

        if (surface != null) {
            val surfaceView = SurfaceRegistry.findSurfaceView(surface)
            if (surfaceView != null &&
                surfaceView.isAttachedToWindow &&
                runCatching { surfaceView.holder.surface.isValid }.getOrDefault(false)
            ) {
                addTarget(
                    CoverTarget(
                        kind = CoverTargetKind.SURFACE_VIEW,
                        surfaceView = surfaceView,
                        sizeHint = surfaceView.sizeOrNull(),
                        topInset = surfaceView.localStatusBarInsetTop()
                    )
                )
            }

            val textureView = SurfaceRegistry.findTextureView(surface)
            if (textureView != null && textureView.isAvailable) {
                addTarget(
                    CoverTarget(
                        kind = CoverTargetKind.TEXTURE_VIEW,
                        textureView = textureView,
                        sizeHint = textureView.sizeOrNull(),
                        topInset = textureView.localStatusBarInsetTop()
                    )
                )
            }

            if (surface.isValid) {
                targets += CoverTarget(
                    kind = CoverTargetKind.CODEC_SURFACE,
                    surface = surface,
                    sizeHint = SurfaceRegistry.findSurfaceTextureSize(surface)
                )
            }
        }

        currentRenderViewTargets().forEach(::addTarget)

        currentWindow()?.let { window ->
            val decorView = window.decorView
            if (decorView.isAttachedToWindow) {
                targets += CoverTarget(
                    kind = CoverTargetKind.WINDOW,
                    window = window,
                    sizeHint = decorView.sizeOrNull(),
                    topInset = decorView.localStatusBarInsetTop(),
                    secureWindow = window.isSecureWindow()
                )
            }
        }

        return targets
    }

    private fun currentRenderViewTargets(): List<CoverTarget> {
        val decorView = currentWindow()?.decorView ?: return emptyList()
        val renderViews = mutableListOf<View>()
        collectRenderViews(decorView, renderViews)

        return renderViews
            .asSequence()
            .filter { it.isAttachedToWindow && it.visibility == View.VISIBLE && it.width > 0 && it.height > 0 }
            .sortedByDescending { it.width.toLong() * it.height.toLong() }
            .mapNotNull { view ->
                when (view) {
                    is SurfaceView -> {
                        if (!runCatching { view.holder.surface.isValid }.getOrDefault(false)) {
                            null
                        } else {
                            CoverTarget(
                                kind = CoverTargetKind.SURFACE_VIEW,
                                surfaceView = view,
                                sizeHint = view.sizeOrNull(),
                                topInset = view.localStatusBarInsetTop()
                            )
                        }
                    }

                    is TextureView -> {
                        if (!view.isAvailable) {
                            null
                        } else {
                            CoverTarget(
                                kind = CoverTargetKind.TEXTURE_VIEW,
                                textureView = view,
                                sizeHint = view.sizeOrNull(),
                                topInset = view.localStatusBarInsetTop()
                            )
                        }
                    }

                    else -> null
                }
            }
            .toList()
    }

    private fun collectRenderViews(view: View, out: MutableList<View>) {
        if (view is SurfaceView || view is TextureView) {
            out += view
        }

        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) {
            collectRenderViews(view.getChildAt(index), out)
        }
    }

    private fun requestCoverFromTarget(
        session: CodecSession,
        bitmap: Bitmap,
        source: String,
        targets: List<CoverTarget>,
        index: Int,
        displaySize: Pair<Int, Int>,
        useTopInset: Boolean = false,
        previousFailure: String? = null
    ) {
        if (index >= targets.size) {
            bitmap.recycle()
            finishCoverAttempt(
                session,
                success = false,
                reason = "source=$source, ${previousFailure ?: "all cover targets failed"}"
            )
            return
        }

        val target = targets[index]
        val sourceRect = target.sourceRect(displaySize, useTopInset)
        if (previousFailure != null) {
            logDebug(
                "MediaCodecHook: cover capture fallback, session=${session.sessionId}, " +
                    "source=$source, next=${target.description(displaySize, useTopInset)}, reason=$previousFailure"
            )
        }

        runCatching {
            when (target.kind) {
                CoverTargetKind.SURFACE_VIEW -> {
                    val surfaceView = target.surfaceView ?: error("missing SurfaceView")
                    if (sourceRect != null) {
                        PixelCopy.request(
                            surfaceView,
                            sourceRect,
                            bitmap,
                            { result ->
                                handleCoverCopyResult(
                                    session,
                                    bitmap,
                                    result,
                                    source,
                                    target.kind,
                                    targets,
                                    index,
                                    displaySize,
                                    useTopInset
                                )
                            },
                            mainHandler
                        )
                    } else {
                        PixelCopy.request(
                            surfaceView,
                            bitmap,
                            { result ->
                                handleCoverCopyResult(
                                    session,
                                    bitmap,
                                    result,
                                    source,
                                    target.kind,
                                    targets,
                                    index,
                                    displaySize,
                                    useTopInset
                                )
                            },
                            mainHandler
                        )
                    }
                }

                CoverTargetKind.TEXTURE_VIEW -> {
                    val textureView = target.textureView ?: error("missing TextureView")
                    if (!textureView.isAvailable) {
                        requestCoverFromTarget(
                            session,
                            bitmap,
                            source,
                            targets,
                            index + 1,
                            displaySize,
                            useTopInset = false,
                            previousFailure = "${target.kind.label} unavailable"
                        )
                    } else {
                        if (sourceRect != null) {
                            val sourceBitmap = textureView.getBitmap(textureView.width, textureView.height)
                            if (sourceBitmap == null) {
                                requestCoverFromTarget(
                                    session,
                                    bitmap,
                                    source,
                                    targets,
                                    index + 1,
                                    displaySize,
                                    useTopInset = false,
                                    previousFailure = "${target.kind.label} unavailable"
                                )
                                return@runCatching
                            }
                            bitmap.drawScaled(sourceBitmap, sourceRect)
                            sourceBitmap.recycle()
                        } else {
                            textureView.getBitmap(bitmap)
                        }
                        handleCoverBitmap(session, bitmap, source, target.kind, targets, index, displaySize, useTopInset)
                    }
                }

                CoverTargetKind.CODEC_SURFACE -> {
                    val targetSurface = target.surface ?: error("missing codec Surface")
                    if (!targetSurface.isValid) {
                        requestCoverFromTarget(
                            session,
                            bitmap,
                            source,
                            targets,
                            index + 1,
                            displaySize,
                            useTopInset = false,
                            previousFailure = "${target.kind.label} invalid"
                        )
                        return@runCatching
                    }
                    if (sourceRect != null) {
                        PixelCopy.request(
                            targetSurface,
                            sourceRect,
                            bitmap,
                            { result ->
                                handleCoverCopyResult(
                                    session,
                                    bitmap,
                                    result,
                                    source,
                                    target.kind,
                                    targets,
                                    index,
                                    displaySize,
                                    useTopInset
                                )
                            },
                            mainHandler
                        )
                    } else {
                        PixelCopy.request(
                            targetSurface,
                            bitmap,
                            { result ->
                                handleCoverCopyResult(
                                    session,
                                    bitmap,
                                    result,
                                    source,
                                    target.kind,
                                    targets,
                                    index,
                                    displaySize,
                                    useTopInset
                                )
                            },
                            mainHandler
                        )
                    }
                }

                CoverTargetKind.WINDOW -> {
                    val window = target.window ?: error("missing Window")
                    if (sourceRect != null) {
                        PixelCopy.request(
                            window,
                            sourceRect,
                            bitmap,
                            { result ->
                                handleCoverCopyResult(
                                    session,
                                    bitmap,
                                    result,
                                    source,
                                    target.kind,
                                    targets,
                                    index,
                                    displaySize,
                                    useTopInset
                                )
                            },
                            mainHandler
                        )
                    } else {
                        PixelCopy.request(
                            window,
                            bitmap,
                            { result ->
                                handleCoverCopyResult(
                                    session,
                                    bitmap,
                                    result,
                                    source,
                                    target.kind,
                                    targets,
                                    index,
                                    displaySize,
                                    useTopInset
                                )
                            },
                            mainHandler
                        )
                    }
                }
            }
        }.onFailure { throwable ->
            requestCoverFromTarget(
                session,
                bitmap,
                source,
                targets,
                index + 1,
                displaySize,
                useTopInset = false,
                previousFailure = "${target.kind.label} request failed: ${throwable.message}"
            )
        }
    }

    private fun handleCoverCopyResult(
        session: CodecSession,
        bitmap: Bitmap,
        result: Int,
        source: String,
        targetKind: CoverTargetKind,
        targets: List<CoverTarget>,
        index: Int,
        displaySize: Pair<Int, Int>,
        useTopInset: Boolean
    ) {
        if (result != PixelCopy.SUCCESS) {
            val target = targets.getOrNull(index)
            requestCoverFromTarget(
                session,
                bitmap,
                source,
                targets,
                index + 1,
                displaySize,
                useTopInset = false,
                previousFailure = "${targetKind.label} PixelCopy result=${result.pixelCopyResultName()}($result)" +
                    target.secureWindowSuffix()
            )
            return
        }

        handleCoverBitmap(session, bitmap, source, targetKind, targets, index, displaySize, useTopInset)
    }

    private fun handleCoverBitmap(
        session: CodecSession,
        bitmap: Bitmap,
        source: String,
        targetKind: CoverTargetKind,
        targets: List<CoverTarget>,
        index: Int,
        displaySize: Pair<Int, Int>,
        useTopInset: Boolean
    ) {
        thread(start = true, name = "CoverCompress-${session.codecKey}") {
            val target = targets[index]
            if (!useTopInset && target.shouldRetryWithTopInset(displaySize, bitmap)) {
                mainHandler.post {
                    requestCoverFromTarget(
                        session,
                        bitmap,
                        source,
                        targets,
                        index,
                        displaySize,
                        useTopInset = true,
                        previousFailure = "${targetKind.label} detected top black inset"
                    )
                }
                return@thread
            }

            val averageLuma = bitmap.averageLuma()
            val secureWindowSuffix = target.secureWindowSuffix()
            if (averageLuma < COVER_DARK_LUMA_THRESHOLD &&
                index < targets.lastIndex &&
                session.coverAttempts.get() < COVER_FRAME_THRESHOLDS.lastIndex
            ) {
                mainHandler.post {
                    requestCoverFromTarget(
                        session,
                        bitmap,
                        source,
                        targets,
                        index + 1,
                        displaySize,
                        useTopInset = false,
                        previousFailure = "${targetKind.label} cover too dark, luma=$averageLuma$secureWindowSuffix"
                    )
                }
                return@thread
            }

            if (averageLuma < COVER_DARK_LUMA_THRESHOLD &&
                session.coverAttempts.get() < COVER_FRAME_THRESHOLDS.lastIndex
            ) {
                bitmap.recycle()
                finishCoverAttempt(
                    session,
                    success = false,
                    reason = "source=$source, ${targetKind.label} cover too dark, luma=$averageLuma$secureWindowSuffix"
                )
                return@thread
            }

            val bytes = bitmap.toWebpBytes()
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
                    "source=$source, ${targetKind.label} saved cover ${width}x$height"
                } else {
                    "source=$source, ${targetKind.label} cover broadcast failed"
                }
            )
        }
    }

    private fun currentWindow(): Window? = currentWindowProvider()

    private fun cancelCoverCapture(session: CodecSession, reason: String) {
        session.coverCaptureInFlight.set(false)
        if (session.coverSkipLogged.compareAndSet(false, true)) {
            logDebug("MediaCodecHook: cover capture skipped, session=${session.sessionId}, $reason")
        }
    }

    private fun finishCoverAttempt(session: CodecSession, success: Boolean, reason: String) {
        if (success) {
            session.coverSaved.set(true)
        } else {
            session.coverAttempts.incrementAndGet()
        }
        session.coverCaptureInFlight.set(false)
        logDebug(
            "MediaCodecHook: cover capture ${if (success) "success" else "retry"}, " +
                "session=${session.sessionId}, $reason"
        )
    }
}

private fun View.sizeOrNull(): Pair<Int, Int>? {
    return if (width > 0 && height > 0) width to height else null
}

private fun View.localStatusBarInsetTop(): Int {
    val statusBarTop = rootWindowInsets
        ?.getInsets(WindowInsets.Type.statusBars())
        ?.top
        ?: return 0
    if (statusBarTop <= 0 || height <= 0) return 0

    val location = IntArray(2)
    getLocationInWindow(location)
    return (statusBarTop - location[1]).coerceIn(0, height)
}

private fun Window.isSecureWindow(): Boolean {
    return attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}

private data class CoverTarget(
    val kind: CoverTargetKind,
    val surface: Surface? = null,
    val surfaceView: SurfaceView? = null,
    val textureView: TextureView? = null,
    val window: Window? = null,
    val sizeHint: Pair<Int, Int>? = null,
    val topInset: Int = 0,
    val secureWindow: Boolean = false
) {
    fun description(displaySize: Pair<Int, Int>? = null, useTopInset: Boolean = false): String {
        val size = sizeHint?.let { "${it.first}x${it.second}" } ?: "unknown"
        val crop = displaySize
            ?.let { sourceRect(it, useTopInset) }
            ?.let { ", crop=${it.left},${it.top},${it.right},${it.bottom}" }
            .orEmpty()
        val inset = topInset.takeIf { it > 0 }?.let { ", topInset=$it" }.orEmpty()
        val secure = secureWindow.takeIf { it }?.let { ", secureWindow=true" }.orEmpty()
        return "${kind.label}($size$inset$secure$crop)"
    }
}

private enum class CoverTargetKind(val label: String) {
    SURFACE_VIEW("surfaceView"),
    TEXTURE_VIEW("textureView"),
    CODEC_SURFACE("codecSurface"),
    WINDOW("window")
}

private fun CoverTarget.sourceRect(displaySize: Pair<Int, Int>, useTopInset: Boolean): Rect? {
    if (kind == CoverTargetKind.CODEC_SURFACE) return null
    return sizeHint?.videoAspectCropRect(
        displaySize = displaySize,
        topInset = if (useTopInset) topInset else 0
    )
}

private fun CoverTarget.shouldRetryWithTopInset(displaySize: Pair<Int, Int>, bitmap: Bitmap): Boolean {
    if (topInset <= 0 || kind == CoverTargetKind.CODEC_SURFACE) return false
    val sourceRect = sourceRect(displaySize, useTopInset = false) ?: return false
    if (sourceRect.top != 0 || sourceRect.height() <= 0) return false

    val expectedInsetHeight = (topInset.toFloat() / sourceRect.height() * bitmap.height)
        .roundToInt()
        .coerceAtLeast(1)
    return bitmap.hasTopBlackBand(expectedInsetHeight)
}

private fun CoverTarget?.secureWindowSuffix(): String {
    return if (this?.secureWindow == true) ", secureWindow=true" else ""
}

private fun Int.pixelCopyResultName(): String {
    return when (this) {
        PixelCopy.SUCCESS -> "SUCCESS"
        PixelCopy.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
        PixelCopy.ERROR_TIMEOUT -> "ERROR_TIMEOUT"
        PixelCopy.ERROR_SOURCE_NO_DATA -> "ERROR_SOURCE_NO_DATA"
        PixelCopy.ERROR_SOURCE_INVALID -> "ERROR_SOURCE_INVALID"
        PixelCopy.ERROR_DESTINATION_INVALID -> "ERROR_DESTINATION_INVALID"
        else -> "UNKNOWN"
    }
}

private fun Pair<Int, Int>.videoAspectCropRect(displaySize: Pair<Int, Int>, topInset: Int): Rect? {
    val sourceWidth = first
    val sourceHeight = second
    val videoWidth = displaySize.first
    val videoHeight = displaySize.second
    if (sourceWidth <= 0 || sourceHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
        return null
    }

    val sourceAspect = sourceWidth.toFloat() / sourceHeight
    val videoAspect = videoWidth.toFloat() / videoHeight
    val aspectDelta = abs(sourceAspect - videoAspect) / videoAspect
    if (aspectDelta <= COVER_ASPECT_MATCH_TOLERANCE) return null

    return if (sourceAspect < videoAspect) {
        val cropHeight = (sourceWidth / videoAspect).roundToInt().coerceIn(1, sourceHeight)
        val maxTop = (sourceHeight - cropHeight).coerceAtLeast(0)
        val top = if (sourceHeight > sourceWidth) {
            topInset.coerceIn(0, maxTop)
        } else {
            maxTop / 2
        }
        Rect(0, top, sourceWidth, top + cropHeight)
    } else {
        val cropWidth = (sourceHeight * videoAspect).roundToInt().coerceIn(1, sourceWidth)
        val left = (sourceWidth - cropWidth) / 2
        Rect(left, 0, left + cropWidth, sourceHeight)
    }
}

private fun Bitmap.drawScaled(source: Bitmap, sourceRect: Rect) {
    Canvas(this).drawBitmap(source, sourceRect, Rect(0, 0, width, height), null)
}

private fun Bitmap.hasTopBlackBand(expectedHeight: Int): Boolean {
    if (height < 8) return false
    val bandHeight = expectedHeight.coerceIn(2, (height / 3).coerceAtLeast(2))
    val topLuma = averageLuma(yStart = 0, yEnd = bandHeight)
    if (topLuma >= COVER_DARK_LUMA_THRESHOLD) return false

    val sampleStart = (bandHeight * 2).coerceAtMost(height - 1)
    val sampleEnd = (sampleStart + bandHeight).coerceAtMost(height)
    if (sampleEnd <= sampleStart) return false

    val contentLuma = averageLuma(yStart = sampleStart, yEnd = sampleEnd)
    return contentLuma >= COVER_DARK_LUMA_THRESHOLD * 2
}

private fun Pair<Int, Int>.scaledCoverSize(): Pair<Int, Int> {
    val sourceWidth = first
    val sourceHeight = second
    val scale = (COVER_MAX_LONG_EDGE.toFloat() / maxOf(sourceWidth, sourceHeight)).coerceAtMost(1f)
    val targetWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    return targetWidth to targetHeight
}

private fun Bitmap.toWebpBytes(): ByteArray {
    return ByteArrayOutputStream().use { output ->
        compress(Bitmap.CompressFormat.WEBP_LOSSY, COVER_WEBP_QUALITY, output)
        output.toByteArray()
    }
}

private fun Bitmap.averageLuma(yStart: Int = 0, yEnd: Int = height): Float {
    val stepX = (width / 32).coerceAtLeast(1)
    val startY = yStart.coerceIn(0, height)
    val endY = yEnd.coerceIn(startY, height)
    val sampleHeight = endY - startY
    if (sampleHeight <= 0) return 0f

    val stepY = (sampleHeight / 32).coerceAtLeast(1)
    var total = 0f
    var count = 0

    var y = startY
    while (y < endY) {
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
