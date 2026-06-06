#include <android/log.h>
#include <android/native_window.h>
#include <dlfcn.h>
#include <jni.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaCrypto.h>
#include <media/NdkMediaFormat.h>

#include <atomic>
#include <climits>
#include <cstdarg>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>

namespace {

constexpr const char *kTag = "MediaCodecat";
constexpr const char *kBridgeClassName = "com/absinthe/mediacodecat/hook/NativeMediaCodecBridge";
constexpr const char *kMediaCodecLibrary = "libmediandk.so";
constexpr jint kInlineHookStateUnavailable = -1;
constexpr jint kInlineHookStateDisabled = 0;
constexpr jint kInlineHookStateEnabled = 1;

using HookFunType = int (*)(void *func, void *replace, void **backup);
using UnhookFunType = int (*)(void *func);
using NativeOnModuleLoaded = void (*)(const char *name, void *handle);

struct NativeAPIEntries {
    uint32_t version;
    HookFunType hook_func;
    UnhookFunType unhook_func;
};

using ConfigureFn = media_status_t (*)(
    AMediaCodec *codec,
    const AMediaFormat *format,
    ANativeWindow *surface,
    AMediaCrypto *crypto,
    uint32_t flags
);
using StartFn = media_status_t (*)(AMediaCodec *codec);
using QueueInputBufferFn = media_status_t (*)(
    AMediaCodec *codec,
    size_t idx,
    off_t offset,
    size_t size,
    uint64_t time,
    uint32_t flags
);
using ReleaseOutputBufferFn = media_status_t (*)(AMediaCodec *codec, size_t idx, bool render);
using ReleaseOutputBufferAtTimeFn = media_status_t (*)(AMediaCodec *codec, size_t idx, int64_t timestamp_ns);
using StopFn = media_status_t (*)(AMediaCodec *codec);
using DeleteFn = media_status_t (*)(AMediaCodec *codec);
using GetNameFn = media_status_t (*)(AMediaCodec *codec, char **out_name);
using ReleaseNameFn = void (*)(AMediaCodec *codec, char *name);
using FormatGetStringFn = bool (*)(AMediaFormat *format, const char *key, const char **out_value);
using FormatGetInt32Fn = bool (*)(AMediaFormat *format, const char *key, int32_t *out);

HookFunType g_hook_func = nullptr;
JavaVM *g_vm = nullptr;
jclass g_bridge_class = nullptr;
jmethodID g_on_configured = nullptr;
jmethodID g_on_input_buffer_queued = nullptr;
jmethodID g_on_output_buffer_released = nullptr;
jmethodID g_on_deleted = nullptr;
jmethodID g_media_ndk_inline_hook_state = nullptr;

std::atomic_bool g_mediandk_hooks_installed{false};
std::atomic_int g_inline_hook_state{kInlineHookStateUnavailable};
std::atomic_bool g_inline_hook_disabled_logged{false};
std::atomic_bool g_inline_hook_unavailable_logged{false};
std::mutex g_codec_info_mutex;
std::unordered_map<AMediaCodec *, std::string> g_codec_names;
std::unordered_map<AMediaCodec *, std::string> g_codec_mimes;

ConfigureFn original_configure = nullptr;
StartFn original_start = nullptr;
QueueInputBufferFn original_queue_input_buffer = nullptr;
ReleaseOutputBufferFn original_release_output_buffer = nullptr;
ReleaseOutputBufferAtTimeFn original_release_output_buffer_at_time = nullptr;
StopFn original_stop = nullptr;
DeleteFn original_delete = nullptr;
GetNameFn original_get_name = nullptr;
ReleaseNameFn original_release_name = nullptr;
FormatGetStringFn original_format_get_string = nullptr;
FormatGetInt32Fn original_format_get_int32 = nullptr;

void log_info(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(ANDROID_LOG_INFO, kTag, fmt, args);
    va_end(args);
}

void log_debug(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(ANDROID_LOG_DEBUG, kTag, fmt, args);
    va_end(args);
}

void log_warn(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(ANDROID_LOG_WARN, kTag, fmt, args);
    va_end(args);
}

bool ends_with(const char *value, const char *suffix) {
    if (value == nullptr || suffix == nullptr) return false;
    const size_t value_len = std::strlen(value);
    const size_t suffix_len = std::strlen(suffix);
    return value_len >= suffix_len && std::strcmp(value + value_len - suffix_len, suffix) == 0;
}

JNIEnv *current_env(bool *should_detach) {
    *should_detach = false;
    if (g_vm == nullptr) return nullptr;

    JNIEnv *env = nullptr;
    const jint status = g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (status == JNI_OK) return env;
    if (status != JNI_EDETACHED) return nullptr;

    if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    *should_detach = true;
    return env;
}

void detach_if_needed(bool should_detach) {
    if (should_detach && g_vm != nullptr) {
        g_vm->DetachCurrentThread();
    }
}

bool resolve_bridge(JNIEnv *env) {
    if (g_bridge_class != nullptr) return true;

    jclass local_class = env->FindClass(kBridgeClassName);
    if (local_class == nullptr) {
        env->ExceptionClear();
        log_warn("NativeMediaCodecHook: bridge class not found");
        return false;
    }

    g_bridge_class = reinterpret_cast<jclass>(env->NewGlobalRef(local_class));
    env->DeleteLocalRef(local_class);
    if (g_bridge_class == nullptr) return false;

    g_on_configured = env->GetStaticMethodID(
        g_bridge_class,
        "onConfigured",
        "(JLjava/lang/String;Ljava/lang/String;IIIJ)V"
    );
    g_on_input_buffer_queued = env->GetStaticMethodID(
        g_bridge_class,
        "onInputBufferQueued",
        "(JIJ)V"
    );
    g_on_output_buffer_released = env->GetStaticMethodID(
        g_bridge_class,
        "onOutputBufferReleased",
        "(JZ)V"
    );
    g_on_deleted = env->GetStaticMethodID(
        g_bridge_class,
        "onDeleted",
        "(J)V"
    );
    g_media_ndk_inline_hook_state = env->GetStaticMethodID(
        g_bridge_class,
        "mediaNdkInlineHookState",
        "()I"
    );

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }

    return g_on_configured != nullptr &&
        g_on_input_buffer_queued != nullptr &&
        g_on_output_buffer_released != nullptr &&
        g_on_deleted != nullptr &&
        g_media_ndk_inline_hook_state != nullptr;
}

jint query_media_ndk_inline_hook_state() {
    bool should_detach = false;
    JNIEnv *env = current_env(&should_detach);
    if (env == nullptr || !resolve_bridge(env)) {
        detach_if_needed(should_detach);
        return kInlineHookStateUnavailable;
    }

    const jint state = env->CallStaticIntMethod(g_bridge_class, g_media_ndk_inline_hook_state);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        detach_if_needed(should_detach);
        return kInlineHookStateUnavailable;
    }

    detach_if_needed(should_detach);
    return state;
}

bool media_ndk_inline_hooks_enabled() {
    const int cached_state = g_inline_hook_state.load();
    if (cached_state == kInlineHookStateEnabled) return true;
    if (cached_state == kInlineHookStateDisabled) return false;

    const jint state = query_media_ndk_inline_hook_state();
    if (state == kInlineHookStateEnabled) {
        g_inline_hook_state.store(kInlineHookStateEnabled);
        log_info("NativeMediaCodecHook: libmediandk inline hooks enabled by settings");
        return true;
    }
    if (state == kInlineHookStateDisabled) {
        g_inline_hook_state.store(kInlineHookStateDisabled);
        if (!g_inline_hook_disabled_logged.exchange(true)) {
            log_warn("NativeMediaCodecHook: libmediandk inline hooks disabled by settings");
        }
        return false;
    }

    if (!g_inline_hook_unavailable_logged.exchange(true)) {
        log_warn("NativeMediaCodecHook: libmediandk inline hook setting unavailable");
    }
    return false;
}

jstring new_nullable_string(JNIEnv *env, const std::string &value) {
    return value.empty() ? nullptr : env->NewStringUTF(value.c_str());
}

void notify_configured(
    AMediaCodec *codec,
    const std::string &codec_name,
    const std::string &mime,
    int32_t width,
    int32_t height,
    uint32_t flags,
    ANativeWindow *surface
) {
    bool should_detach = false;
    JNIEnv *env = current_env(&should_detach);
    if (env == nullptr || !resolve_bridge(env)) {
        detach_if_needed(should_detach);
        return;
    }

    jstring j_codec_name = new_nullable_string(env, codec_name);
    jstring j_mime = new_nullable_string(env, mime);
    env->CallStaticVoidMethod(
        g_bridge_class,
        g_on_configured,
        reinterpret_cast<jlong>(codec),
        j_codec_name,
        j_mime,
        static_cast<jint>(width),
        static_cast<jint>(height),
        static_cast<jint>(flags),
        reinterpret_cast<jlong>(surface)
    );
    if (j_codec_name != nullptr) env->DeleteLocalRef(j_codec_name);
    if (j_mime != nullptr) env->DeleteLocalRef(j_mime);
    if (env->ExceptionCheck()) env->ExceptionClear();
    detach_if_needed(should_detach);
}

void notify_input_buffer_queued(AMediaCodec *codec, size_t size, uint64_t presentation_time_us) {
    if (size == 0) return;

    bool should_detach = false;
    JNIEnv *env = current_env(&should_detach);
    if (env == nullptr || !resolve_bridge(env)) {
        detach_if_needed(should_detach);
        return;
    }

    env->CallStaticVoidMethod(
        g_bridge_class,
        g_on_input_buffer_queued,
        reinterpret_cast<jlong>(codec),
        static_cast<jint>(size > INT32_MAX ? INT32_MAX : size),
        static_cast<jlong>(presentation_time_us)
    );
    if (env->ExceptionCheck()) env->ExceptionClear();
    detach_if_needed(should_detach);
}

void notify_output_buffer_released(AMediaCodec *codec, bool render) {
    bool should_detach = false;
    JNIEnv *env = current_env(&should_detach);
    if (env == nullptr || !resolve_bridge(env)) {
        detach_if_needed(should_detach);
        return;
    }

    env->CallStaticVoidMethod(
        g_bridge_class,
        g_on_output_buffer_released,
        reinterpret_cast<jlong>(codec),
        static_cast<jboolean>(render)
    );
    if (env->ExceptionCheck()) env->ExceptionClear();
    detach_if_needed(should_detach);
}

void notify_deleted(AMediaCodec *codec) {
    bool should_detach = false;
    JNIEnv *env = current_env(&should_detach);
    if (env == nullptr || !resolve_bridge(env)) {
        detach_if_needed(should_detach);
        return;
    }

    env->CallStaticVoidMethod(g_bridge_class, g_on_deleted, reinterpret_cast<jlong>(codec));
    if (env->ExceptionCheck()) env->ExceptionClear();
    detach_if_needed(should_detach);
}

std::string format_string(const AMediaFormat *format, const char *key) {
    const char *value = nullptr;
    if (format != nullptr &&
        original_format_get_string != nullptr &&
        original_format_get_string(const_cast<AMediaFormat *>(format), key, &value) &&
        value != nullptr) {
        return value;
    }
    return {};
}

int32_t format_int32(const AMediaFormat *format, const char *key) {
    int32_t value = 0;
    if (format != nullptr &&
        original_format_get_int32 != nullptr &&
        original_format_get_int32(const_cast<AMediaFormat *>(format), key, &value)) {
        return value;
    }
    return 0;
}

void remember_codec_name(AMediaCodec *codec, const char *name) {
    if (codec == nullptr || name == nullptr || name[0] == '\0') return;
    std::lock_guard<std::mutex> lock(g_codec_info_mutex);
    g_codec_names[codec] = name;
}

void remember_codec_mime(AMediaCodec *codec, const char *mime) {
    if (codec == nullptr || mime == nullptr || mime[0] == '\0') return;
    std::lock_guard<std::mutex> lock(g_codec_info_mutex);
    g_codec_mimes[codec] = mime;
}

std::string remembered_codec_name(AMediaCodec *codec) {
    if (codec == nullptr) return {};
    if (original_get_name != nullptr && original_release_name != nullptr) {
        char *name = nullptr;
        if (original_get_name(codec, &name) == AMEDIA_OK && name != nullptr) {
            std::string result = name;
            original_release_name(codec, name);
            if (!result.empty()) return result;
        }
    }

    std::lock_guard<std::mutex> lock(g_codec_info_mutex);
    auto it = g_codec_names.find(codec);
    return it == g_codec_names.end() ? std::string{} : it->second;
}

std::string remembered_codec_mime(AMediaCodec *codec) {
    if (codec == nullptr) return {};
    std::lock_guard<std::mutex> lock(g_codec_info_mutex);
    auto it = g_codec_mimes.find(codec);
    return it == g_codec_mimes.end() ? std::string{} : it->second;
}

void forget_codec(AMediaCodec *codec) {
    if (codec == nullptr) return;
    std::lock_guard<std::mutex> lock(g_codec_info_mutex);
    g_codec_names.erase(codec);
    g_codec_mimes.erase(codec);
}

media_status_t hooked_configure(
    AMediaCodec *codec,
    const AMediaFormat *format,
    ANativeWindow *surface,
    AMediaCrypto *crypto,
    uint32_t flags
) {
    media_status_t result = original_configure(codec, format, surface, crypto, flags);
    if (result != AMEDIA_OK || codec == nullptr) return result;

    std::string mime = format_string(format, "mime");
    if (mime.empty()) mime = remembered_codec_mime(codec);
    if (mime.rfind("video/", 0) != 0) return result;

    int32_t width = format_int32(format, "width");
    int32_t height = format_int32(format, "height");
    std::string codec_name = remembered_codec_name(codec);
    log_info(
        "NativeMediaCodecHook: configured codec=%p, name=%s, mime=%s, size=%dx%d, surface=%p, flags=%u",
        codec,
        codec_name.empty() ? "-" : codec_name.c_str(),
        mime.c_str(),
        width,
        height,
        surface,
        flags
    );
    notify_configured(codec, codec_name, mime, width, height, flags, surface);
    return result;
}

media_status_t hooked_start(AMediaCodec *codec) {
    media_status_t result = original_start(codec);
    if (result == AMEDIA_OK && codec != nullptr) {
        log_debug("NativeMediaCodecHook: start codec=%p", codec);
    }
    return result;
}

media_status_t hooked_queue_input_buffer(
    AMediaCodec *codec,
    size_t idx,
    off_t offset,
    size_t size,
    uint64_t time,
    uint32_t flags
) {
    media_status_t result = original_queue_input_buffer(codec, idx, offset, size, time, flags);
    if (result == AMEDIA_OK && codec != nullptr && size > 0) {
        notify_input_buffer_queued(codec, size, time);
    }
    return result;
}

media_status_t hooked_release_output_buffer(AMediaCodec *codec, size_t idx, bool render) {
    media_status_t result = original_release_output_buffer(codec, idx, render);
    if (result == AMEDIA_OK && codec != nullptr) {
        notify_output_buffer_released(codec, render);
    }
    return result;
}

media_status_t hooked_release_output_buffer_at_time(AMediaCodec *codec, size_t idx, int64_t timestamp_ns) {
    media_status_t result = original_release_output_buffer_at_time(codec, idx, timestamp_ns);
    if (result == AMEDIA_OK && codec != nullptr) {
        notify_output_buffer_released(codec, true);
    }
    return result;
}

media_status_t hooked_stop(AMediaCodec *codec) {
    media_status_t result = original_stop(codec);
    if (codec != nullptr) {
        notify_deleted(codec);
    }
    return result;
}

media_status_t hooked_delete(AMediaCodec *codec) {
    notify_deleted(codec);
    forget_codec(codec);
    return original_delete(codec);
}

void hook_symbol(void *handle, const char *symbol, void *replacement, void **backup) {
    if (g_hook_func == nullptr || handle == nullptr) return;
    void *target = dlsym(handle, symbol);
    if (target == nullptr) {
        log_debug("NativeMediaCodecHook: missing symbol %s", symbol);
        return;
    }

    int result = g_hook_func(target, replacement, backup);
    if (result == 0) {
        log_debug("NativeMediaCodecHook: hooked %s", symbol);
    } else {
        log_warn("NativeMediaCodecHook: failed to hook %s, result=%d", symbol, result);
    }
}

void install_mediandk_hooks(void *handle) {
    if (!media_ndk_inline_hooks_enabled()) return;

    bool expected = false;
    if (!g_mediandk_hooks_installed.compare_exchange_strong(expected, true)) return;

    original_get_name = reinterpret_cast<GetNameFn>(dlsym(handle, "AMediaCodec_getName"));
    original_release_name = reinterpret_cast<ReleaseNameFn>(dlsym(handle, "AMediaCodec_releaseName"));
    original_format_get_string = reinterpret_cast<FormatGetStringFn>(dlsym(handle, "AMediaFormat_getString"));
    original_format_get_int32 = reinterpret_cast<FormatGetInt32Fn>(dlsym(handle, "AMediaFormat_getInt32"));
    hook_symbol(
        handle,
        "AMediaCodec_configure",
        reinterpret_cast<void *>(hooked_configure),
        reinterpret_cast<void **>(&original_configure)
    );
    hook_symbol(
        handle,
        "AMediaCodec_start",
        reinterpret_cast<void *>(hooked_start),
        reinterpret_cast<void **>(&original_start)
    );
    hook_symbol(
        handle,
        "AMediaCodec_queueInputBuffer",
        reinterpret_cast<void *>(hooked_queue_input_buffer),
        reinterpret_cast<void **>(&original_queue_input_buffer)
    );
    hook_symbol(
        handle,
        "AMediaCodec_releaseOutputBuffer",
        reinterpret_cast<void *>(hooked_release_output_buffer),
        reinterpret_cast<void **>(&original_release_output_buffer)
    );
    hook_symbol(
        handle,
        "AMediaCodec_releaseOutputBufferAtTime",
        reinterpret_cast<void *>(hooked_release_output_buffer_at_time),
        reinterpret_cast<void **>(&original_release_output_buffer_at_time)
    );
    hook_symbol(
        handle,
        "AMediaCodec_stop",
        reinterpret_cast<void *>(hooked_stop),
        reinterpret_cast<void **>(&original_stop)
    );
    hook_symbol(
        handle,
        "AMediaCodec_delete",
        reinterpret_cast<void *>(hooked_delete),
        reinterpret_cast<void **>(&original_delete)
    );

    log_info("NativeMediaCodecHook: installed libmediandk hooks");
}

void try_install_loaded_mediandk() {
    if (g_mediandk_hooks_installed.load()) return;
    void *handle = dlopen(kMediaCodecLibrary, RTLD_NOW | RTLD_NOLOAD);
    if (handle != nullptr) {
        install_mediandk_hooks(handle);
    }
}

void on_library_loaded(const char *name, void *handle) {
    if (handle != nullptr &&
        ends_with(name, kMediaCodecLibrary) &&
        media_ndk_inline_hooks_enabled()) {
        install_mediandk_hooks(handle);
    }
}

} // namespace

extern "C" jint JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    bool should_detach = false;
    JNIEnv *env = current_env(&should_detach);
    if (env != nullptr) {
        resolve_bridge(env);
    }
    detach_if_needed(should_detach);
    log_info("NativeMediaCodecHook: JNI loaded");
    return JNI_VERSION_1_6;
}

extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    if (entries == nullptr || entries->hook_func == nullptr) {
        log_warn("NativeMediaCodecHook: native_init missing hook entries");
        return on_library_loaded;
    }

    g_hook_func = entries->hook_func;
    log_info("NativeMediaCodecHook: native_init version=%u", entries->version);
    try_install_loaded_mediandk();
    return on_library_loaded;
}

extern "C" JNIEXPORT void JNICALL
Java_com_absinthe_mediacodecat_hook_NativeMediaCodecBridge_refreshMediaNdkInlineHooksNative(
    JNIEnv *,
    jclass
) {
    try_install_loaded_mediandk();
}
