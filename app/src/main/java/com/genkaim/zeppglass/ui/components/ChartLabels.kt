package com.genkaim.zeppglass.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp

/**
 * A value printed on a chart sits on a light grey badge, so the line or bar
 * behind it cannot make the digits hard to read.
 */
fun DrawScope.drawBadgedLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    topLeft: Offset,
    badge: Color
) {
    val layout = measurer.measure(text, style)
    val padX = 2.dp.toPx()
    val padY = 1.dp.toPx()
    drawRoundRect(
        color = badge,
        topLeft = Offset(topLeft.x - padX, topLeft.y - padY),
        size = Size(layout.size.width + padX * 2, layout.size.height + padY * 2),
        cornerRadius = CornerRadius(3.dp.toPx())
    )
    drawText(layout, topLeft = topLeft)
}
