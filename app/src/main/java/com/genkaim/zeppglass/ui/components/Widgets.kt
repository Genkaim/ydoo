package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.ui.theme.LocalZgPalette

/**
 * Page title. It is the first element of a screen column and never moves with
 * the scroll: content starts below it, so there is no gradient and no overlay.
 */
/** Fixed header height, published so a page can start its content below it. */
val PageHeaderHeight = 96.dp

/**
 * How far past its own height a header's fade reaches. Kept short: the gradient
 * is painted over the content, so a long tail washed out the card sitting right
 * under the title.
 */
val TailFade = 22.dp

/**
 * Pins a page header over the scrolling content. The block is simply the page
 * colour: cards slide underneath it, with no fade or shadow at the edge.
 */
@Composable
fun PinnedPageHeader(
    modifier: Modifier = Modifier,
    onHeight: ((Dp) -> Unit)? = null,
    tail: Dp = TailFade,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged {
                onHeight?.invoke(with(density) { it.height.toDp() })
            }
            // Opaque at the top, fully transparent further down: content scrolls
            // under the title and fades out instead of meeting a hard edge. The
            // fade runs past the header's own height (TailFade), so the solid
            // block does not end in a visible band under the title.
            .drawBehind {
                val fade = tail.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to background,
                            0.22f to background,
                            1f to background.copy(alpha = 0f)
                        ),
                        startY = 0f,
                        endY = size.height + fade
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height + fade)
                )
            },
        content = content
    )
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Compact "label ---- value" row used by the data browser and detail lists.
 *
 * Values use the same face as everything else on the page. They used to be
 * monospaced for a terminal feel; the mixed faces made one card look like two
 * different apps, so every Number now follows the system face.
 */
/**
 * Two-state switch for the body-composition card: watch entries on the left,
 * scale readings on the right. Deliberately tiny — it sits in a card corner.
 */
@Composable
fun BodySourceToggle(
    source: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
    ) {
        listOf("watch" to "手表", "scale" to "体脂秤").forEach { (key, label) ->
            val selected = source == key
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onChange(key) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1.2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

/**
 * One column of the horizontally arranged stat rows (activity's "今日心率" and
 * the sleep cards): small tinted icon + label on top, the value and an
 * optional unit underneath.
 */
@Composable
fun StatBlock(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    unit: String? = null
) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1
            )
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Flat metric tile: one border, no elevation, monospace value. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    caption: String? = null,
    modifier: Modifier = Modifier,
    verdict: Pair<String, Color>? = null,
    onClick: (() -> Unit)? = null
) {
    // A tile showing "--" has nothing behind it, so it must not open a page:
    // only a tile with a reading stays clickable.
    val hasValue = value.isNotBlank() && value != "--"
    FlatPanel(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick.takeIf { hasValue }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                val abnormal = verdict != null && verdict.first !in setOf("正常", "放松", "达标")
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface
                )
                if (unit != null) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        // The unit belongs to the number: same colour, never the
                        // muted grey that made "82 %" look like two readings.
                        color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 3.dp, bottom = 3.dp)
                    )
                }
                if (verdict != null) {
                    Text(
                        verdict.first,
                        style = MaterialTheme.typography.labelSmall,
                        color = verdict.second,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                    )
                }
            }
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String?,
    modifier: Modifier
) {
    MetricTile(label = label, value = value, unit = unit, icon = null, modifier = modifier)
}

@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    FlatPanel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            actionLabel,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.size(5.dp))
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, tint: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** A one-pixel rule between rows, in the theme's border colour. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalZgPalette.current.border)
    )
}

@Composable
fun ClickableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun FlatDivider(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().height(1.dp),
        color = LocalZgPalette.current.border,
        border = BorderStroke(0.dp, Color.Transparent)
    ) {}
}




