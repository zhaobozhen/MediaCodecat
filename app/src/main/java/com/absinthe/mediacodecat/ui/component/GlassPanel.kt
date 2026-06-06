package com.absinthe.mediacodecat.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

@Composable
internal fun GlassPanel(
    backdrop: Backdrop,
    highlightAngle: () -> Float = { 45f },
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)

    Column(
        modifier = modifier
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(6f.dp.toPx(), 10f.dp.toPx())
                },
                highlight = {
                    Highlight(
                        style = HighlightStyle.Default(
                            angle = highlightAngle(),
                            falloff = 2f
                        )
                    )
                },
                onDrawSurface = {
                    drawRect(surfaceColor)
                }
            ),
        content = content
    )
}
