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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.absinthe.mediacodecat.data.VideoRecordContract
import com.absinthe.mediacodecat.data.VideoRecordSink
import com.absinthe.mediacodecat.manager.SurfaceRegistry
import com.absinthe.mediacodecat.settings.HookSettings
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
    private const val NO_JAVA_CODEC_DIAGNOSTIC_DELAY_MS = 4_000L
    private const val FALLBACK_CODEC_NAME = "Native/Surface fallback"
    private const val NATIVE_CONFIGURE_FLAG_ENCODE = 1
    private const val INLINE_HOOK_STATE_UNAVAILABLE = -1
    private const val INLINE_HOOK_STATE_DISABLED = 0
    private const val INLINE_HOOK_STATE_ENABLED = 1

    private val sessions = ConcurrentHashMap<Int, CodecSession>()
    private val nativeSessions = ConcurrentHashMap<Long, CodecSession>()
    private val nativeSessionPtrs = ConcurrentHashMap<Int, Long>()
    private val fallbackSessions = ConcurrentHashMap<String, CodecSession>()
    private val renderMissingSessionKeys = ConcurrentHashMap.newKeySet<Int>()
    private val hookedSurfaceHolderClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val noJavaCodecDiagnosticKeys = ConcurrentHashMap.newKeySet<String>()
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
    @Volatile
    private var installedPackageName: String = ""
    @Volatile
    private var installedProcessName: String = "unknown"

    fun install(module: XposedModule, packageName: String, processName: String) {
        xposedModule = module
        installedPackageName = packageName
        installedProcessName = processName
        NativeMediaCodecBridge.refreshMediaNdkInlineHooks()

        val codecClass = MediaCodec::class.java
        val intType = Int::class.javaPrimitiveType!!
        val longType = Long::class.javaPrimitiveType!!
        val booleanType = Boolean::class.javaPrimitiveType!!

        installActivityTrackingHooks(module, packageName, processName)
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

    private fun installActivityTrackingHooks(module: XposedModule, packageName: String, processName: String) {
        val activityClass = Activity::class.java
        hookAfter(
            module = module,
            method = activityClass.optionalMethod("onResume"),
            label = "Activity.onResume"
        ) { chain, _ ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            resumedActivity = WeakReference(activity)
            scheduleNoJavaCodecDiagnostic(activity, packageName, processName)
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
            closeFallbackSession(activityFallbackKey(processName, activity))
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
            method = View::class.java.optionalMethod("onDetachedFromWindow"),
            label = "View.onDetachedFromWindow"
        ) { chain ->
            when (val view = chain.thisObject) {
                is SurfaceView -> SurfaceRegistry.unregister(view)
                is TextureView -> SurfaceRegistry.unregister(view)
            }
        }
    }

    private fun scheduleNoJavaCodecDiagnostic(activity: Activity, packageName: String, processName: String) {
        val activityName = activity.javaClass.name
        val diagnosticKey = activityFallbackKey(processName, activity)
        if (!noJavaCodecDiagnosticKeys.add(diagnosticKey)) return

        mainHandler.postDelayed({
            if (resumedActivity?.get() !== activity) return@postDelayed
            if (sessions.isNotEmpty()) return@postDelayed

            val renderView = activity.window.decorView.findLargestRenderView()
            val renderViews = activity.window.decorView.renderViewSummary()
            logInfo(
                "MediaCodecHook: no Java MediaCodec session after activity resume, " +
                    "package=$packageName, process=$processName, activity=$activityName, " +
                    "renderViews=$renderViews; app may use native AMediaCodec/WebRTC or another process"
            )
            if (renderView != null) {
                createFallbackSession(
                    key = diagnosticKey,
                    activity = activity,
                    renderView = renderView,
                    packageName = packageName,
                    processName = processName
                )
            }
        }, NO_JAVA_CODEC_DIAGNOSTIC_DELAY_MS)
    }

    private fun createFallbackSession(
        key: String,
        activity: Activity,
        renderView: View,
        packageName: String,
        processName: String
    ) {
        val width = renderView.width.takeIf { it > 0 } ?: return
        val height = renderView.height.takeIf { it > 0 } ?: return
        val context = activity.applicationContext ?: currentApplicationContext() ?: return
        val surface = renderView.fallbackSurfaceOrNull()
        val session = CodecSession(
            codecKey = key.hashCode(),
            sessionId = "${context.packageName}:fallback:${UUID.randomUUID()}",
            context = context,
            packageName = currentPackageName(context, packageName),
            processName = currentProcessName(processName),
            codecName = FALLBACK_CODEC_NAME,
            format = MediaFormat().apply {
                setString(MediaFormat.KEY_MIME, VideoRecordContract.Records.FALLBACK_SURFACE_MIME)
                setInteger(MediaFormat.KEY_WIDTH, width)
                setInteger(MediaFormat.KEY_HEIGHT, height)
                setString(VideoRecordContract.Records.FALLBACK_SOURCE_KEY, "render-view")
                setString(VideoRecordContract.Records.FALLBACK_VIEW_CLASS_KEY, renderView.javaClass.name)
                setString(VideoRecordContract.Records.FALLBACK_ACTIVITY_CLASS_KEY, activity.javaClass.name)
                setString(
                    VideoRecordContract.Records.FALLBACK_SURFACE_CLASS_KEY,
                    surface?.javaClass?.name ?: renderView.javaClass.name
                )
                setInteger(
                    VideoRecordContract.Records.FALLBACK_SECURE_WINDOW_KEY,
                    if (activity.window.isSecureWindow()) 1 else 0
                )
            },
            surface = surface,
            firstSeenAtMs = System.currentTimeMillis()
        )
        session.surfaceId = surface?.stableId() ?: renderView.stableViewId()

        if (fallbackSessions.putIfAbsent(key, session) != null) return

        publishRecord(session, requireMetrics = false)
        coverCapture.scheduleDelayedCapture(session, "activityFallback")
        logInfo(
            "MediaCodecHook: fallback render view capture scheduled, " +
                "session=${session.sessionId}, view=${renderView.renderViewSummary()}, surface=${session.surfaceId}"
        )
    }

    private fun closeFallbackSession(key: String) {
        noJavaCodecDiagnosticKeys.remove(key)
        val session = fallbackSessions.remove(key) ?: return
        session.close()
        publishRecord(session, requireMetrics = false)
    }

    private fun activityFallbackKey(processName: String, activity: Activity): String {
        return "$processName:${activity.javaClass.name}"
    }

    fun onNativeConfigured(
        codecPtr: Long,
        codecName: String?,
        mime: String?,
        width: Int,
        height: Int,
        flags: Int,
        nativeWindowPtr: Long
    ) {
        if (codecPtr == 0L) return
        val normalizedMime = mime?.trim()?.takeIf { it.startsWith("video/") } ?: return
        closeNativeSession(codecPtr)

        if (flags and NATIVE_CONFIGURE_FLAG_ENCODE != 0) {
            logDebug("NativeMediaCodecHook: skip video encoder codec=$codecName, mime=$normalizedMime")
            return
        }

        val context = currentApplicationContext() ?: run {
            logDebug("NativeMediaCodecHook: skip native video record, app context is null")
            return
        }
        val codecKey = nativeCodecKey(codecPtr)
        val recordCodecName = nativeCodecName(codecName)
        val format = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, normalizedMime)
            if (width > 0) setInteger(MediaFormat.KEY_WIDTH, width)
            if (height > 0) setInteger(MediaFormat.KEY_HEIGHT, height)
            setString("mediacodecat-native-codec-ptr", codecPtr.hexPointer())
            if (nativeWindowPtr != 0L) {
                setString("mediacodecat-native-window", nativeWindowPtr.hexPointer())
            }
        }
        val session = CodecSession(
            codecKey = codecKey,
            sessionId = "${context.packageName}:native:${UUID.randomUUID()}",
            context = context,
            packageName = currentPackageName(context, installedPackageName),
            processName = currentProcessName(installedProcessName),
            codecName = recordCodecName,
            format = format,
            surface = null,
            firstSeenAtMs = System.currentTimeMillis()
        )
        session.surfaceId = nativeWindowPtr.takeIf { it != 0L }?.let { "nativeWindow@${it.toString(16)}" }

        nativeSessions[codecPtr] = session
        nativeSessionPtrs[codecKey] = codecPtr
        publishRecord(session, requireMetrics = false)
        rateWorker.start(session)
        logInfo(
            "NativeMediaCodecHook: configured video codec=$recordCodecName, " +
                "mime=$normalizedMime, size=${width}x$height, window=${session.surfaceId}, " +
                "session=${session.sessionId}"
        )
    }

    fun onNativeInputBufferQueued(codecPtr: Long, size: Int, presentationTimeUs: Long) {
        if (size <= 0) return
        nativeSessions[codecPtr]?.let { session ->
            val frameCount = session.offerInput(size, presentationTimeUs)
            coverCapture.maybeCapture(session, frameCount, "nativeQueueInputBuffer")
        }
    }

    fun onNativeOutputBufferReleased(codecPtr: Long, render: Boolean) {
        val session = nativeSessions[codecPtr] ?: return
        val frameCount = if (render) {
            session.offerRenderedFrame()
        } else {
            session.offerNonRenderedOutput()
        }
        if (render && session.coverFrameLogged.compareAndSet(false, true)) {
            logDebug(
                "NativeMediaCodecHook: rendered video frame " +
                    "session=${session.sessionId}, window=${session.surfaceId}"
            )
        }
        coverCapture.maybeCapture(session, frameCount, "nativeReleaseOutputBuffer")
    }

    fun onNativeDeleted(codecPtr: Long) {
        closeNativeSession(codecPtr)
    }

    fun mediaNdkInlineHookState(): Int {
        val context = currentApplicationContext() ?: return INLINE_HOOK_STATE_UNAVAILABLE
        val enabled = HookSettings.queryNativeMediaNdkInlineHookEnabled(context)
            ?: return INLINE_HOOK_STATE_UNAVAILABLE
        return if (enabled) INLINE_HOOK_STATE_ENABLED else INLINE_HOOK_STATE_DISABLED
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

    private fun publishRecord(session: CodecSession, requireMetrics: Boolean = true) {
        if (!VideoRecordSink.upsert(session.context, session.toRecord(), requireMetrics = requireMetrics) &&
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
        val nativePtr = nativeSessionPtrs.remove(codecKey)
        nativePtr?.let { nativeSessions.remove(it, session) }
        session.close()
        publishRecord(session, requireMetrics = nativePtr == null)
    }

    private fun closeNativeSession(codecPtr: Long) {
        val session = nativeSessions.remove(codecPtr) ?: return
        nativeSessionPtrs.remove(session.codecKey, codecPtr)
        session.close()
        publishRecord(session, requireMetrics = false)
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

    private fun nativeCodecKey(codecPtr: Long): Int {
        val folded = codecPtr xor (codecPtr ushr 32)
        return folded.toInt()
    }

    private fun nativeCodecName(codecName: String?): String {
        val suffix = codecName?.takeIf { it.isNotBlank() }
        return if (suffix == null) {
            VideoRecordContract.Records.NATIVE_CODEC_NAME_PREFIX
        } else {
            "${VideoRecordContract.Records.NATIVE_CODEC_NAME_PREFIX}: $suffix"
        }
    }

    private fun Long.hexPointer(): String = "0x${toString(16)}"

    private fun View.renderViewSummary(): String {
        val summaries = mutableListOf<String>()
        collectRenderViewSummaries(this, summaries)
        return summaries.takeIf { it.isNotEmpty() }?.joinToString(separator = "; ") ?: "none"
    }

    private fun View.findLargestRenderView(): View? {
        val renderViews = mutableListOf<View>()
        collectRenderViews(this, renderViews)
        return renderViews.maxByOrNull { it.width.toLong() * it.height.toLong() }
    }

    private fun collectRenderViews(view: View, out: MutableList<View>) {
        if (view.isRenderViewCandidate()) {
            out += view
        }

        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) {
            collectRenderViews(view.getChildAt(index), out)
        }
    }

    private fun collectRenderViewSummaries(view: View, out: MutableList<String>) {
        if (view is SurfaceView || view is TextureView) {
            out += "${view.javaClass.name}(${view.width}x${view.height}, " +
                "visible=${view.visibility == View.VISIBLE}, attached=${view.isAttachedToWindow})"
        }

        if (out.size >= 4 || view !is ViewGroup) return
        for (index in 0 until view.childCount) {
            collectRenderViewSummaries(view.getChildAt(index), out)
            if (out.size >= 4) return
        }
    }

    private fun View.isRenderViewCandidate(): Boolean {
        return (this is SurfaceView || this is TextureView) &&
            visibility == View.VISIBLE &&
            isAttachedToWindow &&
            width > 0 &&
            height > 0
    }

    private fun View.fallbackSurfaceOrNull(): Surface? {
        return when (this) {
            is SurfaceView -> runCatching {
                holder.surface
                    ?.takeIf { it.isValid }
                    ?.also { SurfaceRegistry.register(it, this, this) }
            }.getOrNull()

            is TextureView -> {
                runCatching {
                    surfaceTexture?.let { SurfaceRegistry.register(it, this) }
                }
                null
            }

            else -> null
        }
    }

    private fun android.view.Window.isSecureWindow(): Boolean {
        return attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }

    private fun View.stableViewId(): String {
        return "view:${javaClass.name}@${System.identityHashCode(this).toString(16)}"
    }
}
