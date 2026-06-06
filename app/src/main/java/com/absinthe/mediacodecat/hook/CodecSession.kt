package com.absinthe.mediacodecat.hook

import android.content.Context
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Surface
import com.absinthe.mediacodecat.model.FrameEvent
import com.absinthe.mediacodecat.model.VideoRecord
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class CodecSession(
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
    val coverDelayedCaptureScheduled = AtomicBoolean(false)
    val coverAttempts = AtomicInteger(0)
    val lastCoverAttemptAtElapsedMs = AtomicLong(0L)

    @Volatile var surfaceId: String? = surface?.stableId()
    @Volatile var bitrateKbps: Int? = null
    @Volatile var estimatedFrameRate: Float? = null
    @Volatile var lastSeenAtMs: Long = firstSeenAtMs
    val firstSeenAtElapsedMs: Long = SystemClock.elapsedRealtime()
    @Volatile var lastActivityAtElapsedMs: Long = firstSeenAtElapsedMs

    private val closed = AtomicBoolean(false)
    val isClosed: Boolean get() = closed.get()

    fun mergeFormat(newer: MediaFormat, onKeyFailure: (String, Throwable) -> Unit) {
        synchronized(formatLock) {
            format = format.mergeWith(newer, onKeyFailure)
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
        lastActivityAtElapsedMs = nowElapsedMs
        lastSeenAtMs = System.currentTimeMillis()
        return inputFrameCount.incrementAndGet()
    }

    fun offerRenderedFrame(): Int {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        renderedFrameEvents.offer(nowElapsedMs)
        lastActivityAtElapsedMs = nowElapsedMs
        lastSeenAtMs = System.currentTimeMillis()
        return renderedFrameCount.incrementAndGet()
    }

    fun offerNonRenderedOutput(): Int {
        lastActivityAtElapsedMs = SystemClock.elapsedRealtime()
        lastSeenAtMs = System.currentTimeMillis()
        return nonRenderedOutputCount.incrementAndGet()
    }

    fun coverDisplaySize(sizeHint: Pair<Int, Int>?): Pair<Int, Int>? {
        val formatSize = synchronized(formatLock) { format.captureDisplaySize() }
        return formatSize ?: sizeHint
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
