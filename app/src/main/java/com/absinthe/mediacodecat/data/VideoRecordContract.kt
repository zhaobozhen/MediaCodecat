package com.absinthe.mediacodecat.data

import android.net.Uri

object VideoRecordContract {
    const val AUTHORITY = "com.absinthe.mediacodecat.records"
    const val MODULE_PACKAGE = "com.absinthe.mediacodecat"

    object Broadcast {
        const val ACTION_UPSERT = "$MODULE_PACKAGE.action.UPSERT_VIDEO_RECORD"
        const val ACTION_UPSERT_COVER = "$MODULE_PACKAGE.action.UPSERT_VIDEO_COVER"
        const val RECEIVER_CLASS = "$MODULE_PACKAGE.data.VideoRecordReceiver"
        const val EXTRA_VALUES = "$MODULE_PACKAGE.extra.VIDEO_RECORD_VALUES"
        const val EXTRA_COVER_SESSION_ID = "$MODULE_PACKAGE.extra.COVER_SESSION_ID"
        const val EXTRA_COVER_BYTES = "$MODULE_PACKAGE.extra.COVER_BYTES"
        const val EXTRA_COVER_MIME_TYPE = "$MODULE_PACKAGE.extra.COVER_MIME_TYPE"
        const val EXTRA_COVER_WIDTH = "$MODULE_PACKAGE.extra.COVER_WIDTH"
        const val EXTRA_COVER_HEIGHT = "$MODULE_PACKAGE.extra.COVER_HEIGHT"
        const val EXTRA_COVER_CAPTURED_AT_MS = "$MODULE_PACKAGE.extra.COVER_CAPTURED_AT_MS"
    }

    private val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

    object Records {
        const val PATH = "video_records"
        const val TABLE = "video_records"

        val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, PATH)

        const val QUERY_PARAMETER_LIMIT = "limit"
        const val QUERY_PARAMETER_OFFSET = "offset"

        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.com.absinthe.mediacodecat.video_record"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.com.absinthe.mediacodecat.video_record"

        const val ID = "_id"
        const val SCHEMA_VERSION = "schema_version"
        const val SESSION_ID = "session_id"
        const val PACKAGE_NAME = "package_name"
        const val PROCESS_NAME = "process_name"
        const val CODEC_NAME = "codec_name"
        const val MIME = "mime"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val FRAME_RATE = "frame_rate"
        const val ROTATION_DEGREES = "rotation_degrees"
        const val COLOR_FORMAT = "color_format"
        const val COLOR_STANDARD = "color_standard"
        const val COLOR_RANGE = "color_range"
        const val COLOR_TRANSFER = "color_transfer"
        const val PROFILE = "profile"
        const val LEVEL = "level"
        const val BITRATE_KBPS = "bitrate_kbps"
        const val SURFACE_ID = "surface_id"
        const val MEDIA_FORMAT = "media_format"
        const val FIRST_SEEN_AT_MS = "first_seen_at_ms"
        const val LAST_SEEN_AT_MS = "last_seen_at_ms"

        const val NATIVE_CODEC_NAME_PREFIX = "Native AMediaCodec"
        const val FALLBACK_SURFACE_MIME = "video/surface"
        const val FALLBACK_SOURCE_KEY = "mediacodecat-fallback-source"
        const val FALLBACK_VIEW_CLASS_KEY = "mediacodecat-view-class"
        const val FALLBACK_ACTIVITY_CLASS_KEY = "mediacodecat-activity-class"
        const val FALLBACK_SURFACE_CLASS_KEY = "mediacodecat-surface-class"
        const val FALLBACK_SECURE_WINDOW_KEY = "mediacodecat-secure-window"
    }
}
