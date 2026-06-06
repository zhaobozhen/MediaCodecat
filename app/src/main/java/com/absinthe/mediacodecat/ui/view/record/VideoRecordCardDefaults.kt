package com.absinthe.mediacodecat.ui.view.record

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class CoverFrame(
    val width: Dp
)


internal fun resolutionBadgeSize(aspectRatio: Float): Pair<Dp, Dp> {
    val safeAspectRatio = aspectRatio.coerceIn(
        ResolutionBadgeMinAspectRatio,
        ResolutionBadgeMaxAspectRatio
    )
    val frameAspectRatio = ResolutionBadgeWidth.value / ResolutionBadgeHeight.value

    return if (safeAspectRatio >= frameAspectRatio) {
        ResolutionBadgeWidth to ResolutionBadgeWidth / safeAspectRatio
    } else {
        ResolutionBadgeHeight * safeAspectRatio to ResolutionBadgeHeight
    }
}

internal val ResolutionBadgeWidth = 22.dp

internal val ResolutionBadgeHeight = 14.dp

internal val ResolutionBadgeCornerRadius = 3.dp

internal val ResolutionBadgeStrokeWidth = 1.4.dp

internal val InfoLeadingSlotWidth = 22.dp

internal val InfoLeadingGap = 6.dp

internal const val ResolutionBadgeDefaultAspectRatio = 16f / 9f

internal const val ResolutionBadgeMinAspectRatio = 0.5f

internal const val ResolutionBadgeMaxAspectRatio = 2.75f

internal val PortraitCoverHeight = 132.dp

internal val PortraitCoverMinWidth = 68.dp

internal val PortraitCoverMaxWidth = 96.dp

internal const val CoverDefaultAspectRatio = 16f / 9f

internal const val CoverMinAspectRatio = 0.42f

internal const val CoverMaxAspectRatio = 2.6f

internal const val PortraitCoverAspectThreshold = 0.92f

internal val PackageIconSize = 16.dp

internal const val PackageIconBitmapSizePx = 96
