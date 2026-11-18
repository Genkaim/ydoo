package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Small Markdown renderer for assistant messages: headings, lists, quotes,
 * fenced code and `card` blocks. Cards are rendered from the local HealthData
 * by type, so the numbers and rings always match the app instead of trusting
 * text the model typed.
 */
@Composable
fun MarkdownView(
    text: String,
    data: HealthData? = null,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    val blocks = remember(text) { parseBlocks(text) }
    // A little more air between lines than Material's default: answers carry
    // lists and inline numbers, and tight leading made them run together.
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp)
    val quoteStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> Text(
                    block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    },
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
                is Block.Paragraph -> InlineText(
                    block.text,
                    bodyStyle,
                    color
                )
                is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    block.items.forEach { item ->
                        Row {
                            Text("·", color = color, modifier = Modifier.width(12.dp))
                            InlineText(item, bodyStyle, color)
                        }
                    }
                }
                is Block.Ordered -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row {
                            Text("${index + 1}.", color = color, modifier = Modifier.width(20.dp))
                            InlineText(item, bodyStyle, color)
                        }
                    }
                }
                is Block.Quote -> Row {
                    Spacer(
                        Modifier
                            .width(2.dp)
                            .height(28.dp)
                            .background(color.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.width(8.dp))
                    InlineText(
                        block.text,
                        quoteStyle,
                        color.copy(alpha = 0.8f)
                    )
                }
                is Block.Rule -> Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LocalZgPalette.current.border)
                )
                is Block.Code -> {
                    if (block.lang.equals("card", true) || block.lang.equals("metric", true) ||
                        (block.lang.equals("json", true) && block.code.contains("\"type\""))
                    ) {
                        AiDataCard(block.code, data)
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                block.code,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = color,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiDataCard(payload: String, data: HealthData?) {
    val json = runCatching { JSONObject(payload.trim()) }.getOrNull() ?: run {
        Text(
            payload.trim(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    when (json.optString("type").lowercase()) {
        "sleep" -> SleepDataCard(json, data)
        "charge" -> ChargeDataCard(json, data)
        "activity" -> ActivityDataCard(json, data)
        else -> MetricDataCard(json)
    }
}

@Composable
private fun MetricDataCard(json: JSONObject) {
    FlatPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text(
                json.optString("title").ifBlank { "数据" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val value = json.optString("value")
            if (value.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        value,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    val unit = json.optString("unit")
                    if (unit.isNotBlank()) {
                        Text(
                            " $unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            val items = json.optJSONArray("items")
            if (items != null && items.length() > 0) {
                Spacer(Modifier.height(8.dp))
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            item.optString("label"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            item.optString("value"),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
            val caption = json.optString("caption")
            if (caption.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SleepDataCard(json: JSONObject, data: HealthData?) {
    val date = json.optString("date").toDate() ?: data?.latestNight()?.date
    val night = date?.let { data?.nightOn(it) } ?: data?.latestNight()
    if (night == null) {
        MetricDataCard(json)
        return
    }
    val palette = LocalZgPalette.current
    FlatPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text(
                json.optString("title").ifBlank { "${Fmt.date(night.date)} 睡眠" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ProgressRing(
                    (night.score ?: 0) / 100f,
                    Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = palette.sleepLight
                ) {
                    Text(
                        night.score?.toString() ?: "--",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${Fmt.hoursMinutes(night.totalMinutes)} 小时",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        "深睡 ${Fmt.minutes(night.deepMinutes)} · REM ${Fmt.minutes(night.remMinutes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SleepStageBar(
                night,
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
    }
}

@Composable
private fun ChargeDataCard(json: JSONObject, data: HealthData?) {
    val date = json.optString("date").toDate() ?: data?.latestCharge()?.date
    val charge = date?.let { data?.chargeOn(it) } ?: data?.latestCharge()
    if (charge == null) {
        MetricDataCard(json)
        return
    }
    FlatPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text(
                json.optString("title").ifBlank { "${Fmt.date(charge.date)} 身体电量" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ProgressRing(
                    (charge.total ?: 0) / 100f,
                    Modifier.size(58.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        charge.total?.toString() ?: "--",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "体力 ${charge.physical?.roundToInt() ?: "--"}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "精神 ${charge.mental?.roundToInt() ?: "--"}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityDataCard(json: JSONObject, data: HealthData?) {
    val date = json.optString("date").toDate() ?: LocalDate.now()
    val day = data?.day(date) ?: data?.latestDay()
    if (day == null) {
        MetricDataCard(json)
        return
    }
    val steps = day.steps ?: 0
    val goal = day.stepGoal ?: 7000
    val kcal = day.calories ?: 0.0
    val active = day.activityMinutes
    val activeGoal = day.activityGoal
    FlatPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text(
                json.optString("title").ifBlank { "${Fmt.date(day.date)} 活动" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ProgressRing(
                    (steps.toFloat() / goal).coerceIn(0f, 1f),
                    Modifier.size(58.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "${(steps.toFloat() / goal * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "$steps / $goal 步",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        "${"%.1f".format((day.distanceM ?: 0.0) / 1000)} km · ${kcal.toInt()} kcal · " +
                            (active?.let { m -> activeGoal?.let { g -> "活动 $m/$g 分" } ?: "活动 $m 分" }
                                ?: "活动 --"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun String.toDate(): LocalDate? =
    if (this.isBlank()) null
    else if (this == "today") LocalDate.now()
    else runCatching { LocalDate.parse(this) }.getOrNull()

// ---------------- parsing ----------------

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Bullets(val items: List<String>) : Block
    data class Ordered(val items: List<String>) : Block
    data class Quote(val text: String) : Block
    data class Code(val lang: String, val code: String) : Block
    data object Rule : Block
}

private fun parseBlocks(text: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = text.replace("\r\n", "\n").split('\n')
    var index = 0
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        when {
            trimmed.isEmpty() -> index++
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code.appendLine(lines[index])
                    index++
                }
                index++
                blocks.add(Block.Code(lang, code.toString().trimEnd()))
            }
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
                blocks.add(Block.Heading(level, trimmed.drop(level).trim()))
                index++
            }
            trimmed == "---" || trimmed == "***" -> {
                blocks.add(Block.Rule)
                index++
            }
            trimmed.startsWith("> ") -> {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quote.add(lines[index].trim().removePrefix(">").trim())
                    index++
                }
                blocks.add(Block.Quote(quote.joinToString(" ")))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (current.startsWith("- ") || current.startsWith("* ") || current.startsWith("• ")) {
                        items.add(current.drop(2).trim())
                        index++
                    } else break
                }
                blocks.add(Block.Bullets(items))
            }
            Regex("^\\d+\\.\\s").containsMatchIn(trimmed) -> {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val match = Regex("^\\d+\\.\\s+(.*)$").find(lines[index].trim())
                    if (match != null) {
                        items.add(match.groupValues[1])
                        index++
                    } else break
                }
                blocks.add(Block.Ordered(items))
            }
            else -> {
                val paragraph = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (current.isEmpty() || current.startsWith("#") || current.startsWith("```") ||
                        current.startsWith("- ") || current.startsWith("* ") ||
                        current.startsWith("> ") || Regex("^\\d+\\.\\s").containsMatchIn(current)
                    ) break
                    paragraph.add(current)
                    index++
                }
                if (paragraph.isNotEmpty()) blocks.add(Block.Paragraph(paragraph.joinToString(" ")))
            }
        }
    }
    return blocks
}

/** Inline bold, italic, code and links. */
@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chipStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = color
    )
    val chips = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        val pattern = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`|\\[[^]]+]\\([^)]+\\)|\\*[^*]+\\*)")
        var cursor = 0
        var chipIndex = 0
        pattern.findAll(text).forEach { match ->
            append(text.substring(cursor, match.range.first))
            val token = match.value
            when {
                token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(token.removeSurrounding("**"))
                }
                token.startsWith("`") -> {
                    val code = token.removeSurrounding("`")
                    val id = "chip${chipIndex++}"
                    val measured = measurer.measure(code, chipStyle)
                    val widthPx = measured.size.width + with(density) { 10.dp.toPx() }.toInt()
                    val width = with(density) { widthPx.toSp() }
                    chips[id] = InlineTextContent(
                        Placeholder(width, 20.sp, PlaceholderVerticalAlign.TextCenter)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(code, style = chipStyle, maxLines = 1)
                        }
                    }
                    appendInlineContent(id, code)
                }
                token.startsWith("[") -> {
                    val label = token.substringAfter('[').substringBefore(']')
                    withStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)
                    ) { append(label) }
                }
                token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removeSurrounding("*"))
                }
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    Text(
        annotated,
        inlineContent = chips,
        style = style,
        color = color,
        modifier = modifier
    )
}
