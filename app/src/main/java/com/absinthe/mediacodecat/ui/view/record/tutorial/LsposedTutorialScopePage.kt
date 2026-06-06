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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.absinthe.mediacodecat.R

@Composable
internal fun TutorialScopePage(
    checkedProgress: Float,
    highlightAlpha: Float,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceColor)
            .border(
                BorderStroke(1.dp, outlineColor),
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TutorialScopeHeader()
        TutorialScopeSearchBar()

        TutorialAppRow(
            title = stringResource(R.string.empty_video_records_target_app),
            subtitle = stringResource(R.string.empty_video_records_target_package),
            highlightAlpha = highlightAlpha,
            checkedProgress = checkedProgress
        )
        TutorialInactiveAppRow(
            title = stringResource(R.string.empty_video_records_other_app),
            subtitle = stringResource(R.string.empty_video_records_other_package)
        )
    }
}

@Composable
private fun TutorialScopeHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
        )
        Text(
            text = stringResource(R.string.empty_video_records_scope_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f))
            )
        }
    }
}

@Composable
private fun TutorialScopeSearchBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                    RoundedCornerShape(percent = 50)
                )
        )
        Text(
            text = stringResource(R.string.empty_video_records_search_apps),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TutorialAppRow(
    title: String,
    subtitle: String,
    highlightAlpha: Float,
    checkedProgress: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TutorialAppIcon(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TutorialScopeCheckbox(progress = checkedProgress)
    }
}

@Composable
private fun TutorialInactiveAppRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TutorialAppIcon(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TutorialScopeCheckbox(progress = 0f, enabled = false)
    }
}


@Composable
private fun TutorialScopeCheckbox(
    progress: Float,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val borderColor =
        if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.56f + progress * 0.44f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
        }
    val fillColor =
        if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = progress)
        } else {
            Color.Transparent
        }

    Canvas(modifier = modifier.size(22.dp)) {
        val cornerRadius = 5.dp.toPx()
        drawRoundRect(
            color = fillColor,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
        drawRoundRect(
            color = borderColor,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = 1.5.dp.toPx())
        )
        if (progress > 0f) {
            val checkAlpha = progress.coerceIn(0f, 1f)
            val start = Offset(size.width * 0.28f, size.height * 0.52f)
            val middle = Offset(size.width * 0.44f, size.height * 0.68f)
            val end = Offset(size.width * 0.74f, size.height * 0.34f)
            drawLine(
                color = Color.White.copy(alpha = checkAlpha),
                start = start,
                end = middle,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = checkAlpha),
                start = middle,
                end = end,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

