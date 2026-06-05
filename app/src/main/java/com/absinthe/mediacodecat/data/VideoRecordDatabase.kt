package com.absinthe.mediacodecat.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VideoRecordDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE ${VideoRecordContract.Records.TABLE} (
                ${VideoRecordContract.Records.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${VideoRecordContract.Records.SCHEMA_VERSION} INTEGER NOT NULL,
                ${VideoRecordContract.Records.SESSION_ID} TEXT NOT NULL UNIQUE,
                ${VideoRecordContract.Records.PACKAGE_NAME} TEXT NOT NULL,
                ${VideoRecordContract.Records.PROCESS_NAME} TEXT NOT NULL,
                ${VideoRecordContract.Records.CODEC_NAME} TEXT,
                ${VideoRecordContract.Records.MIME} TEXT NOT NULL,
                ${VideoRecordContract.Records.WIDTH} INTEGER,
                ${VideoRecordContract.Records.HEIGHT} INTEGER,
                ${VideoRecordContract.Records.FRAME_RATE} REAL,
                ${VideoRecordContract.Records.ROTATION_DEGREES} INTEGER,
                ${VideoRecordContract.Records.COLOR_FORMAT} INTEGER,
                ${VideoRecordContract.Records.COLOR_STANDARD} INTEGER,
                ${VideoRecordContract.Records.COLOR_RANGE} INTEGER,
                ${VideoRecordContract.Records.COLOR_TRANSFER} INTEGER,
                ${VideoRecordContract.Records.PROFILE} INTEGER,
                ${VideoRecordContract.Records.LEVEL} INTEGER,
                ${VideoRecordContract.Records.BITRATE_KBPS} INTEGER,
                ${VideoRecordContract.Records.SURFACE_ID} TEXT,
                ${VideoRecordContract.Records.MEDIA_FORMAT} TEXT NOT NULL,
                ${VideoRecordContract.Records.FIRST_SEEN_AT_MS} INTEGER NOT NULL,
                ${VideoRecordContract.Records.LAST_SEEN_AT_MS} INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX idx_video_records_package_last_seen
            ON ${VideoRecordContract.Records.TABLE} (
                ${VideoRecordContract.Records.PACKAGE_NAME},
                ${VideoRecordContract.Records.LAST_SEEN_AT_MS}
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX idx_video_records_mime
            ON ${VideoRecordContract.Records.TABLE} (${VideoRecordContract.Records.MIME})
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${VideoRecordContract.Records.TABLE}")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "video_records.db"
        private const val DATABASE_VERSION = 1
    }
}
