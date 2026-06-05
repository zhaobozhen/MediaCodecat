package com.absinthe.mediacodecat.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabLabels = remember { listOf("记录", "概览", "设置") }
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
        val panelBackdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }
        val bottomBarBackdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }

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

                1 -> PlaceholderTab(
                    title = "概览",
                    modifier = Modifier.fillMaxSize()
                )

                else -> PlaceholderTab(
                    title = "设置",
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
                .padding(horizontal = 36f.dp, vertical = 16.dp)
                .align(Alignment.BottomCenter)
        ) {
            tabLabels.forEachIndexed { index, label ->
                LiquidBottomTab({ selectedTabIndex = index }) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
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

@Composable
private fun PlaceholderTab(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16f.dp)
            .systemBarsPadding()
            .displayCutoutPadding(),
        verticalArrangement = Arrangement.spacedBy(16f.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        repeat(20) {
            RandomColorBox()
        }
    }
}

@Composable
fun RandomColorBox(
    size: Dp = 80.dp
) {
    // 生成一次随机颜色并记住
    val color = remember {
        Color(
            red = (0..255).random(),
            green = (0..255).random(),
            blue = (0..255).random()
        )
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(color)
    )
}
