package com.absinthe.mediacodecat.ui.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.capsule.ContinuousRoundedRectangle

internal fun DrawScope.drawContinuousRoundedRect(
    color: Color,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    cornerRadius: Float,
    style: DrawStyle = Fill
) {
    val outline = ContinuousRoundedRectangle(cornerRadius)
        .createOutline(size, LayoutDirection.Ltr, this)

    translate(left = topLeft.x, top = topLeft.y) {
        when (outline) {
            is Outline.Generic -> drawPath(outline.path, color, style = style)
            is Outline.Rectangle -> drawRect(color, size = size, style = style)
            is Outline.Rounded -> {
                val path = Path().apply {
                    addRoundRect(outline.roundRect)
                }
                drawPath(path, color, style = style)
            }
        }
    }
}
