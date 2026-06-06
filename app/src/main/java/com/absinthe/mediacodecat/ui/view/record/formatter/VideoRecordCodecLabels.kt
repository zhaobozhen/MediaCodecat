package com.absinthe.mediacodecat.ui.view.record.formatter

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat

internal fun codecProfileLabel(mime: String, value: Int, strings: VideoRecordStrings): String? {
    return when (mime.normalizedMime()) {
        MediaFormat.MIMETYPE_VIDEO_AVC -> avcProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_HEVC -> hevcProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_VP8 -> vp8ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_VP9 -> vp9ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_AV1 -> av1ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> dolbyVisionProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_H263 -> h263ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_MPEG2 -> mpeg2ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> mpeg4ProfileLabel(value)
        MIME_VIDEO_VVC -> vvcProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_RAW -> null
        else -> return null
    } ?: strings.profileUnknownFormat.formatLocalized(value)
}

internal fun codecLevelLabel(mime: String, value: Int, strings: VideoRecordStrings): String? {
    return when (mime.normalizedMime()) {
        MediaFormat.MIMETYPE_VIDEO_AVC -> avcLevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_HEVC -> hevcLevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_VP8 -> vp8LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_VP9 -> vp9LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_AV1 -> av1LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> dolbyVisionLevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_H263 -> h263LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_MPEG2 -> mpeg2LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> mpeg4LevelLabel(value)
        MIME_VIDEO_VVC -> vvcLevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_RAW -> null
        else -> return null
    } ?: strings.levelUnknownFormat.formatLocalized(value)
}

private fun avcProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.AVCProfileBaseline -> "Baseline"
    CodecProfileLevel.AVCProfileConstrainedBaseline -> "Constrained Baseline"
    CodecProfileLevel.AVCProfileMain -> "Main"
    CodecProfileLevel.AVCProfileExtended -> "Extended"
    CodecProfileLevel.AVCProfileHigh -> "High"
    CodecProfileLevel.AVCProfileConstrainedHigh -> "Constrained High"
    CodecProfileLevel.AVCProfileHigh10 -> "High 10"
    CodecProfileLevel.AVCProfileHigh422 -> "High 4:2:2"
    CodecProfileLevel.AVCProfileHigh444 -> "High 4:4:4"
    else -> null
}

private fun avcLevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.AVCLevel1 -> "L1"
    CodecProfileLevel.AVCLevel1b -> "L1b"
    CodecProfileLevel.AVCLevel11 -> "L1.1"
    CodecProfileLevel.AVCLevel12 -> "L1.2"
    CodecProfileLevel.AVCLevel13 -> "L1.3"
    CodecProfileLevel.AVCLevel2 -> "L2"
    CodecProfileLevel.AVCLevel21 -> "L2.1"
    CodecProfileLevel.AVCLevel22 -> "L2.2"
    CodecProfileLevel.AVCLevel3 -> "L3"
    CodecProfileLevel.AVCLevel31 -> "L3.1"
    CodecProfileLevel.AVCLevel32 -> "L3.2"
    CodecProfileLevel.AVCLevel4 -> "L4"
    CodecProfileLevel.AVCLevel41 -> "L4.1"
    CodecProfileLevel.AVCLevel42 -> "L4.2"
    CodecProfileLevel.AVCLevel5 -> "L5"
    CodecProfileLevel.AVCLevel51 -> "L5.1"
    CodecProfileLevel.AVCLevel52 -> "L5.2"
    CodecProfileLevel.AVCLevel6 -> "L6"
    CodecProfileLevel.AVCLevel61 -> "L6.1"
    CodecProfileLevel.AVCLevel62 -> "L6.2"
    else -> null
}

private fun hevcProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.HEVCProfileMain -> "Main"
    CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
    CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
    CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR10"
    CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main 10 HDR10+"
    HEVC_PROFILE_MAIN_400 -> "Main 4:0:0"
    HEVC_PROFILE_MAIN_444 -> "Main 4:4:4"
    else -> null
}

private fun hevcLevelLabel(value: Int): String? {
    return hevcTierLevelLabel(value, tier = "Main", levels = HevcMainTierLevels)
        ?: hevcTierLevelLabel(value, tier = "High", levels = HevcHighTierLevels)
}

private fun hevcTierLevelLabel(
    value: Int,
    tier: String,
    levels: List<Pair<Int, String>>
): String? {
    val label = levels.firstOrNull { it.first == value }?.second ?: return null
    return "$tier $label"
}

private val HevcMainTierLevels = listOf(
    CodecProfileLevel.HEVCMainTierLevel1 to "L1",
    CodecProfileLevel.HEVCMainTierLevel2 to "L2",
    CodecProfileLevel.HEVCMainTierLevel21 to "L2.1",
    CodecProfileLevel.HEVCMainTierLevel3 to "L3",
    CodecProfileLevel.HEVCMainTierLevel31 to "L3.1",
    CodecProfileLevel.HEVCMainTierLevel4 to "L4",
    CodecProfileLevel.HEVCMainTierLevel41 to "L4.1",
    CodecProfileLevel.HEVCMainTierLevel5 to "L5",
    CodecProfileLevel.HEVCMainTierLevel51 to "L5.1",
    CodecProfileLevel.HEVCMainTierLevel52 to "L5.2",
    CodecProfileLevel.HEVCMainTierLevel6 to "L6",
    CodecProfileLevel.HEVCMainTierLevel61 to "L6.1",
    CodecProfileLevel.HEVCMainTierLevel62 to "L6.2"
)

private val HevcHighTierLevels = listOf(
    CodecProfileLevel.HEVCHighTierLevel1 to "L1",
    CodecProfileLevel.HEVCHighTierLevel2 to "L2",
    CodecProfileLevel.HEVCHighTierLevel21 to "L2.1",
    CodecProfileLevel.HEVCHighTierLevel3 to "L3",
    CodecProfileLevel.HEVCHighTierLevel31 to "L3.1",
    CodecProfileLevel.HEVCHighTierLevel4 to "L4",
    CodecProfileLevel.HEVCHighTierLevel41 to "L4.1",
    CodecProfileLevel.HEVCHighTierLevel5 to "L5",
    CodecProfileLevel.HEVCHighTierLevel51 to "L5.1",
    CodecProfileLevel.HEVCHighTierLevel52 to "L5.2",
    CodecProfileLevel.HEVCHighTierLevel6 to "L6",
    CodecProfileLevel.HEVCHighTierLevel61 to "L6.1",
    CodecProfileLevel.HEVCHighTierLevel62 to "L6.2"
)

private fun vp8ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.VP8ProfileMain -> "Main"
    else -> null
}

private fun vp8LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.VP8Level_Version0 -> "Version 0"
    CodecProfileLevel.VP8Level_Version1 -> "Version 1"
    CodecProfileLevel.VP8Level_Version2 -> "Version 2"
    CodecProfileLevel.VP8Level_Version3 -> "Version 3"
    else -> null
}

private fun vp9ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.VP9Profile0 -> "Profile 0"
    CodecProfileLevel.VP9Profile1 -> "Profile 1"
    CodecProfileLevel.VP9Profile2 -> "Profile 2"
    CodecProfileLevel.VP9Profile2HDR -> "Profile 2 HDR"
    CodecProfileLevel.VP9Profile2HDR10Plus -> "Profile 2 HDR10+"
    CodecProfileLevel.VP9Profile3 -> "Profile 3"
    CodecProfileLevel.VP9Profile3HDR -> "Profile 3 HDR"
    CodecProfileLevel.VP9Profile3HDR10Plus -> "Profile 3 HDR10+"
    else -> null
}

private fun vp9LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.VP9Level1 -> "L1"
    CodecProfileLevel.VP9Level11 -> "L1.1"
    CodecProfileLevel.VP9Level2 -> "L2"
    CodecProfileLevel.VP9Level21 -> "L2.1"
    CodecProfileLevel.VP9Level3 -> "L3"
    CodecProfileLevel.VP9Level31 -> "L3.1"
    CodecProfileLevel.VP9Level4 -> "L4"
    CodecProfileLevel.VP9Level41 -> "L4.1"
    CodecProfileLevel.VP9Level5 -> "L5"
    CodecProfileLevel.VP9Level51 -> "L5.1"
    CodecProfileLevel.VP9Level52 -> "L5.2"
    CodecProfileLevel.VP9Level6 -> "L6"
    CodecProfileLevel.VP9Level61 -> "L6.1"
    CodecProfileLevel.VP9Level62 -> "L6.2"
    else -> null
}

private fun av1ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.AV1ProfileMain8 -> "Main 8"
    CodecProfileLevel.AV1ProfileMain10 -> "Main 10"
    CodecProfileLevel.AV1ProfileMain10HDR10 -> "Main 10 HDR10"
    CodecProfileLevel.AV1ProfileMain10HDR10Plus -> "Main 10 HDR10+"
    else -> null
}

private fun av1LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.AV1Level2 -> "L2"
    CodecProfileLevel.AV1Level21 -> "L2.1"
    CodecProfileLevel.AV1Level22 -> "L2.2"
    CodecProfileLevel.AV1Level23 -> "L2.3"
    CodecProfileLevel.AV1Level3 -> "L3"
    CodecProfileLevel.AV1Level31 -> "L3.1"
    CodecProfileLevel.AV1Level32 -> "L3.2"
    CodecProfileLevel.AV1Level33 -> "L3.3"
    CodecProfileLevel.AV1Level4 -> "L4"
    CodecProfileLevel.AV1Level41 -> "L4.1"
    CodecProfileLevel.AV1Level42 -> "L4.2"
    CodecProfileLevel.AV1Level43 -> "L4.3"
    CodecProfileLevel.AV1Level5 -> "L5"
    CodecProfileLevel.AV1Level51 -> "L5.1"
    CodecProfileLevel.AV1Level52 -> "L5.2"
    CodecProfileLevel.AV1Level53 -> "L5.3"
    CodecProfileLevel.AV1Level6 -> "L6"
    CodecProfileLevel.AV1Level61 -> "L6.1"
    CodecProfileLevel.AV1Level62 -> "L6.2"
    CodecProfileLevel.AV1Level63 -> "L6.3"
    CodecProfileLevel.AV1Level7 -> "L7"
    CodecProfileLevel.AV1Level71 -> "L7.1"
    CodecProfileLevel.AV1Level72 -> "L7.2"
    CodecProfileLevel.AV1Level73 -> "L7.3"
    else -> null
}

private fun dolbyVisionProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.DolbyVisionProfileDvavPer -> "dvav.01"
    CodecProfileLevel.DolbyVisionProfileDvavPen -> "dvav.02"
    CodecProfileLevel.DolbyVisionProfileDvheDer -> "dvhe.03"
    CodecProfileLevel.DolbyVisionProfileDvheDen -> "dvhe.04"
    CodecProfileLevel.DolbyVisionProfileDvheDtr -> "dvhe.05"
    CodecProfileLevel.DolbyVisionProfileDvheStn -> "dvhe.06"
    CodecProfileLevel.DolbyVisionProfileDvheDth -> "dvhe.07"
    CodecProfileLevel.DolbyVisionProfileDvheDtb -> "dvhe.08"
    CodecProfileLevel.DolbyVisionProfileDvheSt -> "dvhe.09"
    CodecProfileLevel.DolbyVisionProfileDvav110 -> "dvav.10"
    CodecProfileLevel.DolbyVisionProfileDvavSe -> "dvav.11"
    else -> null
}

private fun dolbyVisionLevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.DolbyVisionLevelHd24 -> "HD 24"
    CodecProfileLevel.DolbyVisionLevelHd30 -> "HD 30"
    CodecProfileLevel.DolbyVisionLevelFhd24 -> "FHD 24"
    CodecProfileLevel.DolbyVisionLevelFhd30 -> "FHD 30"
    CodecProfileLevel.DolbyVisionLevelFhd60 -> "FHD 60"
    CodecProfileLevel.DolbyVisionLevelUhd24 -> "UHD 24"
    CodecProfileLevel.DolbyVisionLevelUhd30 -> "UHD 30"
    CodecProfileLevel.DolbyVisionLevelUhd48 -> "UHD 48"
    CodecProfileLevel.DolbyVisionLevelUhd60 -> "UHD 60"
    CodecProfileLevel.DolbyVisionLevelUhd120 -> "UHD 120"
    CodecProfileLevel.DolbyVisionLevel8k30 -> "8K 30"
    CodecProfileLevel.DolbyVisionLevel8k60 -> "8K 60"
    else -> null
}

private fun h263ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.H263ProfileBaseline -> "Baseline"
    CodecProfileLevel.H263ProfileH320Coding -> "H.320"
    CodecProfileLevel.H263ProfileBackwardCompatible -> "Backward Compatible"
    CodecProfileLevel.H263ProfileISWV2 -> "ISW V2"
    CodecProfileLevel.H263ProfileISWV3 -> "ISW V3"
    CodecProfileLevel.H263ProfileHighCompression -> "High Compression"
    CodecProfileLevel.H263ProfileInternet -> "Internet"
    CodecProfileLevel.H263ProfileInterlace -> "Interlace"
    CodecProfileLevel.H263ProfileHighLatency -> "High Latency"
    else -> null
}

private fun h263LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.H263Level10 -> "L10"
    CodecProfileLevel.H263Level20 -> "L20"
    CodecProfileLevel.H263Level30 -> "L30"
    CodecProfileLevel.H263Level40 -> "L40"
    CodecProfileLevel.H263Level45 -> "L45"
    CodecProfileLevel.H263Level50 -> "L50"
    CodecProfileLevel.H263Level60 -> "L60"
    CodecProfileLevel.H263Level70 -> "L70"
    else -> null
}

private fun mpeg2ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.MPEG2ProfileSimple -> "Simple"
    CodecProfileLevel.MPEG2ProfileMain -> "Main"
    CodecProfileLevel.MPEG2ProfileSNR -> "SNR"
    CodecProfileLevel.MPEG2ProfileSpatial -> "Spatial"
    CodecProfileLevel.MPEG2ProfileHigh -> "High"
    CodecProfileLevel.MPEG2Profile422 -> "4:2:2"
    else -> null
}

private fun mpeg2LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.MPEG2LevelLL -> "Low"
    CodecProfileLevel.MPEG2LevelML -> "Main"
    CodecProfileLevel.MPEG2LevelH14 -> "High 1440"
    CodecProfileLevel.MPEG2LevelHL -> "High"
    CodecProfileLevel.MPEG2LevelHP -> "HighP"
    else -> null
}

private fun mpeg4ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.MPEG4ProfileSimple -> "Simple"
    CodecProfileLevel.MPEG4ProfileSimpleScalable -> "Simple Scalable"
    CodecProfileLevel.MPEG4ProfileCore -> "Core"
    CodecProfileLevel.MPEG4ProfileMain -> "Main"
    CodecProfileLevel.MPEG4ProfileNbit -> "N-bit"
    CodecProfileLevel.MPEG4ProfileScalableTexture -> "Scalable Texture"
    CodecProfileLevel.MPEG4ProfileSimpleFace -> "Simple Face"
    CodecProfileLevel.MPEG4ProfileSimpleFBA -> "Simple FBA"
    CodecProfileLevel.MPEG4ProfileBasicAnimated -> "Basic Animated"
    CodecProfileLevel.MPEG4ProfileHybrid -> "Hybrid"
    CodecProfileLevel.MPEG4ProfileAdvancedRealTime -> "Advanced Real Time"
    CodecProfileLevel.MPEG4ProfileCoreScalable -> "Core Scalable"
    CodecProfileLevel.MPEG4ProfileAdvancedCoding -> "Advanced Coding"
    CodecProfileLevel.MPEG4ProfileAdvancedCore -> "Advanced Core"
    CodecProfileLevel.MPEG4ProfileAdvancedScalable -> "Advanced Scalable"
    CodecProfileLevel.MPEG4ProfileAdvancedSimple -> "Advanced Simple"
    else -> null
}

private fun mpeg4LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.MPEG4Level0 -> "L0"
    CodecProfileLevel.MPEG4Level0b -> "L0b"
    CodecProfileLevel.MPEG4Level1 -> "L1"
    CodecProfileLevel.MPEG4Level2 -> "L2"
    CodecProfileLevel.MPEG4Level3 -> "L3"
    CodecProfileLevel.MPEG4Level3b -> "L3b"
    CodecProfileLevel.MPEG4Level4 -> "L4"
    CodecProfileLevel.MPEG4Level4a -> "L4a"
    CodecProfileLevel.MPEG4Level5 -> "L5"
    CodecProfileLevel.MPEG4Level6 -> "L6"
    else -> null
}

private fun vvcProfileLabel(value: Int): String? = when (value) {
    VVC_PROFILE_MAIN_8 -> "Main 8"
    VVC_PROFILE_MAIN_10 -> "Main 10"
    VVC_PROFILE_MAIN_10_HDR10 -> "Main 10 HDR10"
    VVC_PROFILE_MAIN_10_HDR10_PLUS -> "Main 10 HDR10+"
    VVC_PROFILE_MAIN_10_STILL -> "Main 10 Still"
    else -> null
}

private fun vvcLevelLabel(value: Int): String? {
    return vvcTierLevelLabel(value, tier = "Main", levels = VvcMainTierLevels)
        ?: vvcTierLevelLabel(value, tier = "High", levels = VvcHighTierLevels)
}

private fun vvcTierLevelLabel(
    value: Int,
    tier: String,
    levels: List<Pair<Int, String>>
): String? {
    val label = levels.firstOrNull { it.first == value }?.second ?: return null
    return "$tier $label"
}

private val VvcMainTierLevels = listOf(
    VVC_MAIN_TIER_LEVEL_10 to "L1",
    VVC_MAIN_TIER_LEVEL_20 to "L2",
    VVC_MAIN_TIER_LEVEL_21 to "L2.1",
    VVC_MAIN_TIER_LEVEL_30 to "L3",
    VVC_MAIN_TIER_LEVEL_31 to "L3.1",
    VVC_MAIN_TIER_LEVEL_40 to "L4",
    VVC_MAIN_TIER_LEVEL_41 to "L4.1",
    VVC_MAIN_TIER_LEVEL_50 to "L5",
    VVC_MAIN_TIER_LEVEL_51 to "L5.1",
    VVC_MAIN_TIER_LEVEL_52 to "L5.2",
    VVC_MAIN_TIER_LEVEL_60 to "L6",
    VVC_MAIN_TIER_LEVEL_61 to "L6.1",
    VVC_MAIN_TIER_LEVEL_62 to "L6.2",
    VVC_MAIN_TIER_LEVEL_63 to "L6.3"
)

private val VvcHighTierLevels = listOf(
    VVC_HIGH_TIER_LEVEL_40 to "L4",
    VVC_HIGH_TIER_LEVEL_41 to "L4.1",
    VVC_HIGH_TIER_LEVEL_50 to "L5",
    VVC_HIGH_TIER_LEVEL_51 to "L5.1",
    VVC_HIGH_TIER_LEVEL_52 to "L5.2",
    VVC_HIGH_TIER_LEVEL_60 to "L6",
    VVC_HIGH_TIER_LEVEL_61 to "L6.1",
    VVC_HIGH_TIER_LEVEL_62 to "L6.2",
    VVC_HIGH_TIER_LEVEL_63 to "L6.3"
)

private const val MIME_VIDEO_VVC = "video/vvc"

private const val HEVC_PROFILE_MAIN_400 = 8
private const val HEVC_PROFILE_MAIN_444 = 16

private const val VVC_PROFILE_MAIN_8 = 1
private const val VVC_PROFILE_MAIN_10 = 2
private const val VVC_PROFILE_MAIN_10_STILL = 4
private const val VVC_PROFILE_MAIN_10_HDR10 = 4096
private const val VVC_PROFILE_MAIN_10_HDR10_PLUS = 8192

private const val VVC_MAIN_TIER_LEVEL_10 = 1
private const val VVC_MAIN_TIER_LEVEL_20 = 2
private const val VVC_MAIN_TIER_LEVEL_21 = 4
private const val VVC_MAIN_TIER_LEVEL_30 = 8
private const val VVC_MAIN_TIER_LEVEL_31 = 16
private const val VVC_MAIN_TIER_LEVEL_40 = 32
private const val VVC_MAIN_TIER_LEVEL_41 = 128
private const val VVC_MAIN_TIER_LEVEL_50 = 512
private const val VVC_MAIN_TIER_LEVEL_51 = 2048
private const val VVC_MAIN_TIER_LEVEL_52 = 8192
private const val VVC_MAIN_TIER_LEVEL_60 = 32768
private const val VVC_MAIN_TIER_LEVEL_61 = 131072
private const val VVC_MAIN_TIER_LEVEL_62 = 524288
private const val VVC_MAIN_TIER_LEVEL_63 = 2097152

private const val VVC_HIGH_TIER_LEVEL_40 = 64
private const val VVC_HIGH_TIER_LEVEL_41 = 256
private const val VVC_HIGH_TIER_LEVEL_50 = 1024
private const val VVC_HIGH_TIER_LEVEL_51 = 4096
private const val VVC_HIGH_TIER_LEVEL_52 = 16384
private const val VVC_HIGH_TIER_LEVEL_60 = 65536
private const val VVC_HIGH_TIER_LEVEL_61 = 262144
private const val VVC_HIGH_TIER_LEVEL_62 = 1048576
private const val VVC_HIGH_TIER_LEVEL_63 = 4194304
