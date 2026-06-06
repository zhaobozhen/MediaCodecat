package com.absinthe.mediacodecat.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.absinthe.mediacodecat.R
import com.absinthe.mediacodecat.ui.view.record.VideoRecordGallery
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showOpenSourceNotices by rememberSaveable { mutableStateOf(false) }
    val tabLabels = listOf(
        stringResource(R.string.tab_records),
        stringResource(R.string.tab_overview),
        stringResource(R.string.tab_settings)
    )
    val tabIcons = listOf(
        Icons.Outlined.PlayArrow,
        Icons.Outlined.Info,
        Icons.Outlined.Settings
    )
    val isLightTheme = !isSystemInDarkTheme()
    val backgroundColor =
        if (isLightTheme) Color(0xFFF6F7F9)
        else Color(0xFF101214)
    val contentColor = if (isLightTheme) Color.Black else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        BackHandler(enabled = showOpenSourceNotices) {
            showOpenSourceNotices = false
        }

        AnimatedContent(
            targetState = showOpenSourceNotices,
            modifier = Modifier.matchParentSize(),
            transitionSpec = { openSourceNoticesTransition() },
            label = "openSourceNoticesTransition"
        ) { isOpenSourceNotices ->
            if (isOpenSourceNotices) {
                OpenSourceNoticesScreen(
                    onBack = { showOpenSourceNotices = false },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val bottomTabsPadding = edgeToEdgeBottomTabsPadding()
                val panelBackdrop = rememberLayerBackdrop {
                    drawRect(backgroundColor)
                    drawContent()
                }
                val bottomBarBackdrop = rememberLayerBackdrop {
                    drawRect(backgroundColor)
                    drawContent()
                }

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .layerBackdrop(bottomBarBackdrop)
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .layerBackdrop(panelBackdrop)
                        )

                        when (selectedTabIndex) {
                            0 -> VideoRecordGallery(
                                backdrop = panelBackdrop,
                                modifier = Modifier.fillMaxSize()
                            )

                            1 -> StatisticsPlaceholderTab(modifier = Modifier.fillMaxSize())

                            else -> SettingsScreen(
                                onOpenSourceNoticesClick = { showOpenSourceNotices = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    LiquidBottomTabs(
                        selectedTabIndex = { selectedTabIndex },
                        onTabSelected = { selectedTabIndex = it },
                        backdrop = bottomBarBackdrop,
                        tabsCount = tabLabels.size,
                        modifier = Modifier
                            .padding(bottomTabsPadding)
                            .align(Alignment.BottomCenter)
                    ) {
                        tabLabels.forEachIndexed { index, label ->
                            LiquidBottomTab({ selectedTabIndex = index }) {
                                Icon(
                                    imageVector = tabIcons[index],
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                BasicText(
                                    label,
                                    style = TextStyle(contentColor, 12f.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<Boolean>.openSourceNoticesTransition(): ContentTransform {
    val direction = if (targetState) 1 else -1
    val animationSpec = tween<IntOffset>(
        durationMillis = OpenSourceNoticesTransitionDurationMillis,
        easing = FastOutSlowInEasing
    )
    val fadeSpec = tween<Float>(
        durationMillis = OpenSourceNoticesTransitionDurationMillis,
        easing = FastOutSlowInEasing
    )

    return (
        slideInHorizontally(animationSpec = animationSpec) { width -> direction * width / 3 } +
            fadeIn(animationSpec = fadeSpec)
        ).togetherWith(
            slideOutHorizontally(animationSpec = animationSpec) { width -> -direction * width / 5 } +
                fadeOut(animationSpec = fadeSpec)
        ).using(SizeTransform(clip = false))
}

@Composable
private fun edgeToEdgeBottomTabsPadding(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()

    return PaddingValues(
        start = safeDrawingPadding.calculateStartPadding(layoutDirection) + 36.dp,
        top = 16.dp,
        end = safeDrawingPadding.calculateEndPadding(layoutDirection) + 36.dp,
        bottom = navigationBarsPadding.calculateBottomPadding() + 16.dp
    )
}

private const val OpenSourceNoticesTransitionDurationMillis = 320

@Composable
private fun StatisticsPlaceholderTab(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "TODO",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
    }
}
