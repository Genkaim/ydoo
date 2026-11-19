package com.genkaim.zeppglass.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.genkaim.zeppglass.data.AiClient
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.BodyProfile
import com.genkaim.zeppglass.data.ChatMessage
import com.genkaim.zeppglass.data.DietStore
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.data.Meal
import com.genkaim.zeppglass.data.MealItem
import com.genkaim.zeppglass.data.WeightPoint
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.SettingsActivity
import com.genkaim.zeppglass.ui.components.CapsuleField
import com.genkaim.zeppglass.ui.components.FlatButton
import com.genkaim.zeppglass.ui.components.FlatPanel
import com.genkaim.zeppglass.ui.components.RoundIconButton
import com.genkaim.zeppglass.ui.components.SectionTitle
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

private const val FOOD_PROMPT = """
你是一名营养估算助手。看这张食物照片，把这一餐里看得见的食物逐一列出，按一人份估算：
- 每种食物：名称、大致份量、热量 kcal
- 整餐合计：总热量 kcal，蛋白质、碳水、脂肪（克）
只输出一个 JSON 对象，不要 Markdown 代码块、不要解释，格式：
{"items":[{"name":"米饭","amount":"1 碗","kcal":230},{"name":"鸡胸肉","amount":"150 g","kcal":250}],"calories":480,"protein_g":38,"carbs_g":52,"fat_g":9,"note":"估算依据"}
看不清或份量不确定时给保守估计，并在 note 里说明。不要编造看不到的食物。
"""

private const val TEXT_FOOD_PROMPT = """
你是一名营养估算助手。用户会用文字描述刚吃的东西，请据此把这一餐的食物逐一列出，按一人份估算：
- 每种食物：名称、大致份量、热量 kcal
- 整餐合计：总热量 kcal，蛋白质、碳水、脂肪（克）
只输出一个 JSON 对象，不要 Markdown 代码块、不要解释，格式：
{"items":[{"name":"米饭","amount":"1 碗","kcal":230}],"calories":480,"protein_g":38,"carbs_g":52,"fat_g":9,"note":"估算依据"}
描述里没写份量就按常见一人份估计，并在 note 里说明。不要添加用户没有提到的食物。
"""

/** A meal under construction, either straight from the camera or being edited. */
private data class MealDraft(
    /** Null for a new photo, the stored meal's id while editing. */
    val id: String?,
    val epochMs: Long,
    val photoPath: String?,
    val items: List<MealItem>,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val note: String?
)

/** The floating card that follows a photo: spinner, then success or failure. */
private sealed interface PhotoFlow {
    /** Null when the estimate came from a written description instead of a photo. */
    data class Loading(val file: File?) : PhotoFlow
    data class Failed(val file: File?, val message: String) : PhotoFlow
}

/**
 * Photo-based diet log. The photo goes to the user's own OpenAI-compatible
 * endpoint; the estimate and the photo stay on the phone afterwards. The AI
 * only proposes a list of foods — nothing is stored until the user confirms it
 * in the floating card, and every item stays editable.
 */
@Composable
fun DietDetail(data: HealthData?, settings: AppSettings, date: LocalDate = LocalDate.now()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val day = remember(date) { date }
    val today = remember { LocalDate.now() }

    LaunchedEffect(Unit) { DietStore.attach(context) }

    var flow by remember { mutableStateOf<PhotoFlow?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var draft by remember { mutableStateOf<MealDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var describeOpen by remember { mutableStateOf(false) }
    // Tapping a capture button without an endpoint used to push an inline card
    // into the page, which shoved everything below it down. A dialog asks the
    // question without moving the page.
    var setupPrompt by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }

    fun analyze(file: File) {
        flow = PhotoFlow.Loading(file)
        error = null
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { shrinkForUpload(file.readBytes()) }
                val text = withContext(Dispatchers.IO) {
                    AiClient(settings).vision(bytes, FOOD_PROMPT)
                }
                val json = parseFoodJson(text)
                    ?: error("AI 返回的内容不是可用的估算：${text.take(60)}")
                draft = MealDraft(
                    id = null,
                    epochMs = System.currentTimeMillis(),
                    photoPath = file.absolutePath,
                    items = parseItems(json),
                    proteinG = json.optDouble("protein_g", Double.NaN).takeUnless { it.isNaN() },
                    carbsG = json.optDouble("carbs_g", Double.NaN).takeUnless { it.isNaN() },
                    fatG = json.optDouble("fat_g", Double.NaN).takeUnless { it.isNaN() },
                    note = json.optString("note").takeIf { it.isNotBlank() }
                )
                flow = null
            } catch (e: Exception) {
                // The failure belongs to the photo card, not to the page.
                flow = PhotoFlow.Failed(file, e.message ?: "估算失败")
            }
        }
    }

    /**
     * The written-description path: same JSON contract as the photo, only the
     * input differs. The result goes through the same editable card, so nothing
     * is stored until the user confirms it.
     */
    fun analyzeText(described: String) {
        val text = described.trim()
        if (text.isEmpty()) return
        describeOpen = false
        error = null
        flow = PhotoFlow.Loading(null)
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    AiClient(settings).complete(
                        listOf(
                            ChatMessage("system", TEXT_FOOD_PROMPT),
                            ChatMessage("user", "这一餐：$text")
                        ),
                        maxTokens = 1024
                    )
                }
                val json = parseFoodJson(reply)
                    ?: error("AI 返回的内容不是可用的估算：${reply.take(60)}")
                val items = parseItems(json)
                // An empty list would open an editor with nothing in it.
                if (items.isEmpty()) error("AI 没有给出具体的食物，换个说法再试")
                draft = MealDraft(
                    id = null,
                    epochMs = System.currentTimeMillis(),
                    photoPath = null,
                    items = items,
                    proteinG = json.optDouble("protein_g", Double.NaN).takeUnless { it.isNaN() },
                    carbsG = json.optDouble("carbs_g", Double.NaN).takeUnless { it.isNaN() },
                    fatG = json.optDouble("fat_g", Double.NaN).takeUnless { it.isNaN() },
                    note = json.optString("note").takeIf { it.isNotBlank() }
                )
                description = ""
                flow = null
            } catch (e: Exception) {
                flow = PhotoFlow.Failed(null, e.message ?: "估算失败")
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val file = cameraFile
        cameraFile = null
        if (saved && file != null) analyze(file) else file?.delete()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) { copyToDietFile(context, uri) }
                if (file == null) {
                    flow = PhotoFlow.Failed(null, "读不到这张图片")
                } else {
                    analyze(file)
                }
            }
        }
    }

    val configured = settings.aiApiKey.isNotBlank() && settings.aiBaseUrl.isNotBlank()

    fun takePhoto() {
        if (!configured) {
            setupPrompt = true
            return
        }
        val file = DietStore.newPhotoFile()
        cameraFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        runCatching { cameraLauncher.launch(uri) }.onFailure {
            cameraFile = null
            file.delete()
            error = "没有可用的相机，换从相册选吧"
        }
    }

    fun pickPhoto() {
        if (!configured) {
            setupPrompt = true
            return
        }
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun describe() {
        if (!configured) {
            setupPrompt = true
            return
        }
        describeOpen = true
    }

    val meals = DietStore.mealsOn(day)
    val intake = meals.sumOf { it.calories }
    val target = DietStore.calorieTarget(data, day)
    val remaining = target?.minus(intake)
    // Two verdicts only: eating less than the budget is not a problem to flag,
    // so 过少 is gone; 过多 appears exactly when the intake passes the budget.
    val status = when {
        target == null || intake <= 0 -> "未记录"
        intake > target -> "过多"
        else -> "适量"
    }
    val statusColor = when (status) {
        "过多" -> MaterialTheme.colorScheme.error
        "适量" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val protein = meals.mapNotNull { it.proteinG }.takeIf { it.isNotEmpty() }?.sum()

    // The headline is what is left for today, not a score.
    Column(
        Modifier
            .fillMaxWidth()
            // Extra room below the verdict line: the weekly card used to sit
            // right under it and the two blocks read as one.
            .padding(top = 6.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "今日剩余",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                remaining?.let { kotlin.math.abs(it).toString() } ?: "--",
                style = MaterialTheme.typography.displayLarge,
                color = if (remaining != null && remaining < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                " kcal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        Text(
            if (target == null) "缺少体重资料，无法估算" else status,
            style = MaterialTheme.typography.titleSmall,
            color = statusColor
        )
    }

    // This week's saving, not a rolling seven days: it runs from Monday up to
    // the last day that has settled (a day counts once its 05:00 boundary has
    // passed, so today's figure does not drift while the user is still eating).
    // The card is always on screen — 常驻 — so the week's number is there even
    // before the first day of the week has closed.
    val now = remember { java.time.LocalTime.now() }
    val currentLogicalDay = if (now.hour < 5) today.minusDays(1) else today
    val weekStart = currentLogicalDay.with(
        java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)
    )
    var settledDays = 0
    var weekDeficit = 0
    var cursor = weekStart
    while (cursor < currentLogicalDay) {
        val logged = DietStore.mealsOn(cursor)
        val budget = DietStore.calorieTarget(data, cursor)
        // A day with no record is skipped, not read as a full deficit.
        if (logged.isNotEmpty() && budget != null) {
            weekDeficit += budget - logged.sumOf { it.calories }
            settledDays++
        }
        cursor = cursor.plusDays(1)
    }
    val activeCalories = data?.day(today)?.calories

    FlatPanel(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (weekDeficit >= 0) "本周节省" else "本周超出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (settledDays == 0) "本周还没有已结算的记录" else "本周已结算 $settledDays 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                (if (weekDeficit > 0) "+" else "") + "$weekDeficit kcal",
                style = MaterialTheme.typography.headlineSmall,
                color = if (weekDeficit >= 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }

    FlatPanel {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DietStat("已摄入", intake.toString(), "kcal", Modifier.weight(1f))
            DietStat(
                "今日活动消耗",
                activeCalories?.roundToInt()?.toString() ?: "--",
                "kcal",
                Modifier.weight(1f)
            )
        }
    }

    // 体重 / 体脂 / 骨骼肌 sit next to the calorie numbers because they are the
    // other half of the same question: what goes in, and what it builds.
    val weightsUpToDay = remember(data, day) {
        data?.weights.orEmpty().filter {
            Instant.ofEpochMilli(it.epochMs).atZone(ZoneId.systemDefault()).toLocalDate() <= day
        }
    }
    val bodyPoint = weightsUpToDay.maxByOrNull { it.epochMs }
    // The newest reading is often a plain weight (phone sync, hand entry) with no
    // impedance, while an older scale reading still has fat/muscle. Take those two
    // numbers from the newest reading that actually carries them.
    val compositionPoint = remember(weightsUpToDay) {
        val withComposition = weightsUpToDay.filter {
            it.bodyFatPct != null || it.skeletalMusclePct != null || it.skeletalMuscleKg != null
        }
        // A shared scale can store another person's much lighter reading; keep the
        // composition of a body close to the current weight, then take the newest.
        val latestKg = weightsUpToDay.maxByOrNull { it.epochMs }?.weightKg
        val sameBody = if (latestKg == null) withComposition else withComposition.filter {
            it.weightKg == null || kotlin.math.abs(it.weightKg - latestKg) <= 8.0
        }
        (sameBody.ifEmpty { withComposition }).maxByOrNull { it.epochMs }
    }
    if (bodyPoint != null) {
        BodyCompositionCard(bodyPoint, compositionPoint, data?.body)
    }

    if (meals.isNotEmpty()) {
        FlatPanel {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DietStat(
                    "蛋白质",
                    protein?.roundToInt()?.toString() ?: "--",
                    "g",
                    Modifier.weight(1f)
                )
                DietStat(
                    "碳水",
                    meals.sumOf { it.carbsG ?: 0.0 }.roundToInt().toString(),
                    "g",
                    Modifier.weight(1f)
                )
                DietStat(
                    "脂肪",
                    meals.sumOf { it.fatG ?: 0.0 }.roundToInt().toString(),
                    "g",
                    Modifier.weight(1f)
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlatButton(
            text = "拍照估算",
            onClick = { takePhoto() },
            modifier = Modifier.weight(1.2f),
            icon = Icons.Rounded.CameraAlt,
            horizontalPadding = 8.dp
        )
        FlatButton(
            text = "相册",
            onClick = { pickPhoto() },
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.PhotoLibrary,
            primary = false,
            horizontalPadding = 8.dp
        )
        FlatButton(
            text = "描述",
            onClick = { describe() },
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.EditNote,
            primary = false,
            horizontalPadding = 8.dp
        )
    }
    if (!configured) {
        FlatPanel {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "还没有配置 AI 接入点，拍照后无法估算。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                ) { Text("去设置") }
            }
        }
    }

    error?.let {
        FlatPanel {
            Column(Modifier.padding(16.dp)) {
                Text(it, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { error = null }) { Text("知道了") }
            }
        }
    }

    SectionTitle("今天的记录", trailing = if (meals.isEmpty()) null else "${meals.size} 餐")
    if (meals.isEmpty()) {
        FlatPanel {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.Restaurant,
                    contentDescription = null,
                    tint = LocalZgPalette.current.accent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("还没有记录", style = MaterialTheme.typography.titleSmall)
            }
        }
    } else {
        FlatPanel {
            Column(Modifier.padding(vertical = 4.dp)) {
                meals.reversed().forEach { meal ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MealThumb(meal.photoPath, 54.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                Fmt.time(meal.epochMs) + " · ${meal.calories} kcal",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                meal.items.joinToString("、") { food ->
                                    food.amount?.let { "${food.name} $it" } ?: food.name
                                }.ifBlank { "一餐" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                draft = MealDraft(
                                    id = meal.id,
                                    epochMs = meal.epochMs,
                                    photoPath = meal.photoPath,
                                    items = meal.items,
                                    proteinG = meal.proteinG,
                                    carbsG = meal.carbsG,
                                    fatG = meal.fatG,
                                    note = meal.note
                                )
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "编辑这一餐",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Everything about how the number is built lives in one card at the bottom.
    SectionTitle("怎么算的")
    FlatPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "今日可用 = 基础代谢（按身高、生日、性别与体重算）+ 手表当天的活动消耗。",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "剩余 = 今日可用 − 已摄入；只有摄入超过今日可用时才记为过多。",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "本周节省从周一起算：每天（可用 − 已摄入）相加；一天要等次日 05:00 之后才结算，所以当天不会随时变动；没记录的天不当作缺口。",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "拍照和相册识别需要支持图片输入的模型（如 GPT-4o、Gemini、Qwen-VL）；文字描述用普通对话模型即可。内容只发给你配置的 AI 接入点，识别结果确认后才写进本机记录。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Floating cards: the spinner / failure while the model looks at the photo,
    // then the editable list the user confirms.
    when (val state = flow) {
        is PhotoFlow.Loading -> FloatingCard(onDismiss = {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.file != null) {
                    MealThumb(state.file.absolutePath, 54.dp)
                } else {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("AI 正在估算这份食物", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "识别食物、份量与热量，通常需要几秒",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is PhotoFlow.Failed -> FloatingCard(onDismiss = {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.file?.let {
                    MealThumb(it.absolutePath, 54.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text("这次没识别出来", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlatButton(
                text = "知道了",
                onClick = {
                    state.file?.let { runCatching { it.delete() } }
                    flow = null
                    draft = null
                },
                modifier = Modifier.fillMaxWidth(),
                primary = false
            )
        }
        null -> Unit
    }

    draft?.let { current ->
        MealEditorCard(
            initial = current.items,
            isNew = current.id == null,
            onDismiss = {
                if (current.id == null) {
                    // A cancelled photo leaves nothing behind.
                    current.photoPath?.let { path -> runCatching { File(path).delete() } }
                }
                draft = null
            },
            onDelete = if (current.id != null) {
                {
                    DietStore.meals.firstOrNull { it.id == current.id }?.let { DietStore.remove(it) }
                    draft = null
                }
            } else null,
            onConfirm = { items ->
                val total = items.sumOf { it.kcal ?: 0 }.takeIf { it > 0 }
                if (current.id == null) {
                    DietStore.add(
                        Meal(
                            id = UUID.randomUUID().toString(),
                            epochMs = current.epochMs,
                            photoPath = current.photoPath,
                            calories = total ?: 0,
                            proteinG = current.proteinG,
                            carbsG = current.carbsG,
                            fatG = current.fatG,
                            items = items,
                            note = current.note
                        )
                    )
                } else {
                    DietStore.meals.firstOrNull { it.id == current.id }?.let { stored ->
                        DietStore.update(
                            stored.copy(
                                calories = total ?: stored.calories,
                                items = items
                            )
                        )
                    }
                }
                draft = null
            }
        )
    }

    if (setupPrompt) {
        AlertDialog(
            onDismissRequest = { setupPrompt = false },
            title = { Text("还没有配置 AI 接入点") },
            text = {
                Text("拍照、相册和文字描述都要走你自己填的 AI 接入点，先在设置里填好地址、密钥和模型。")
            },
            confirmButton = {
                TextButton(onClick = {
                    setupPrompt = false
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { setupPrompt = false }) { Text("取消") }
            }
        )
    }

    if (describeOpen) {
        DescriptionDialog(
            text = description,
            onTextChange = { description = it },
            onDismiss = { describeOpen = false },
            onSubmit = { analyzeText(it) }
        )
    }
}

/**
 * Written description of a meal. The AI turns it into the same item list the
 * photo path produces, so the confirmation card afterwards is identical.
 */
@Composable
private fun DescriptionDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false is what makes the keyboard inset
        // reach these composables. Without it the card never moves, and the
        // 估算 key sits behind the keyboard — which reads as "there is no
        // confirm button at all".
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            FlatPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("用文字描述这一餐", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "例如：一碗米饭、两个鸡腿、一份炒青菜、一杯无糖豆浆。份量没写就按常见一人份算。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CapsuleField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = "吃了什么，大概多少…",
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "取消",
                            onClick = onDismiss
                        )
                        RoundIconButton(
                            icon = Icons.Rounded.Check,
                            contentDescription = "估算",
                            onClick = { onSubmit(text) },
                            enabled = text.isNotBlank(),
                            primary = text.isNotBlank()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DietStat(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge
            )
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp)
                )
            }
        }
    }
}

/**
 * The scale numbers, next to the calorie ones: weight, body fat and skeletal
 * muscle, each with a one-word rating to the right of the number.
 *
 * Ratings use Chinese adult reference bands (BMI <18.5 偏轻, 18.5–24 正常,
 * 24–28 偏高, ≥28 肥胖; body fat and muscle differ by sex). Normal readings stay
 * in ink; only a reading outside its band is coloured. They describe the
 * reading, not the person — a missing scale field shows `--` instead of a guess.
 */
@Composable
private fun BodyCompositionCard(
    point: WeightPoint,
    composition: WeightPoint?,
    body: BodyProfile?
) {
    val palette = LocalZgPalette.current
    val male = body?.isMale
    // Prefer the scale's own BMI (it knows the height it used); fall back to the
    // profile height when the record is a hand-typed weight.
    val bmi = point.bmi ?: run {
        val h = body?.heightCm?.takeIf { it > 0 }?.toDouble()
        val w = point.weightKg
        if (h != null && w != null) w / ((h / 100) * (h / 100)) else null
    }
    val normalInk = MaterialTheme.colorScheme.onSurface

    val weightVerdict = when {
        bmi == null -> null
        bmi < 18.5 -> "偏轻" to palette.warn
        bmi < 24.0 -> "正常" to normalInk
        bmi < 28.0 -> "偏高" to palette.warn
        else -> "肥胖" to palette.critical
    }
    val fat = composition?.bodyFatPct
    val fatVerdict = when {
        fat == null -> null
        // Bands differ by sex: 10–20% is normal for men, 18–28% for women.
        male == true -> when {
            fat < 10.0 -> "偏低" to palette.warn
            fat <= 20.0 -> "正常" to normalInk
            fat <= 25.0 -> "偏高" to palette.warn
            else -> "偏高" to palette.critical
        }
        male == false -> when {
            fat < 18.0 -> "偏低" to palette.warn
            fat <= 28.0 -> "正常" to normalInk
            fat <= 34.0 -> "偏高" to palette.warn
            else -> "偏高" to palette.critical
        }
        else -> if (fat in 15.0..25.0) "正常" to normalInk
        else "偏离" to palette.warn
    }
    val muscle = composition?.skeletalMusclePct
    val muscleVerdict = when {
        muscle == null -> null
        // Skeletal muscle has a floor rather than a ceiling: more is fine.
        muscle < (if (male == false) 35.0 else 40.0) -> "偏低" to palette.warn
        else -> "正常" to normalInk
    }
    val muscleKg = composition?.skeletalMuscleKg

    FlatPanel {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BodyStat(
                "体重",
                point.weightKg?.let { Fmt.oneDecimal(it) } ?: "--",
                "kg",
                MaterialTheme.colorScheme.primary,
                weightVerdict,
                Modifier.weight(1f)
            )
            BodyStat(
                "体脂",
                fat?.let { Fmt.oneDecimal(it) } ?: "--",
                "%",
                palette.workout,
                fatVerdict,
                Modifier.weight(1f)
            )
            BodyStat(
                "骨骼肌",
                muscleKg?.let { Fmt.oneDecimal(it) } ?: "--",
                "kg",
                palette.sleepRem,
                muscleVerdict,
                Modifier.weight(1f)
            )
        }
    }
}

/** One column of the body-composition card: label, then value with the verdict
 *  sitting to its right. */
@Composable
private fun BodyStat(
    label: String,
    value: String,
    unit: String,
    tint: Color,
    verdict: Pair<String, Color>?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val abnormal = verdict != null && verdict.first != "正常"
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
            )
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
    }
}

/** A card that floats over the page instead of taking a slot in the list. */
@Composable
private fun FloatingCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            FlatPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp)) { content() }
            }
        }
    }
}

private class EditableFood(name: String, amount: String, kcal: String) {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
    var kcal by mutableStateOf(kcal)
}

/**
 * The one card used both right after a photo and when editing a stored meal.
 * Foods are listed one per row — name on the left, portion and energy on the
 * right — and a row taps into a small editor. Adding a food opens that same
 * editor with empty fields, so a blank row never enters the list. Delete sits
 * bottom-left as a red circle, cancel and confirm on the same line.
 */
@Composable
private fun MealEditorCard(
    initial: List<MealItem>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onConfirm: (List<MealItem>) -> Unit
) {
    val rows = remember {
        mutableStateListOf<EditableFood>().apply {
            initial.forEach { food ->
                add(EditableFood(food.name, food.amount.orEmpty(), food.kcal?.toString().orEmpty()))
            }
        }
    }
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    val total = rows.sumOf { it.kcal.toIntOrNull() ?: 0 }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            FlatPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isNew) "这一餐的食物" else "编辑这一餐",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭")
                        }
                    }
                    rows.forEachIndexed { index, food ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                food.name.ifBlank { "未命名" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                listOfNotNull(
                                    food.amount.trim().takeIf { it.isNotEmpty() },
                                    food.kcal.takeIf { it.isNotEmpty() }?.let { "$it kcal" }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { editing = index },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "编辑这一项",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    TextButton(onClick = { adding = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加食物")
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "合计",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$total kcal",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onDelete != null) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除这一餐",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        RoundIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "取消",
                            onClick = onDismiss
                        )
                        RoundIconButton(
                            icon = Icons.Rounded.Check,
                            contentDescription = "完成",
                            onClick = {
                                val items = rows.mapNotNull { food ->
                                    val name = food.name.trim()
                                    if (name.isEmpty()) null
                                    else MealItem(
                                        name = name,
                                        amount = food.amount.trim().takeIf { it.isNotEmpty() },
                                        kcal = food.kcal.toIntOrNull()
                                    )
                                }
                                onConfirm(items)
                            },
                            primary = true
                        )
                    }
                }
            }
        }
    }

    editing?.let { index ->
        val food = rows.getOrNull(index)
        if (food != null) {
            FoodEditor(
                initial = food,
                title = "修改食物",
                onDismiss = { editing = null },
                onApply = {
                    rows[index] = it
                    editing = null
                }
            )
        }
    }

    if (adding) {
        FoodEditor(
            initial = EditableFood("", "", ""),
            title = "添加食物",
            onDismiss = { adding = false },
            onApply = {
                rows.add(it)
                adding = false
            }
        )
    }
}

/**
 * Small editor for a single food row. Fields are the same borderless capsules
 * as the rest of the app, and the card lifts above the keyboard while typing.
 */
@Composable
private fun FoodEditor(
    initial: EditableFood,
    title: String,
    onDismiss: () -> Unit,
    onApply: (EditableFood) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var amount by remember { mutableStateOf(initial.amount) }
    var kcal by remember { mutableStateOf(initial.kcal) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            FlatPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    FoodFieldLabel("名称")
                    CapsuleField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "米饭"
                    )
                    FoodFieldLabel("分量")
                    CapsuleField(
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = "1 碗 / 150 g"
                    )
                    FoodFieldLabel("热量")
                    CapsuleField(
                        value = kcal,
                        onValueChange = { text -> kcal = text.filter { it.isDigit() }.take(5) },
                        placeholder = "kcal",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "取消",
                            onClick = onDismiss
                        )
                        RoundIconButton(
                            icon = Icons.Rounded.Check,
                            contentDescription = "确定",
                            onClick = { onApply(EditableFood(name.trim(), amount.trim(), kcal.trim())) },
                            enabled = name.isNotBlank(),
                            primary = name.isNotBlank()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodFieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp)
    )
}

/** Reads the items array; accepts objects and the old plain strings. */
private fun parseItems(json: JSONObject): List<MealItem> {
    val array: JSONArray = json.optJSONArray("items")
        ?: json.optJSONArray("foods")
        ?: return emptyList()
    val items = mutableListOf<MealItem>()
    for (i in 0 until array.length()) {
        when (val entry = array.opt(i)) {
            is JSONObject -> {
                val name = entry.optString("name").trim()
                if (name.isEmpty()) continue
                items.add(
                    MealItem(
                        name = name,
                        amount = entry.optString("amount").trim().takeIf { it.isNotEmpty() },
                        kcal = entry.optDouble("kcal", -1.0).takeIf { it >= 0 }?.roundToInt()
                    )
                )
            }
            is String -> entry.trim().takeIf { it.isNotEmpty() }?.let { items.add(MealItem(it)) }
        }
    }
    return items
}

private fun parseFoodJson(text: String): JSONObject? {
    val cleaned = text.replace("```json", "```").trim()
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    if (start >= 0 && end > start) {
        runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()?.let { return it }
    }
    // Some models answer with a bare array of foods.
    val arrayStart = cleaned.indexOf('[')
    val arrayEnd = cleaned.lastIndexOf(']')
    if (arrayStart >= 0 && arrayEnd > arrayStart) {
        val array = runCatching { JSONArray(cleaned.substring(arrayStart, arrayEnd + 1)) }.getOrNull()
        if (array != null) return JSONObject().put("items", array)
    }
    return null
}

private fun copyToDietFile(context: Context, uri: Uri): File? = runCatching {
    val target = DietStore.newPhotoFile()
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    target
}.getOrNull()

/** Keep the upload small: max side 1024 px, JPEG 82. */
private fun shrinkForUpload(bytes: ByteArray): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return bytes
    val longSide = max(decoded.width, decoded.height)
    val scale = if (longSide > 1024) 1024f / longSide else 1f
    val out = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt(),
            (decoded.height * scale).roundToInt(),
            true
        )
    } else decoded
    return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
}

@Composable
private fun MealThumb(path: String?, size: Dp) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = path?.let {
            withContext(Dispatchers.IO) {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(it, options)?.asImageBitmap()
            }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

