package com.absinthe.mediacodecat.hook

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import com.absinthe.mediacodecat.data.VideoRecordSink
import com.absinthe.mediacodecat.manager.SurfaceRegistry
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MediaCodecHook {

    private const val TAG = "MediaCodecat"

    private val sessions = ConcurrentHashMap<Int, CodecSession>()
    private val renderMissingSessionKeys = ConcurrentHashMap.newKeySet<Int>()
    private val hookedSurfaceHolderClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val rateWorker by lazy {
        CodecRateWorker(
            publishRecord = ::publishRecord,
            closeSession = { codecKey, session -> closeSession(codecKey, session) },
            logDebug = { message -> logDebug(message) }
        )
    }
    private val coverCapture by lazy {
        MediaCodecCoverCapture(
            mainHandler = mainHandler,
            currentWindowProvider = { resumedActivity?.get()?.window },
            logDebug = { message -> logDebug(message) }
        )
    }
    @Volatile
    private var xposedModule: XposedModule? = null
    @Volatile
    private var resumedActivity: WeakReference<Activity>? = null

    fun install(module: XposedModule, packageName: String, processName: String) {
        xposedModule = module

        val codecClass = MediaCodec::class.java
        val intType = Int::class.javaPrimitiveType!!
        val longType = Long::class.javaPrimitiveType!!
        val booleanType = Boolean::class.javaPrimitiveType!!

        installActivityTrackingHooks(module)
        installSurfaceTrackingHooks(module, intType)

        hookAfter(
            module = module,
            method = codecClass.optionalMethod(
                "configure",
                MediaFormat::class.java,
                Surface::class.java,
                MediaCrypto::class.java,
                intType
            ),
            label = "MediaCodec.configure(public)"
        ) { chain, _ ->
            onConfigured(
                chain = chain,
                packageName = packageName,
                processName = processName,
                configureFlags = chain.getArg(3) as? Int ?: 0
            )
        }

        val descramblerClass = optionalClass("android.media.MediaDescrambler")
        if (descramblerClass != null) {
            hookAfter(
                module = module,
                method = codecClass.optionalMethod(
                    "configure",
                    MediaFormat::class.java,
                    Surface::class.java,
                    intType,
                    descramblerClass
                ),
                label = "MediaCodec.configure(descrambler)"
            ) { chain, _ ->
                onConfigured(
                    chain = chain,
                    packageName = packageName,
                    processName = processName,
                    configureFlags = chain.getArg(2) as? Int ?: 0
                )
            }
        }

        val iHwBinderClass = optionalClass("android.os.IHwBinder")
        if (iHwBinderClass != null) {
            hookAfter(
                module = module,
                method = codecClass.optionalMethod(
                    "configure",
                    MediaFormat::class.java,
                    Surface::class.java,
                    MediaCrypto::class.java,
                    iHwBinderClass,
                    intType
                ),
                label = "MediaCodec.configure(hidden)"
            ) { chain, _ ->
                onConfigured(
                    chain = chain,
                    packageName = packageName,
                    processName = processName,
                    configureFlags = chain.getArg(4) as? Int ?: 0
                )
            }
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod("setOutputSurface", Surface::class.java),
            label = "MediaCodec.setOutputSurface"
        ) { chain, _ ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            val surface = chain.getArg(0) as? Surface
            val session = sessions[codec.key()] ?: return@hookAfter

            session.surface = surface
            session.surfaceId = surface?.stableId()
            session.lastSeenAtMs = System.currentTimeMillis()

            publishRecord(session)
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod("start"),
            label = "MediaCodec.start"
        ) { chain, _ ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            refreshCodecFormats(codec, "start")
            sessions[codec.key()]?.let { coverCapture.scheduleDelayedCapture(it, "start") }
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod("getInputFormat"),
            label = "MediaCodec.getInputFormat"
        ) { chain, result ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            val format = result as? MediaFormat ?: return@hookAfter
            mergeCodecFormat(codec, format, "getInputFormat")
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod("getOutputFormat"),
            label = "MediaCodec.getOutputFormat"
        ) { chain, result ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            val format = result as? MediaFormat ?: return@hookAfter
            mergeCodecFormat(codec, format, "getOutputFormat")
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod("getOutputFormat", intType),
            label = "MediaCodec.getOutputFormat(index)"
        ) { chain, result ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            val format = result as? MediaFormat ?: return@hookAfter
            mergeCodecFormat(codec, format, "getOutputFormat(index)")
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod(
                "queueInputBuffer",
                intType,
                intType,
                intType,
                longType,
                intType
            ),
            label = "MediaCodec.queueInputBuffer"
        ) { chain, _ ->
            val size = chain.getArg(2) as? Int ?: return@hookAfter
            if (size <= 0) return@hookAfter
            val presentationTimeUs = chain.getArg(3) as? Long

            sessions[(chain.thisObject as? MediaCodec)?.key()]?.let { session ->
                val frameCount = session.offerInput(size, presentationTimeUs)
                coverCapture.maybeCapture(session, frameCount, "queueInputBuffer")
            }
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod(
                "queueSecureInputBuffer",
                intType,
                intType,
                MediaCodec.CryptoInfo::class.java,
                longType,
                intType
            ),
            label = "MediaCodec.queueSecureInputBuffer"
        ) { chain, _ ->
            val size = (chain.getArg(2) as? MediaCodec.CryptoInfo)?.totalBytes() ?: 0
            if (size <= 0) return@hookAfter
            val presentationTimeUs = chain.getArg(3) as? Long

            sessions[(chain.thisObject as? MediaCodec)?.key()]?.let { session ->
                val frameCount = session.offerInput(size, presentationTimeUs)
                coverCapture.maybeCapture(session, frameCount, "queueSecureInputBuffer")
            }
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod(
                "dequeueOutputBuffer",
                MediaCodec.BufferInfo::class.java,
                longType
            ),
            label = "MediaCodec.dequeueOutputBuffer"
        ) { chain, result ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            if (result as? Int == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                refreshCodecFormats(codec, "dequeueOutputBuffer(format-changed)")
            }
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod("releaseOutputBuffer", intType, booleanType),
            label = "MediaCodec.releaseOutputBuffer(render)"
        ) { chain, _ ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            val render = chain.getArg(1) as? Boolean ?: return@hookAfter
            onReleasedOutputBuffer(codec, render, "releaseOutputBuffer(render)")
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod("releaseOutputBuffer", intType, longType),
            label = "MediaCodec.releaseOutputBuffer(timestamp)"
        ) { chain, _ ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            onRenderedFrame(codec, "releaseOutputBuffer(timestamp)")
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod(
                "releaseOutputBufferInternal",
                intType,
                booleanType,
                booleanType,
                longType
            ),
            label = "MediaCodec.releaseOutputBufferInternal"
        ) { chain, _ ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            val render = chain.getArg(1) as? Boolean ?: return@hookAfter
            onReleasedOutputBuffer(codec, render, "releaseOutputBufferInternal")
        }

        hookAfter(
            module = module,
            method = codecClass.optionalMethod(
                "releaseOutputBuffer",
                intType,
                booleanType,
                booleanType,
                longType
            ),
            label = "MediaCodec.releaseOutputBuffer(native)"
        ) { chain, _ ->
            val codec = chain.thisObject as? MediaCodec ?: return@hookAfter
            val render = chain.getArg(1) as? Boolean ?: return@hookAfter
            onReleasedOutputBuffer(codec, render, "releaseOutputBuffer(native)")
        }

        hookBefore(module, codecClass.optionalMethod("stop"), "MediaCodec.stop") { chain ->
            (chain.thisObject as? MediaCodec)?.let(::closeSession)
        }
        hookBefore(module, codecClass.optionalMethod("reset"), "MediaCodec.reset") { chain ->
            (chain.thisObject as? MediaCodec)?.let(::closeSession)
        }
        hookBefore(module, codecClass.optionalMethod("release"), "MediaCodec.release") { chain ->
            (chain.thisObject as? MediaCodec)?.let(::closeSession)
        }

        logInfo("MediaCodecHook: installed, package=$packageName, process=$processName")
    }

    private fun installActivityTrackingHooks(module: XposedModule) {
        val activityClass = Activity::class.java
        hookAfter(
            module = module,
            method = activityClass.optionalMethod("onResume"),
            label = "Activity.onResume"
        ) { chain, _ ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            resumedActivity = WeakReference(activity)
        }

        hookBefore(
            module = module,
            method = activityClass.optionalMethod("onDestroy"),
            label = "Activity.onDestroy"
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookBefore
            if (resumedActivity?.get() === activity) {
                resumedActivity = null
            }
        }
    }

    private fun installSurfaceTrackingHooks(module: XposedModule, intType: Class<*>) {
        val surfaceClass = Surface::class.java
        val surfaceTextureClass = SurfaceTexture::class.java
        val surfaceViewClass = SurfaceView::class.java
        val textureViewClass = TextureView::class.java

        hookAfter(
            module = module,
            method = surfaceClass.optionalConstructor(surfaceTextureClass),
            label = "Surface(SurfaceTexture)"
        ) { chain, _ ->
            val surface = chain.thisObject as? Surface ?: return@hookAfter
            val surfaceTexture = chain.getArg(0) as? SurfaceTexture ?: return@hookAfter
            SurfaceRegistry.register(surface, surfaceTexture)
        }

        hookBefore(
            module = module,
            method = surfaceClass.optionalMethod("release"),
            label = "Surface.release"
        ) { chain ->
            (chain.thisObject as? Surface)?.let(SurfaceRegistry::unregister)
        }

        hookAfter(
            module = module,
            method = surfaceTextureClass.optionalMethod("setDefaultBufferSize", intType, intType),
            label = "SurfaceTexture.setDefaultBufferSize"
        ) { chain, _ ->
            val surfaceTexture = chain.thisObject as? SurfaceTexture ?: return@hookAfter
            val width = chain.getArg(0) as? Int ?: return@hookAfter
            val height = chain.getArg(1) as? Int ?: return@hookAfter
            SurfaceRegistry.setSurfaceTextureSize(surfaceTexture, width, height)
        }

        hookBefore(
            module = module,
            method = surfaceTextureClass.optionalMethod("release"),
            label = "SurfaceTexture.release"
        ) { chain ->
            (chain.thisObject as? SurfaceTexture)?.let(SurfaceRegistry::unregister)
        }

        hookAfter(
            module = module,
            method = surfaceViewClass.optionalMethod("getHolder"),
            label = "SurfaceView.getHolder"
        ) { chain, result ->
            val surfaceView = chain.thisObject as? SurfaceView ?: return@hookAfter
            val holder = result as? SurfaceHolder ?: return@hookAfter
            SurfaceRegistry.register(holder, surfaceView)
            installSurfaceHolderHook(module, holder.javaClass)
        }

        hookBefore(
            module = module,
            method = surfaceViewClass.optionalMethod("onDetachedFromWindow"),
            label = "SurfaceView.onDetachedFromWindow"
        ) { chain ->
            (chain.thisObject as? SurfaceView)?.let(SurfaceRegistry::unregister)
        }

        hookAfter(
            module = module,
            method = textureViewClass.optionalMethod("getSurfaceTexture"),
            label = "TextureView.getSurfaceTexture"
        ) { chain, result ->
            val textureView = chain.thisObject as? TextureView ?: return@hookAfter
            val surfaceTexture = result as? SurfaceTexture ?: return@hookAfter
            SurfaceRegistry.register(surfaceTexture, textureView)
        }

        hookAfter(
            module = module,
            method = textureViewClass.optionalMethod("setSurfaceTexture", surfaceTextureClass),
            label = "TextureView.setSurfaceTexture"
        ) { chain, _ ->
            val textureView = chain.thisObject as? TextureView ?: return@hookAfter
            val surfaceTexture = chain.getArg(0) as? SurfaceTexture ?: return@hookAfter
            SurfaceRegistry.register(surfaceTexture, textureView)
        }

        hookBefore(
            module = module,
            method = textureViewClass.optionalMethod("onDetachedFromWindow"),
            label = "TextureView.onDetachedFromWindow"
        ) { chain ->
            (chain.thisObject as? TextureView)?.let(SurfaceRegistry::unregister)
        }
    }

    private fun installSurfaceHolderHook(module: XposedModule, holderClass: Class<*>) {
        if (!hookedSurfaceHolderClasses.add(holderClass)) return

        hookAfter(
            module = module,
            method = holderClass.optionalMethod("getSurface"),
            label = "SurfaceHolder.getSurface(${holderClass.name})"
        ) { chain, result ->
            val holder = chain.thisObject as? SurfaceHolder ?: return@hookAfter
            val surface = result as? Surface ?: return@hookAfter
            val surfaceView = SurfaceRegistry.findSurfaceView(holder) ?: return@hookAfter
            SurfaceRegistry.register(surface, surfaceView, surfaceView)
        }
    }

    private fun onConfigured(
        chain: XposedInterface.Chain,
        packageName: String,
        processName: String,
        configureFlags: Int
    ) {
        val codec = chain.thisObject as? MediaCodec ?: return
        val format = chain.getArg(0) as? MediaFormat ?: return
        val mime = format.mime() ?: return

        closeSession(codec)

        if (!mime.startsWith("video/")) return
        if (configureFlags and MediaCodec.CONFIGURE_FLAG_ENCODE != 0) {
            logDebug("MediaCodecHook: skip video encoder codec=${codec.safeName()}, format=$format")
            return
        }

        val context = currentApplicationContext() ?: run {
            logDebug("MediaCodecHook: skip video record, app context is null")
            return
        }
        val surface = chain.getArg(1) as? Surface
        val session = CodecSession(
            codecKey = codec.key(),
            sessionId = newSessionId(context, codec),
            context = context,
            packageName = currentPackageName(context, packageName),
            processName = currentProcessName(processName),
            codecName = codec.safeName(),
            format = MediaFormat(format),
            surface = surface,
            firstSeenAtMs = System.currentTimeMillis()
        )

        sessions[session.codecKey] = session
        logInfo(
            "MediaCodecHook: configured video codec=${session.codecName}, " +
                    "format=$format, surface=$surface, session=${session.sessionId}"
        )

        publishRecord(session)
        rateWorker.start(session)
    }

    private fun hookAfter(
        module: XposedModule,
        method: Executable?,
        label: String,
        onAfter: (XposedInterface.Chain, Any?) -> Unit
    ) {
        hookMethod(module, method, label) { chain ->
            val result = chain.proceed()
            onAfter(chain, result)
            result
        }
    }

    private fun hookBefore(
        module: XposedModule,
        method: Executable?,
        label: String,
        onBefore: (XposedInterface.Chain) -> Unit
    ) {
        hookMethod(module, method, label) { chain ->
            onBefore(chain)
            chain.proceed()
        }
    }

    private fun hookMethod(
        module: XposedModule,
        method: Executable?,
        label: String,
        intercept: (XposedInterface.Chain) -> Any?
    ) {
        if (method == null) {
            logDebug("MediaCodecHook: skip missing method $label")
            return
        }
        runCatching {
            module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain -> intercept(chain) }
        }.onFailure {
            logWarn("MediaCodecHook: failed to hook $label", it)
        }
    }

    private fun Class<*>.optionalMethod(name: String, vararg parameterTypes: Class<*>): Method? {
        return runCatching {
            getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
        }.getOrElse {
            runCatching {
                getMethod(name, *parameterTypes).apply { isAccessible = true }
            }.getOrNull()
        }
    }

    private fun Class<*>.optionalConstructor(vararg parameterTypes: Class<*>): Constructor<*>? {
        return runCatching {
            getDeclaredConstructor(*parameterTypes).apply { isAccessible = true }
        }.getOrNull()
    }

    private fun optionalClass(name: String): Class<*>? {
        return runCatching { Class.forName(name) }.getOrNull()
    }

    private fun logDebug(message: String, throwable: Throwable? = null) {
        log(Log.DEBUG, message, throwable)
    }

    private fun logInfo(message: String, throwable: Throwable? = null) {
        log(Log.INFO, message, throwable)
    }

    private fun logWarn(message: String, throwable: Throwable? = null) {
        log(Log.WARN, message, throwable)
    }

    private fun log(priority: Int, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.println(priority, TAG, message)
        } else {
            Log.println(priority, TAG, "$message\n${Log.getStackTraceString(throwable)}")
        }
        runCatching {
            if (throwable == null) {
                xposedModule?.log(priority, TAG, message)
            } else {
                xposedModule?.log(priority, TAG, message, throwable)
            }
        }
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

        session.mergeFormat(format) { key, throwable ->
            logDebug("MediaCodecHook: skip format key=$key while merging", throwable)
        }
        session.lastSeenAtMs = System.currentTimeMillis()
        logDebug(
            "MediaCodecHook: merged $source format, session=${session.sessionId}, format=$format"
        )
        publishRecord(session)
    }

    private fun publishRecord(session: CodecSession) {
        if (!VideoRecordSink.upsert(session.context, session.toRecord()) &&
            session.persistFailureLogged.compareAndSet(false, true)
        ) {
            logDebug("MediaCodecHook: failed to persist video record, session=${session.sessionId}")
        }
    }

    private fun onRenderedFrame(codec: MediaCodec, source: String) {
        val codecKey = codec.key()
        val session = sessions[codecKey] ?: run {
            if (renderMissingSessionKeys.add(codecKey)) {
                logDebug("MediaCodecHook: rendered frame has no video session, source=$source, codecKey=$codecKey")
            }
            return
        }
        val frameCount = session.offerRenderedFrame()
        if (session.coverFrameLogged.compareAndSet(false, true)) {
            logDebug(
                "MediaCodecHook: rendered video frame source=$source, " +
                        "surface=${session.surface}, session=${session.sessionId}"
            )
        }
        coverCapture.maybeCapture(session, frameCount, source)
    }

    private fun onReleasedOutputBuffer(codec: MediaCodec, render: Boolean, source: String) {
        if (render) {
            onRenderedFrame(codec, source)
            return
        }

        sessions[codec.key()]?.let { session ->
            val frameCount = session.offerNonRenderedOutput()
            if (session.coverNonRenderLogged.compareAndSet(false, true)) {
                logDebug(
                    "MediaCodecHook: non-render output release source=$source, " +
                            "session=${session.sessionId}"
                )
            }
            coverCapture.maybeCapture(session, frameCount, source)
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
    }

    private fun newSessionId(context: Context, codec: MediaCodec): String {
        return "${context.packageName}:${codec.key()}:${UUID.randomUUID()}"
    }

    private fun currentApplicationContext(): Context? {
        val activityThreadApplication = runCatching {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentApplication")
                .invoke(null) as? Application
        }.getOrNull()

        return activityThreadApplication?.applicationContext
    }

    private fun currentPackageName(context: Context, fallbackPackageName: String): String {
        return fallbackPackageName.takeIf { it.isNotBlank() } ?: context.packageName
    }

    private fun currentProcessName(fallbackProcessName: String): String {
        return fallbackProcessName.takeIf { it.isNotBlank() && it != "unknown" }
            ?: Application.getProcessName().takeIf { it.isNotBlank() }
            ?: "unknown"
    }

}
