package com.absinthe.mediacodecat.model

data class CatInfo(
    val timestampUs: Long,
    val packageName: String,
    val mediaFormatKV: String,
    val thumbnailId: String
)