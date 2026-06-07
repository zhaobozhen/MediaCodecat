package com.absinthe.mediacodecat.ui.view.record.tutorial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.absinthe.mediacodecat.BuildConfig
import com.absinthe.mediacodecat.R
import com.kyant.capsule.ContinuousRoundedRectangle

@Composable
internal fun TutorialModuleListPage(
    selectedAlpha: Float,
    skeletonAlpha: Float,
    modifier: Modifier = Modifier
) {
    val pageBackground = MaterialTheme.colorScheme.surfaceContainerLow

    Column(
        modifier = modifier
            .clip(ContinuousRoundedRectangle(14.dp))
            .background(pageBackground)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                ContinuousRoundedRectangle(14.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.empty_video_records_modules_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            TutorialSearchGlyph()
        }

        TutorialModuleRow(
            skeleton = true,
            skeletonAlpha = skeletonAlpha,
            dimmed = true
        )
        TutorialModuleRow(
            title = stringResource(R.string.app_name),
            description = stringResource(R.string.xposed_description),
            badge = "API ${BuildConfig.XPOSED_TARGET_API_VERSION}",
            selectedAlpha = selectedAlpha
        )
        TutorialModuleRow(
            skeleton = true,
            skeletonAlpha = skeletonAlpha,
            dimmed = true
        )
    }
}

@Composable
private fun TutorialModuleRow(
    title: String = "",
    description: String = "",
    badge: String = "",
    modifier: Modifier = Modifier,
    selectedAlpha: Float = 0f,
    dimmed: Boolean = false,
    skeleton: Boolean = false,
    skeletonAlpha: Float = 0f
) {
    val contentAlpha = if (dimmed) 0.54f else 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f + selectedAlpha))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = selectedAlpha * 1.5f)),
                ContinuousRoundedRectangle(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TutorialAppIcon(
            color = if (dimmed) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier
                .size(28.dp)
                .alpha(contentAlpha)
        )
        Column(modifier = Modifier.weight(1f)) {
            if (skeleton) {
                TutorialSkeletonLine(
                    width = 98.dp,
                    height = 12.dp,
                    alpha = skeletonAlpha
                )
                TutorialSkeletonLine(
                    width = 122.dp,
                    height = 8.dp,
                    alpha = skeletonAlpha,
                    modifier = Modifier.padding(top = 7.dp)
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    fontWeight = if (dimmed) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (skeleton) {
            TutorialSkeletonLine(
                width = 42.dp,
                height = 20.dp,
                alpha = skeletonAlpha,
                cornerRadius = 6.dp
            )
        } else {
            Text(
                text = badge,
                modifier = Modifier
                    .clip(ContinuousRoundedRectangle(6.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (dimmed) 0.45f else 0.9f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = contentAlpha),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TutorialSkeletonLine(
    width: Dp,
    height: Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = height / 2f
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(ContinuousRoundedRectangle(cornerRadius))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    )
}

@Composable
private fun TutorialSearchGlyph(
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(22.dp)) {
        drawCircle(
            color = color,
            radius = 6.dp.toPx(),
            center = Offset(size.width * 0.42f, size.height * 0.42f),
            style = Stroke(width = 2.dp.toPx())
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.62f, size.height * 0.62f),
            end = Offset(size.width * 0.82f, size.height * 0.82f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}


@Composable
internal fun TutorialAppIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ContinuousRoundedRectangle(7.dp))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(ContinuousRoundedRectangle(3.dp))
                .background(color)
        )
    }
}
