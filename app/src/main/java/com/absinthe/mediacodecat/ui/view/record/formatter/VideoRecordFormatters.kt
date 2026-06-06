package com.absinthe.mediacodecat.ui.view.record.formatter

import android.media.MediaFormat
import com.absinthe.mediacodecat.model.VideoRecord
import com.absinthe.mediacodecat.ui.view.record.CoverDefaultAspectRatio
import com.absinthe.mediacodecat.ui.view.record.CoverFrame
import com.absinthe.mediacodecat.ui.view.record.CoverMaxAspectRatio
import com.absinthe.mediacodecat.ui.view.record.CoverMinAspectRatio
import com.absinthe.mediacodecat.ui.view.record.PortraitCoverAspectThreshold
import com.absinthe.mediacodecat.ui.view.record.PortraitCoverHeight
import com.absinthe.mediacodecat.ui.view.record.PortraitCoverMaxWidth
import com.absinthe.mediacodecat.ui.view.record.PortraitCoverMinWidth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun VideoRecord.primaryTitle(strings: VideoRecordStrings): String {
    return buildString {
        append(resolutionLabel(strings))
        append(" ")
        append(mime.ifBlank { strings.fallbackVideo })
    }
}

internal fun VideoRecord.codecLine(strings: VideoRecordStrings): String {
    return strings.codecFormat.formatLocalized(codecName ?: strings.emptyValue)
}

internal fun VideoRecord.attributeLabels(strings: VideoRecordStrings): List<AttributeLabel> {
    return listOfNotNull(
        bitrateAttributeLabel(strings),
        frameRateAttributeLabel(strings),
        AttributeLabel(strings.rotationAttributeFormat, rotationLabel(strings)),
        AttributeLabel(strings.colorAttributeFormat, colorLabel(strings)),
        profileAttributeLabel(strings),
        levelAttributeLabel(strings)
    )
}

private fun VideoRecord.bitrateAttributeLabel(strings: VideoRecordStrings): AttributeLabel? {
    val bitrate = bitrateKbps?.takeIf { it > 0 } ?: return null
    return AttributeLabel(
        format = strings.bitrateAttributeFormat,
        value = strings.bitrateKbpsFormat.formatLocalized(bitrate)
    )
}

private fun VideoRecord.frameRateAttributeLabel(strings: VideoRecordStrings): AttributeLabel? {
    val frameRate = frameRate?.takeIf { it.isFinite() && it > 0f } ?: return null
    val label = if (frameRate.rem(1f) == 0f) {
        frameRate.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", frameRate)
    }
    return AttributeLabel(strings.frameRateAttributeFormat, label)
}

private fun VideoRecord.rotationLabel(strings: VideoRecordStrings): String {
    return rotationDegrees?.let { "${it}\u00B0" } ?: strings.emptyValue
}

private fun VideoRecord.colorLabel(strings: VideoRecordStrings): String {
    val standard = colorStandard?.let { colorStandardLabel(it, strings) }
    val range = colorRange?.let { colorRangeLabel(it, strings) }
    val transfer = colorTransfer?.let { colorTransferLabel(it, strings) }
    return listOfNotNull(standard, range, transfer)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("/")
        ?: strings.emptyValue
}

private fun colorStandardLabel(value: Int, strings: VideoRecordStrings): String {
    return when (value) {
        MediaFormat.COLOR_STANDARD_BT709 -> strings.colorStandardBt709
        MediaFormat.COLOR_STANDARD_BT601_PAL -> strings.colorStandardBt601Pal
        MediaFormat.COLOR_STANDARD_BT601_NTSC -> strings.colorStandardBt601Ntsc
        MediaFormat.COLOR_STANDARD_BT2020 -> strings.colorStandardBt2020
        else -> strings.colorStandardUnknownFormat.formatLocalized(value)
    }
}

private fun colorRangeLabel(value: Int, strings: VideoRecordStrings): String {
    return when (value) {
        MediaFormat.COLOR_RANGE_FULL -> strings.colorRangeFull
        MediaFormat.COLOR_RANGE_LIMITED -> strings.colorRangeLimited
        else -> strings.colorRangeUnknownFormat.formatLocalized(value)
    }
}

private fun colorTransferLabel(value: Int, strings: VideoRecordStrings): String {
    return when (value) {
        MediaFormat.COLOR_TRANSFER_LINEAR -> strings.colorTransferLinear
        MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> strings.colorTransferSdr
        MediaFormat.COLOR_TRANSFER_ST2084 -> strings.colorTransferPq
        MediaFormat.COLOR_TRANSFER_HLG -> strings.colorTransferHlg
        else -> strings.colorTransferUnknownFormat.formatLocalized(value)
    }
}

private fun VideoRecord.profileAttributeLabel(strings: VideoRecordStrings): AttributeLabel? {
    val profileValue = profile ?: return null
    val label = codecProfileLabel(mime, profileValue, strings) ?: return null
    return AttributeLabel(strings.profileAttributeFormat, label)
}

private fun VideoRecord.levelAttributeLabel(strings: VideoRecordStrings): AttributeLabel? {
    val levelValue = level ?: return null
    val label = codecLevelLabel(mime, levelValue, strings) ?: return null
    return AttributeLabel(strings.levelAttributeFormat, label)
}


private fun VideoRecord.resolutionLabel(strings: VideoRecordStrings): String {
    return if (width != null && height != null) "${width}x$height" else strings.unknownSize
}

internal fun VideoRecord.aspectRatio(): Float? {
    val width = width?.takeIf { it > 0 } ?: return null
    val height = height?.takeIf { it > 0 } ?: return null
    return width.toFloat() / height.toFloat()
}

internal fun VideoRecord.coverAspectRatio(): Float {
    return aspectRatio()?.coerceIn(
        CoverMinAspectRatio,
        CoverMaxAspectRatio
    ) ?: CoverDefaultAspectRatio
}

internal fun VideoRecord.usesSideCoverLayout(): Boolean {
    return (aspectRatio() ?: return false) < PortraitCoverAspectThreshold
}

internal fun VideoRecord.coverFrame(): CoverFrame {
    val aspectRatio = coverAspectRatio()
    val width = (PortraitCoverHeight * aspectRatio).coerceIn(
        PortraitCoverMinWidth,
        PortraitCoverMaxWidth
    )
    return CoverFrame(width = width)
}


internal fun VideoRecord.dateTitle(): String {
    return timestampMs().toZonedDateTime().format(DAY_FORMATTER)
}

internal fun VideoRecord.timeRangeTitle(strings: VideoRecordStrings): String {
    val firstSeen = firstSeenAtMs.toZonedDateTime().format(TIME_FORMATTER)
    val lastSeen = lastSeenAtMs.toZonedDateTime().format(TIME_FORMATTER)
    return if (firstSeen == lastSeen) {
        firstSeen
    } else {
        strings.timeRangeFormat.formatLocalized(firstSeen, lastSeen)
    }
}

private fun VideoRecord.timestampMs(): Long {
    return if (lastSeenAtMs > 0) lastSeenAtMs else firstSeenAtMs
}

private fun Long.toZonedDateTime() =
    Instant.ofEpochMilli(coerceAtLeast(0L)).atZone(ZoneId.systemDefault())

private val DAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss")

internal fun String.normalizedMime(): String = substringBefore(';').trim().lowercase(Locale.ROOT)

internal fun String.formatLocalized(vararg args: Any): String =
    String.format(Locale.getDefault(), this, *args)
