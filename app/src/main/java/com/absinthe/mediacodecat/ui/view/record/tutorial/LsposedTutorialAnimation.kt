package com.absinthe.mediacodecat.ui.view.record.tutorial

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

@Composable
internal fun LsposedScopeTutorialAnimation(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LsposedScopeTutorial")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = TutorialAnimationDurationMillis,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "LsposedScopeTutorialProgress"
    )
    val pageProgress = ((progress - 0.38f) / 0.18f).coerceIn(0f, 1f)
    val checkedProgress = ((progress - 0.68f) / 0.18f).coerceIn(0f, 1f)
    val highlightAlpha = (0.1f + 0.1f * (1f - kotlin.math.abs(progress - 0.28f) / 0.35f))
        .coerceIn(0.08f, 0.2f)
    val moduleTouchProgress = (progress / 0.34f).coerceIn(0f, 1f)
    val scopeTouchProgress = ((progress - 0.6f) / 0.24f).coerceIn(0f, 1f)
    val touchPulse = if (progress < 0.34f || progress in 0.6f..0.86f) {
        kotlin.math.sin(progress * kotlin.math.PI.toFloat() * 6f).coerceAtLeast(0f)
    } else {
        0f
    }
    val skeletonAlpha =
        0.32f + 0.18f * ((kotlin.math.sin(progress * kotlin.math.PI.toFloat() * 4f) + 1f) / 2f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .width(TutorialAnimationWidth)
            .height(TutorialAnimationHeight),
        contentAlignment = Alignment.Center
    ) {
        TutorialModuleListPage(
            selectedAlpha = highlightAlpha,
            skeletonAlpha = skeletonAlpha,
            modifier = Modifier
                .fillMaxSize()
                .offset(x = (-18 * pageProgress).dp)
                .alpha(1f - pageProgress)
        )
        TutorialScopePage(
            checkedProgress = checkedProgress,
            highlightAlpha = 0.1f + checkedProgress * 0.1f,
            modifier = Modifier
                .fillMaxSize()
                .offset(x = (18 * (1f - pageProgress)).dp)
                .alpha(pageProgress)
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val moduleStart = Offset(size.width * 0.78f, size.height * 0.28f)
            val moduleEnd = Offset(size.width * 0.48f, size.height * 0.6f)
            val scopeStart = Offset(size.width * 0.34f, size.height * 0.44f)
            val scopeEnd = Offset(size.width * 0.87f, size.height * 0.44f)
            val touch = if (progress < 0.5f) {
                Offset(
                    x = moduleStart.x + (moduleEnd.x - moduleStart.x) * moduleTouchProgress,
                    y = moduleStart.y + (moduleEnd.y - moduleStart.y) * moduleTouchProgress
                )
            } else {
                Offset(
                    x = scopeStart.x + (scopeEnd.x - scopeStart.x) * scopeTouchProgress,
                    y = scopeStart.y + (scopeEnd.y - scopeStart.y) * scopeTouchProgress
                )
            }
            val touchAlpha = when {
                progress < 0.38f -> 1f
                progress < 0.58f -> 0f
                else -> 1f
            }
            val radius = 8.dp.toPx() + touchPulse * 8.dp.toPx()

            drawCircle(
                color = primaryColor.copy(alpha = 0.18f * touchAlpha),
                radius = radius,
                center = touch
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.74f * touchAlpha),
                radius = 4.dp.toPx(),
                center = touch
            )
        }
    }
}


private const val TutorialAnimationDurationMillis = 2_800

private val TutorialAnimationWidth = 248.dp

private val TutorialAnimationHeight = 224.dp
