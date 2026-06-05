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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun MainScreen() {
    Box(Modifier.fillMaxSize()) {
        val backgroundColor = Color.White
        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }

        Column(
            Modifier
                .layerBackdrop(backdrop)
                .verticalScroll(rememberScrollState())
                .padding(16f.dp)
                .systemBarsPadding()
                .displayCutoutPadding()
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16f.dp)
        ) {
            repeat(20) {
                RandomColorBox()
            }
        }

        val isLightTheme = !isSystemInDarkTheme()
        val contentColor = if (isLightTheme) Color.Black else Color.White
        val iconColorFilter = ColorFilter.tint(contentColor)
        var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

        LiquidBottomTabs(
            selectedTabIndex = { selectedTabIndex },
            onTabSelected = { selectedTabIndex = it },
            backdrop = backdrop,
            tabsCount = 3,
            modifier = Modifier
                .padding(horizontal = 36f.dp, vertical = 16.dp)
                .align(Alignment.BottomCenter)
        ) {
            repeat(3) { index ->
                LiquidBottomTab({ selectedTabIndex = index }) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(28.dp)
                    )
                    BasicText(
                        "Tab ${index + 1}",
                        style = TextStyle(contentColor, 12f.sp)
                    )
                }
            }
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
