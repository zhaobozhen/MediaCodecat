package com.absinthe.mediacodecat.hook

import android.app.Application
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Surface
import com.absinthe.mediacodecat.data.VideoRecordSink
import com.absinthe.mediacodecat.manager.SurfaceRegistry
import com.absinthe.mediacodecat.model.FrameEvent
import com.absinthe.mediacodecat.model.VideoRecord
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.roundToInt

object MediaCodecHook : YukiBaseHooker() {

    private const val WINDOW_MS = 1000L
    private const val STALE_SESSION_MS = 60_000L

    private val sessions = ConcurrentHashMap<Int, CodecSession>()

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

                        sessions[instance<MediaCodec>().key()]?.offer(size)
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

                        sessions[instance<MediaCodec>().key()]?.offer(size)
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
            var smoothKbps = 0.0
            val alpha = 0.2

            while (!session.isClosed) {
                var event = session.events.poll()
                while (event != null) {
                    window.addLast(event)
                    event = session.events.poll()
                }

                val nowElapsedMs = SystemClock.elapsedRealtime()
                val cutoff = nowElapsedMs - WINDOW_MS
                while (window.isNotEmpty() && window.first().pts < cutoff) {
                    window.removeFirst()
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
        val content = record.toOverlayText()

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

    private fun VideoRecord.toOverlayText(): String = buildString {
        append(mime)
        if (width != null && height != null) append(" ${width}x$height")
        codecName?.let { appendLine(); append("Codec: ").append(it) }
        appendLine()
        append("Package: ").append(packageName)
        appendLine()
        append("Bitrate: ").append(bitrateKbps?.let { "$it kbps" } ?: "-")
    }

    private fun MediaCodec.key(): Int = System.identityHashCode(this)

    private fun MediaCodec.safeName(): String? = runCatching { name }.getOrNull()

    private fun MediaFormat.mime(): String? = runCatching {
        if (containsKey(MediaFormat.KEY_MIME)) getString(MediaFormat.KEY_MIME) else null
    }.getOrNull()

    private fun MediaCodec.CryptoInfo.totalBytes(): Int {
        val clearBytes = numBytesOfClearData?.sum() ?: 0
        val encryptedBytes = numBytesOfEncryptedData?.sum() ?: 0
        return clearBytes + encryptedBytes
    }

    private fun Surface.stableId(): String {
        return "${javaClass.name}@${System.identityHashCode(this).toString(16)}"
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
        val format: MediaFormat,
        @Volatile var surface: Surface?,
        val firstSeenAtMs: Long
    ) {
        val events = ConcurrentLinkedQueue<FrameEvent>()
        val workerStarted = AtomicBoolean(false)
        val persistFailureLogged = AtomicBoolean(false)

        @Volatile var surfaceId: String? = surface?.stableId()
        @Volatile var bitrateKbps: Int? = null
        @Volatile var lastSeenAtMs: Long = firstSeenAtMs
        @Volatile var lastInputAtElapsedMs: Long = 0L

        private val closed = AtomicBoolean(false)
        val isClosed: Boolean get() = closed.get()

        fun offer(size: Int) {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            events.offer(FrameEvent(pts = nowElapsedMs, size = size))
            lastInputAtElapsedMs = nowElapsedMs
            lastSeenAtMs = System.currentTimeMillis()
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
            format = format,
            firstSeenAtMs = firstSeenAtMs,
            lastSeenAtMs = lastSeenAtMs,
            bitrateKbps = bitrateKbps
        )
    }
}
