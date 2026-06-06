package com.absinthe.mediacodecat.ui.view.record.formatter

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.absinthe.mediacodecat.R

@Composable
internal fun videoRecordStrings(): VideoRecordStrings {
    return VideoRecordStrings(
        fallbackVideo = stringResource(R.string.video_record_fallback_video),
        fallbackSurface = stringResource(R.string.video_record_fallback_surface),
        unknownSize = stringResource(R.string.video_record_unknown_size),
        emptyValue = stringResource(R.string.video_record_empty_value),
        codecFormat = stringResource(R.string.video_record_codec_format),
        viewAttributeFormat = stringResource(R.string.video_record_attribute_view_format),
        activityAttributeFormat = stringResource(R.string.video_record_attribute_activity_format),
        secureWindowAttribute = stringResource(R.string.video_record_attribute_secure_window),
        bitrateAttributeFormat = stringResource(R.string.video_record_attribute_bitrate_format),
        frameRateAttributeFormat = stringResource(R.string.video_record_attribute_frame_rate_format),
        rotationAttributeFormat = stringResource(R.string.video_record_attribute_rotation_format),
        colorAttributeFormat = stringResource(R.string.video_record_attribute_color_format),
        profileAttributeFormat = stringResource(R.string.video_record_attribute_profile_format),
        levelAttributeFormat = stringResource(R.string.video_record_attribute_level_format),
        bitrateKbpsFormat = stringResource(R.string.video_record_bitrate_kbps_format),
        colorStandardBt709 = stringResource(R.string.video_record_color_standard_bt709),
        colorStandardBt601Pal = stringResource(R.string.video_record_color_standard_bt601_pal),
        colorStandardBt601Ntsc = stringResource(R.string.video_record_color_standard_bt601_ntsc),
        colorStandardBt2020 = stringResource(R.string.video_record_color_standard_bt2020),
        colorStandardUnknownFormat = stringResource(R.string.video_record_color_standard_unknown_format),
        colorRangeFull = stringResource(R.string.video_record_color_range_full),
        colorRangeLimited = stringResource(R.string.video_record_color_range_limited),
        colorRangeUnknownFormat = stringResource(R.string.video_record_color_range_unknown_format),
        colorTransferLinear = stringResource(R.string.video_record_color_transfer_linear),
        colorTransferSdr = stringResource(R.string.video_record_color_transfer_sdr),
        colorTransferPq = stringResource(R.string.video_record_color_transfer_pq),
        colorTransferHlg = stringResource(R.string.video_record_color_transfer_hlg),
        colorTransferUnknownFormat = stringResource(R.string.video_record_color_transfer_unknown_format),
        profileUnknownFormat = stringResource(R.string.video_record_profile_unknown_format),
        levelUnknownFormat = stringResource(R.string.video_record_level_unknown_format),
        timeRangeFormat = stringResource(R.string.video_record_time_range_format)
    )
}

internal data class VideoRecordStrings(
    val fallbackVideo: String,
    val fallbackSurface: String,
    val unknownSize: String,
    val emptyValue: String,
    val codecFormat: String,
    val viewAttributeFormat: String,
    val activityAttributeFormat: String,
    val secureWindowAttribute: String,
    val bitrateAttributeFormat: String,
    val frameRateAttributeFormat: String,
    val rotationAttributeFormat: String,
    val colorAttributeFormat: String,
    val profileAttributeFormat: String,
    val levelAttributeFormat: String,
    val bitrateKbpsFormat: String,
    val colorStandardBt709: String,
    val colorStandardBt601Pal: String,
    val colorStandardBt601Ntsc: String,
    val colorStandardBt2020: String,
    val colorStandardUnknownFormat: String,
    val colorRangeFull: String,
    val colorRangeLimited: String,
    val colorRangeUnknownFormat: String,
    val colorTransferLinear: String,
    val colorTransferSdr: String,
    val colorTransferPq: String,
    val colorTransferHlg: String,
    val colorTransferUnknownFormat: String,
    val profileUnknownFormat: String,
    val levelUnknownFormat: String,
    val timeRangeFormat: String
)
