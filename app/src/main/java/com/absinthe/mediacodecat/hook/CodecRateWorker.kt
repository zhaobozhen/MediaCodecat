package com.absinthe.mediacodecat.hook

import android.os.SystemClock
import com.absinthe.mediacodecat.model.FrameEvent
import kotlin.concurrent.thread
import kotlin.math.roundToInt

private const val PUBLISH_INTERVAL_MS = 1000L
private const val RATE_SAMPLE_WINDOW_MS = 5_000L
private const val MIN_RATE_SPAN_MS = 500L
private const val MIN_RATE_FRAME_COUNT = 8
private const val MAX_ESTIMATED_FRAME_RATE = 1000.0
private const val STALE_SESSION_MS = 60_000L

internal class CodecRateWorker(
    private val publishRecord: (CodecSession) -> Unit,
    private val closeSession: (Int, CodecSession) -> Unit,
    private val logDebug: (String) -> Unit
) {
    fun start(session: CodecSession) {
        if (!session.workerStarted.compareAndSet(false, true)) return

        thread(start = true, name = "CodecRateWorker-${session.codecKey}") {
            val window = ArrayDeque<FrameEvent>()
            val outputWindow = ArrayDeque<Long>()
            var smoothKbps = 0.0
            var smoothFps = 0.0
            val alpha = 0.2

            while (!session.isClosed) {
                var sessionChanged = false
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
                val cutoff = nowElapsedMs - RATE_SAMPLE_WINDOW_MS
                while (window.isNotEmpty() && window.first().pts < cutoff) {
                    window.removeFirst()
                }
                while (outputWindow.isNotEmpty() && outputWindow.first() < cutoff) {
                    outputWindow.removeFirst()
                }

                val inputRate = window.estimateInputRate()
                if (inputRate?.bitrateKbps != null) {
                    smoothKbps = if (smoothKbps == 0.0) {
                        inputRate.bitrateKbps
                    } else {
                        smoothKbps * (1 - alpha) + inputRate.bitrateKbps * alpha
                    }

                    session.bitrateKbps = smoothKbps.roundToInt()
                    session.lastSeenAtMs = System.currentTimeMillis()
                    sessionChanged = true
                }

                val inputFps = inputRate?.frameRate
                val frameRate = inputFps ?: outputWindow.estimateWallClockFrameRate()
                if (frameRate != null) {
                    smoothFps = if (smoothFps == 0.0) {
                        frameRate
                    } else {
                        smoothFps * (1 - alpha) + frameRate * alpha
                    }

                    session.estimatedFrameRate = smoothFps.toFloat()
                    session.lastSeenAtMs = System.currentTimeMillis()
                    sessionChanged = true
                }

                if (nowElapsedMs - session.lastActivityAtElapsedMs > STALE_SESSION_MS) {
                    closeSession(session.codecKey, session)
                }

                if (sessionChanged) {
                    publishRecord(session)
                }

                Thread.sleep(PUBLISH_INTERVAL_MS)
            }

            publishRecord(session)
            logDebug("CodecRateWorker: stop, session=${session.sessionId}")
        }
    }
}

private fun ArrayDeque<FrameEvent>.estimateInputRate(): InputRateEstimate? {
    if (size < 2) return null

    val mediaEstimate = estimateMediaTimelineRate()
    val bitrateKbps = mediaEstimate?.bitrateKbps ?: estimateWallClockBitrate()
    val frameRate = mediaEstimate?.frameRate
    return if (bitrateKbps != null || frameRate != null) {
        InputRateEstimate(bitrateKbps = bitrateKbps, frameRate = frameRate)
    } else {
        null
    }
}

private fun ArrayDeque<FrameEvent>.estimateMediaTimelineRate(): InputRateEstimate? {
    val events = filter { it.size > 0 && it.presentationTimeUs != null }
    if (events.size < 2) return null

    val presentationTimesUs = events
        .mapNotNull { it.presentationTimeUs }
        .distinct()
        .sorted()
    if (presentationTimesUs.size < 2) return null

    val spanUs = presentationTimesUs.last() - presentationTimesUs.first()
    if (spanUs <= 0) return null
    if (spanUs < MIN_RATE_SPAN_MS * 1000 && presentationTimesUs.size < MIN_RATE_FRAME_COUNT) {
        return null
    }

    val averageFrameDurationUs = spanUs.toDouble() / (presentationTimesUs.size - 1)
    val durationUs = spanUs + averageFrameDurationUs
    if (durationUs <= 0.0) return null

    val totalBytes = events.sumOf { it.size.toLong() }
    val bitrateKbps = (totalBytes * 8.0 * 1_000_000.0 / durationUs / 1000.0).validBitrate()
    val frameRate = (presentationTimesUs.size * 1_000_000.0 / durationUs).validFrameRate()
    return if (bitrateKbps != null || frameRate != null) {
        InputRateEstimate(bitrateKbps = bitrateKbps, frameRate = frameRate)
    } else {
        null
    }
}

private fun ArrayDeque<FrameEvent>.estimateWallClockBitrate(): Double? {
    if (size < 2) return null
    val durationMs = estimatedDurationMs(first().pts, last().pts, size) ?: return null
    if (durationMs < MIN_RATE_SPAN_MS && size < MIN_RATE_FRAME_COUNT) return null

    var totalBytes = 0L
    for (event in this) {
        if (event.size > 0) totalBytes += event.size
    }

    return (totalBytes * 8.0 / durationMs).validBitrate()
}

private fun ArrayDeque<Long>.estimateWallClockFrameRate(): Double? {
    if (size < 2) return null
    val durationMs = estimatedDurationMs(first(), last(), size) ?: return null
    if (durationMs < MIN_RATE_SPAN_MS && size < MIN_RATE_FRAME_COUNT) return null

    return (size * 1000.0 / durationMs).validFrameRate()
}

private fun estimatedDurationMs(firstMs: Long, lastMs: Long, count: Int): Double? {
    if (count < 2) return null
    val spanMs = lastMs - firstMs
    if (spanMs <= 0) return null
    return spanMs + spanMs.toDouble() / (count - 1)
}

private fun Double.validBitrate(): Double? {
    return takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000.0 }
}

private fun Double.validFrameRate(): Double? {
    return takeIf { it.isFinite() && it > 0.0 && it <= MAX_ESTIMATED_FRAME_RATE }
}

private data class InputRateEstimate(
    val bitrateKbps: Double?,
    val frameRate: Double?
)
