package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.ui.theme.LocalZgPalette

/**
 * Flat content panel: surface colour, no shadow and no outline.
 *
 * Cards are separated by their fill colour alone. `borderWidth` stays for the
 * rare case that a panel needs an outline; at 0 the border is dropped entirely
 * instead of being drawn as a hairline (Material draws 1px even for 0.dp).
 *
 * Pressing a panel goes through `onClick` so the ripple is clipped to the same
 * rounded shape; a `.clickable` on the outer modifier would darken the square
 * corners.
 */
@Composable
fun FlatPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = LocalZgPalette.current.border,
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(Modifier.padding(0.dp), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(Modifier.padding(0.dp), content = content)
        }
    }
}
