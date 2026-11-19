package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Filled capsule button. The highlighted variant uses the accent colour; the
 * quiet one is a muted fill. Neither draws an outline — the flat design has no
 * borders, so a secondary action is told apart by its fill, not by a stroke.
 */
@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    horizontalPadding: Dp = 14.dp
) {
    val background = if (primary) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (primary) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        color = if (enabled) background else background.copy(alpha = 0.45f),
        contentColor = if (enabled) foreground else foreground.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

/**
 * Round icon button: 44 dp by default, used for the confirm / cancel pair, the
 * AI composer's send key and the appearance switch. Same rule as [FlatButton]:
 * the quiet state is a fill, never a ring.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp
) {
    val background = when {
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = tint ?: if (primary) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (enabled) background else background.copy(alpha = 0.45f),
        contentColor = foreground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = if (enabled) foreground else foreground.copy(alpha = 0.55f)
            )
        }
    }
}
