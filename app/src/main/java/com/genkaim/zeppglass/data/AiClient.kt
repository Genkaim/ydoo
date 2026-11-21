package com.genkaim.zeppglass.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

data class ChatMessage(val role: String, val content: String)

class AiException(val code: Int, message: String) : Exception(message)

/**
 * Reads a text field the way a caller expects, which `optString` does not do:
 * for a key whose value is JSON `null` it answers the *four characters*
 * "null". Reasoning models stream `{"delta":{"content":null,
 * "reasoning_content":"…"}}` for every thought token, so the old code appended
 * "null" per chunk and the summary card filled with nullnullnull….
 *
 * Missing key and explicit null both read as empty; everything else is
 * stringified as before.
 */
internal fun JSONObject.text(key: String): String {
    val value = opt(key)
    return when (value) {
        null, JSONObject.NULL -> ""
        is String -> value
        else -> value.toString()
    }
}

/**
 * One SSE chat chunk, already null-safe: `content` is the answer, `reasoning`
 * is the model's private thinking (shown by nobody). Either may be empty.
 */
internal fun JSONObject.sseDelta(): Pair<String, String> {
    val delta = optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("delta")
        ?: return "" to ""
    return delta.text("content") to delta.text("reasoning_content")
}

/**
 * Strips the run of "null" that builds before this one appended for every
 * reasoning-only chunk (thinking is streamed before the answer, so the run is
 * always a prefix). Text that was nothing but that run becomes empty, which
 * lets stored summaries and archived chats be treated as absent instead of
 * being shown again.
 *
 * One "null" in front of real words is left alone: "nullable" is a word, while
 * the bug always produced a run (thinking emits many chunks).
 */
internal fun String.withoutNullChunks(): String {
    var start = 0
    while (startsWith("null", start)) start += 4
    if (start == 0) return this
    if (start == length) return ""
    return if (start >= 8) substring(start) else this
}

/**
 * OpenAI-compatible chat client. Works with the official API as well as any
 * compatible gateway (DeepSeek, Moonshot, Ollama, one-api, ...), because the
 * user supplies the base URL and model.
 */
class AiClient(private val settings: AppSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    companion object {
        /** Default when the field is left empty; the user can type any model. */
        const val DEFAULT_MODEL = "deepseek-v4.1-flash"
    }

    fun endpoint(): String {
        val base = settings.aiBaseUrl.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    /**
     * Custom headers from settings ("Key: Value" per line), plus the session
     * header OpenCode's gateway routes on. An explicitly configured header wins.
     */
    private fun applyHeaders(builder: Request.Builder): Request.Builder {
        builder.header("Authorization", "Bearer ${settings.aiApiKey.trim()}")
        val explicit = mutableSetOf<String>()
        settings.aiHeaders.lineSequence().forEach { line ->
            val index = line.indexOf(':')
            if (index > 0) {
                val name = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    builder.header(name, value)
                    explicit.add(name.lowercase())
                }
            }
        }
        if ("opencode" in endpoint() && "x-opencode-session" !in explicit) {
            builder.header("x-opencode-session", settings.aiSessionId)
        }
        return builder
    }

    fun isConfigured(): Boolean = settings.aiApiKey.isNotBlank() && settings.aiBaseUrl.isNotBlank()

    fun stream(messages: List<ChatMessage>): Flow<String> = flow {
        val payload = JSONObject().apply {
            put("model", settings.aiModel.ifBlank { DEFAULT_MODEL })
            put("stream", true)
            // Long answers were cut by gateways that default to a small limit.
            put("max_tokens", 8192)
            put("temperature", 0.5)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            })
        }
        val request = applyHeaders(
            Request.Builder()
                .url(endpoint())
                .header("Accept", "text/event-stream")
                .post(payload.toString().toRequestBody(jsonType))
        ).build()

        client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful) {
                val errorText = body?.string().orEmpty()
                val hint = when {
                    errorText.contains("MissingSessionID", ignoreCase = true) ->
                        "接入点要求会话头 x-opencode-session，可在设置里补一行；应用已会自动添加"
                    response.code == 401 || response.code == 403 -> "密钥被拒绝"
                    response.code == 404 -> "接入点或模型不存在"
                    response.code == 429 -> "请求太频繁或额度不足"
                    else -> "服务返回 HTTP ${response.code}"
                }
                throw AiException(response.code, "$hint：${errorText.take(180)}")
            }
            if (body == null) throw AiException(0, "服务没有返回内容")

            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.contains("application/json", ignoreCase = true)) {
                // Some gateways ignore `stream` and answer in one piece.
                val bodyText = body.string()
                val content = runCatching {
                    JSONObject(bodyText).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").text("content")
                }.getOrNull()
                if (content.isNullOrEmpty()) throw AiException(0, "无法解析返回内容")
                emit(content)
                return@use
            }

            val source = body.source()
            var emitted = 0
            var reasoned = false
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || line.startsWith(":")) continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val (chunk, thinking) = runCatching { JSONObject(data).sseDelta() }
                    .getOrDefault("" to "")
                if (thinking.isNotEmpty()) reasoned = true
                if (chunk.isNotEmpty()) {
                    emitted += chunk.length
                    emit(chunk)
                }
            }
            // Reasoning-only answers would otherwise end as a blank card: say
            // so instead of leaving the reader with nothing.
            if (emitted == 0 && reasoned) {
                throw AiException(0, "模型只输出了思考过程，没有给出回答")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * One cheap round trip so a typo in the base URL, key or model is caught in
     * settings. It sends the smallest legal chat request and treats any 2xx that
     * parses as a chat completion as success: gateways that return an empty
     * `content` for a 1-token answer used to look like a broken endpoint.
     */
    suspend fun test(): Result<String> = runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (settings.aiBaseUrl.isBlank()) throw AiException(0, "先填 Base URL")
            if (settings.aiApiKey.isBlank()) throw AiException(0, "先填 API Key")
            val model = settings.aiModel.ifBlank { DEFAULT_MODEL }
            val url = endpoint()
            val payload = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", "ping")
                    )
                )
            }
            val request = applyHeaders(
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .post(payload.toString().toRequestBody(jsonType))
            ).build()

            val started = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val hint = when (response.code) {
                        401, 403 -> "密钥被拒绝"
                        404 -> "接入点或模型不存在（$model）"
                        400 -> "请求被拒绝，检查模型名与参数"
                        429 -> "请求太频繁或额度不足"
                        else -> "服务返回 HTTP ${response.code}"
                    }
                    throw AiException(response.code, "$hint：${text.take(160)}")
                }
                val json = runCatching { JSONObject(text) }.getOrNull()
                val hasChoices = json?.optJSONArray("choices")?.length()?.let { it > 0 } == true
                if (json == null || !hasChoices) {
                    val snippet = text.take(120).ifBlank { "空响应" }
                    throw AiException(0, "返回的不是 chat completion：$snippet")
                }
                val ms = System.currentTimeMillis() - started
                "连接成功 · $model · ${ms}ms"
            }
        }
    }

    /**
     * Ask a vision-capable model to describe one photo. The image is sent as a
     * data URL in the OpenAI content-array format.
     *
     * Reasoning models spend output tokens on `reasoning_content` before they
     * write `content`; a budget sized for the answer alone comes back with an
     * empty message and `finish_reason=length`. That was why a perfectly good
     * photo produced "no result", so the budget starts generous and doubles when
     * the model only reasoned. Shape errors (some gateways refuse `max_tokens`
     * or an object `image_url`) fall back to the other common forms.
     */
    fun vision(image: ByteArray, prompt: String, maxTokens: Int = 4096): String {
        val dataUrl = "data:image/jpeg;base64," +
            Base64.getEncoder().encodeToString(image)
        // Photos need a vision model; it may differ from the chat model.
        val visionModel = settings.aiVisionModel.ifBlank {
            settings.aiModel.ifBlank { DEFAULT_MODEL }
        }

        fun payload(budget: Int?, imageAsPlainString: Boolean): JSONObject =
            JSONObject().apply {
                put("model", visionModel)
                put("stream", false)
                if (budget != null) put("max_tokens", budget)
                put("temperature", 0.2)
                put("messages", JSONArray().apply {
                    put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                JSONArray().apply {
                                    put(JSONObject().put("type", "text").put("text", prompt))
                                    val image = if (imageAsPlainString) {
                                        JSONObject().put("type", "image_url").put("image_url", dataUrl)
                                    } else {
                                        JSONObject().put("type", "image_url")
                                            .put("image_url", JSONObject().put("url", dataUrl))
                                    }
                                    put(image)
                                }
                            )
                    )
                })
            }

        /** content, finish_reason, whether the model produced reasoning. */
        fun call(payload: JSONObject): Triple<String, String, Boolean> {
            val request = applyHeaders(
                Request.Builder()
                    .url(endpoint())
                    .post(payload.toString().toRequestBody(jsonType))
            ).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AiException(response.code, visionHint(response.code, text))
                }
                val json = runCatching { JSONObject(text) }.getOrNull()
                    ?: throw AiException(0, "无法解析返回内容：${text.take(160)}")
                val choice = json.optJSONArray("choices")?.optJSONObject(0)
                    ?: throw AiException(0, "返回里没有 choices：${text.take(160)}")
                val message = choice.optJSONObject("message")
                val content = message?.text("content").orEmpty().trim()
                val finish = choice.optString("finish_reason")
                val reasoned = message?.text("reasoning_content").orEmpty().isNotBlank()
                return Triple(content, finish, reasoned)
            }
        }

        var budget = maxTokens
        var last: AiException? = null
        repeat(3) {
            val reply = try {
                call(payload(budget, false))
            } catch (e: AiException) {
                last = e
                // Retry the shape variants only for format rejections.
                if (e.code == 400 || e.code == 415 || e.code == 422) {
                    val alternative = runCatching {
                        call(payload(budget, true))
                    }.getOrElse { inner ->
                        if (inner is AiException) last = inner
                        Triple("", "", false)
                    }
                    if (alternative.first.isNotEmpty()) return alternative.first
                }
                return@repeat
            }
            if (reply.first.isNotEmpty()) return reply.first
            // Nothing but reasoning: give the model room to finish the answer.
            if (reply.second == "length" || reply.third) {
                budget = (budget * 2).coerceAtMost(16384)
                return@repeat
            }
            throw AiException(0, "模型没有返回文字")
        }
        throw last ?: AiException(0, "模型只输出了思考过程，没有给出结果")
    }

    private fun visionHint(code: Int, body: String): String {
        val lower = body.lowercase()
        val unsupportedImage = lower.contains("image") &&
            (lower.contains("not support") || lower.contains("unsupported") ||
                lower.contains("invalid content") || lower.contains("cannot"))
        return when {
            code == 400 && unsupportedImage ->
                "这个接入点不接受图片参数，检查模型是否支持视觉输入"
            code == 401 || code == 403 -> "密钥被拒绝"
            code == 404 -> "接入点或模型不存在"
            code == 413 -> "图片太大，换一张小一点的"
            code == 429 -> "请求太频繁或额度不足"
            else -> "服务返回 HTTP $code：${body.take(120)}"
        }
    }

    /**
     * Non-streaming completion, used for the planning turn of the data agent
     * and for generating starter suggestions.
     */
    fun complete(messages: List<ChatMessage>, maxTokens: Int = 400): String {
        val payload = JSONObject().apply {
            put("model", settings.aiModel.ifBlank { DEFAULT_MODEL })
            put("stream", false)
            put("max_tokens", maxTokens)
            put("temperature", 0.4)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            })
        }
        val request = applyHeaders(
            Request.Builder()
                .url(endpoint())
                .post(payload.toString().toRequestBody(jsonType))
        ).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val hint = when {
                    text.contains("MissingSessionID", ignoreCase = true) ->
                        "接入点要求会话头 x-opencode-session，可在设置里补一行；应用已会自动添加"
                    response.code == 401 || response.code == 403 -> "密钥被拒绝"
                    response.code == 404 -> "接入点或模型不存在"
                    else -> "服务返回 HTTP ${response.code}"
                }
                throw AiException(response.code, "$hint：${text.take(180)}")
            }
            val parsed = runCatching {
                JSONObject(text).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message")
            }.getOrElse { throw AiException(0, "无法解析返回内容") }
            val content = parsed.text("content").trim()
            if (content.isNotEmpty()) return content
            if (parsed.text("reasoning_content").isNotBlank()) {
                throw AiException(0, "模型只输出了思考过程，没有给出回答")
            }
            throw AiException(0, "模型没有返回内容")
        }
    }
}

/**
 * Compact, factual digest of what the app actually holds. Missing values are
 * left out instead of being written as zeros, so the model cannot mistake "not
 * measured" for "measured zero".
 */
object AiContext {
    /** Sleep reads as "7 小时 32 分" so the model never answers in raw minutes. */
    private fun hoursMinutes(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h <= 0 -> "$m 分"
            m == 0 -> "$h 小时"
            else -> "$h 小时 $m 分"
        }
    }

    fun digest(data: HealthData?, days: Int = 7, goal: DietGoal = DietGoal.CUT): String {
        if (data == null) return "（本机还没有健康数据）"
        val builder = StringBuilder()
        val today = data.days.lastOrNull()?.date
        builder.appendLine("窗口：最近 $days 天的本机数据。单位：步 steps，距离 m，睡眠写成「X 小时 Y 分」，心率 bpm。")

        val recentDays = data.days.takeLast(days)
        if (recentDays.isNotEmpty()) {
            builder.appendLine("\n每日活动：")
            recentDays.forEach { d ->
                builder.appendLine(
                    "- ${d.date}: " + buildList {
                        d.steps?.let { add("步数 $it/${d.stepGoal ?: "?"}") }
                        d.distanceM?.let { add("距离 ${(it / 1000).toInt()} km") }
                        d.calories?.let { add("消耗 ${it.toInt()} kcal") }
                        d.activityMinutes?.let { m ->
                            add("活动时长 $m" + (d.activityGoal?.let { g -> "/$g" } ?: "") + " 分钟")
                        }
                        d.restingHr?.let { add("静息心率 $it") }
                        d.sleepMinutes?.let { add("睡眠 ${hoursMinutes(it)}") }
                    }.joinToString("，").ifEmpty { "没有记录" }
                )
            }
        }

        val nights = data.nights.takeLast(days)
        if (nights.isNotEmpty()) {
            builder.appendLine("\n睡眠：")
            nights.forEach { n ->
                val sleepTotal = listOfNotNull(n.deepMinutes, n.lightMinutes, n.remMinutes)
                    .sum().takeIf { it > 0 }
                fun pct(minutes: Int?): String? =
                    if (minutes == null || sleepTotal == null) null
                    else "（${minutes * 100 / sleepTotal}%）"
                builder.appendLine(
                    "- ${n.date}: 总 ${hoursMinutes(n.totalMinutes)}" +
                        (n.score?.let { "，评分 $it" } ?: "") +
                        (n.deepMinutes?.let { "，深睡 ${hoursMinutes(it)}${pct(it) ?: ""}" } ?: "") +
                        (n.lightMinutes?.let { "，浅睡 ${hoursMinutes(it)}${pct(it) ?: ""}" } ?: "") +
                        (n.remMinutes?.let { "，REM ${hoursMinutes(it)}${pct(it) ?: ""}" } ?: "") +
                        (n.wakeCount?.let { "，清醒 $it 次" }
                            ?: n.awakeMinutes?.let { "，清醒 ${hoursMinutes(it)}" } ?: "") +
                        (n.restingHr?.let { "，睡眠心率 $it" } ?: "")
                )
            }
        }

        val readiness = data.readiness.takeLast(days)
        val scoredReadiness = readiness.filter { it.officialScore != null }
        if (scoredReadiness.isNotEmpty()) {
            builder.appendLine("\n准备度（手表给出的评分，1-100；没有评分的日子不在这里）：")
            scoredReadiness.forEach { r ->
                builder.appendLine(
                    "- ${r.date}: " + buildList {
                        r.officialScore?.let { add("准备度 $it") }
                        r.hrvScore?.let { add("HRV 分 $it") }
                        r.sleepHrv?.let { add("睡眠 HRV $it ms") }
                        r.sleepRhr?.let { add("睡眠静息心率 $it bpm") }
                        r.mentalScore?.let { add("精神分 $it") }
                        r.physicalScore?.let { add("体力分 $it") }
                    }.joinToString("，").ifEmpty { "没有记录" }
                )
            }
        }
        // Rows without a 准备度 score are still useful (HRV, resting heart rate)
        // but they are not a readiness reading; saying so keeps the model from
        // reporting a score the watch never gave.
        val recoveryOnly = readiness.filter {
            it.officialScore == null &&
                (it.sleepHrv != null || it.sleepRhr != null || it.skinTempScore != null)
        }
        if (recoveryOnly.isNotEmpty()) {
            builder.appendLine("\n恢复指标（以下日子手表没有给出准备度评分，这些不是准备度）：")
            recoveryOnly.forEach { r ->
                builder.appendLine(
                    "- ${r.date}: " + buildList {
                        r.sleepHrv?.let { add("睡眠 HRV $it ms") }
                        r.sleepRhr?.let { add("睡眠静息心率 $it bpm") }
                        r.skinTempScore?.let { add("皮肤温度分 $it") }
                    }.joinToString("，")
                )
            }
        }

        val charge = data.chargeDays.takeLast(days)
        if (charge.isNotEmpty()) {
            builder.appendLine("\nHybrid Charge（0-100；晨起是刚醒来时的读数，当前是此刻的读数，两者不是一个数）：")
            charge.forEach { c ->
                builder.appendLine(
                    "- ${c.date}: " + buildList {
                        val morning = c.morningScore(data.nightOn(c.date)?.endEpochSec)
                        add("晨起 ${morning ?: "未给出"}")
                        if (c.date == today) {
                            add("当前 ${c.total ?: "未给出"}")
                            c.physical?.let { add("当前体力 ${it.toInt()}") }
                            c.mental?.let { add("当前精神 ${it.toInt()}") }
                        }
                    }.joinToString("，")
                )
            }
        }

        val load = data.load.takeLast(days)
        if (load.isNotEmpty()) {
            builder.appendLine("\n训练负荷：")
            load.forEach { l ->
                builder.appendLine(
                    "- ${l.date}: 当天 ${l.load?.toInt() ?: "?"}，7 天累计 ${l.weekLoad?.toInt() ?: "?"}" +
                        (l.optimalMin?.let { "，合适区间 ${it.toInt()}-${l.optimalMax?.toInt() ?: "?"}" } ?: "")
                )
            }
        }

        val pai = data.pai.takeLast(days)
        if (pai.isNotEmpty()) {
            builder.appendLine("\nPAI：")
            pai.forEach { p -> builder.appendLine("- ${p.date}: 当日 ${p.daily ?: "?"}，累计 ${p.total ?: "?"}") }
        }

        val stress = data.stress.takeLast(days)
        if (stress.isNotEmpty()) {
            builder.appendLine("\n压力（0-100）：")
            stress.forEach { s -> builder.appendLine("- ${s.date}: 平均 ${s.average ?: "?"}（${s.minimum ?: "?"}-${s.maximum ?: "?"}）") }
            data.stress.lastOrNull()?.curve?.lastOrNull()?.let { sample ->
                builder.appendLine("  最新一次取样：${sample.second}")
            }
        }

        data.vo2Max?.let { builder.appendLine("\n最大摄氧量：$it ml/kg/min") }

        val workouts = data.workouts.take(5)
        if (workouts.isNotEmpty()) {
            builder.appendLine("\n最近运动：")
            workouts.forEach { w ->
                builder.appendLine(
                    "- ${w.name}：${w.durationSec / 60} 分钟" +
                        (if (w.distanceM > 100) "，${"%.1f".format(w.distanceM / 1000)} km" else "") +
                        (w.avgHr?.let { "，平均心率 ${it.toInt()}" } ?: "") +
                        (w.load?.let { "，负荷 ${it.toInt()}" } ?: "")
                )
            }
        }

        // Body composition: weight/BMI and the composition figures are listed
        // separately per source, each with its own date, because the wearable and
        // the scale can be used on different days and a manual weight entry
        // carries no composition at all.
        if (data.weights.isNotEmpty()) {
            val body = data.body
            val male = body?.isMale ?: true
            val heightM = body?.heightCm?.takeIf { it > 100 }?.div(100.0)
            builder.appendLine(
                "\n体重与身体成分（体脂秤 / 穿戴设备分开；体重与身体成分各自的日期都写明了，" +
                    "读的时候不要把一个日期套到另一个数字上）："
            )

            fun dayOf(point: WeightPoint) =
                Instant.ofEpochMilli(point.epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

            fun describeWeight(label: String, point: WeightPoint?) {
                if (point == null) {
                    builder.appendLine("- $label 体重：没有记录")
                    return
                }
                val text = buildList {
                    point.weightKg?.let { add("体重 ${"%.1f".format(it)} kg") }
                    point.bmi?.let { add("BMI ${"%.1f".format(it)}") }
                }.joinToString("，").ifEmpty { "没有体重" }
                builder.appendLine("- $label 体重（${dayOf(point)}）：$text")
            }

            fun describeComposition(label: String, point: WeightPoint?) {
                if (point == null) {
                    builder.appendLine("- $label 身体成分：没有记录")
                    return
                }
                val parts = buildList {
                    point.bodyFatPct?.let { add("体脂 ${"%.1f".format(it)}%") }
                    point.muscleKg?.let { add("肌肉 ${"%.1f".format(it)} kg") }
                    point.skeletalMuscleKg?.let { add("骨骼肌 ${"%.1f".format(it)} kg") }
                    point.bodyWaterPct?.let { add("体水分 ${"%.1f".format(it)}%") }
                    point.boneMassKg?.let { add("骨量 ${"%.2f".format(it)} kg") }
                    point.visceralFat?.let { add("内脏脂肪 ${it.toInt()}") }
                    point.metabolismKcal?.let { add("基础代谢 ${it.toInt()} kcal") }
                    point.proteinPct?.let { add("蛋白质 ${"%.1f".format(it)}%") }
                }
                val bmrEstimate = mifflinBmr(body, point.weightKg)
                val score = bodyCompositionScore(
                    bmi = point.bmi,
                    fat = point.bodyFatPct,
                    muscleKg = point.muscleKg,
                    water = point.bodyWaterPct,
                    visceral = point.visceralFat,
                    protein = point.proteinPct,
                    bone = point.boneMassKg,
                    bmr = point.metabolismKcal,
                    bmrEstimate = bmrEstimate,
                    heightM = heightM,
                    male = male
                )
                val age = deurenbergAge(point.bmi, point.bodyFatPct, male)
                builder.appendLine(
                    "- $label 身体成分（${dayOf(point)}）：" +
                        (parts.joinToString("，").ifEmpty { "没有身体成分" } +
                            (score?.let { "，身体得分 $it" } ?: "") +
                            (age?.let { "，身体年龄 $it 岁" } ?: ""))
                )
            }

            val scale = data.weights.filter { it.weightType == 0 }
            val watch = data.weights.filter { it.weightType != 0 }
            describeWeight("体脂秤", scale.maxByOrNull { it.epochMs })
            describeComposition("体脂秤", scale.filter { it.hasComposition }.maxByOrNull { it.epochMs })
            describeWeight("穿戴设备", watch.maxByOrNull { it.epochMs })
            describeComposition("穿戴设备", watch.filter { it.hasComposition }.maxByOrNull { it.epochMs })
        }

        // Diet log. The numbers are the user's own entries (AI estimates they
        // can edit), so they are labelled as estimates rather than as measured.
        val dietDays = DietStore.meals
            .groupBy { it.date() }
            .toList()
            .sortedBy { it.first }
            .takeLast(days)
        if (dietDays.isNotEmpty()) {
            builder.appendLine("\n饮食（本机记录，热量为识别估算，用户可改）：")
            dietDays.forEach { (date, meals) ->
                val intake = meals.sumOf { it.calories }
                val target = DietStore.calorieTarget(data, date, goal)
                builder.appendLine(
                    "- $date: " + buildList {
                        add("摄入 $intake kcal")
                        target?.let {
                            add("目标 $it kcal")
                            add("剩余 ${it - intake} kcal")
                        }
                        meals.mapNotNull { it.proteinG }.takeIf { it.isNotEmpty() }?.let {
                            add("蛋白 ${"%.0f".format(it.sum())} g")
                        }
                        meals.mapNotNull { it.carbsG }.takeIf { it.isNotEmpty() }?.let {
                            add("碳水 ${"%.0f".format(it.sum())} g")
                        }
                        meals.mapNotNull { it.fatG }.takeIf { it.isNotEmpty() }?.let {
                            add("脂肪 ${"%.0f".format(it.sum())} g")
                        }
                    }.joinToString("，")
                )
                meals.sortedBy { it.epochMs }.forEach { meal ->
                    val time = Instant.ofEpochMilli(meal.epochMs)
                        .atZone(ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    val items = meal.items.joinToString("、") { item ->
                        buildString {
                            append(item.name)
                            item.amount?.let { append(" $it") }
                            item.kcal?.let { append(" ${it} kcal") }
                        }
                    }
                    builder.appendLine("  $time ${meal.calories} kcal" + if (items.isEmpty()) "" else "：$items")
                }
            }
        }

        data.latestWeight()?.let { w ->
            val day = Instant.ofEpochMilli(w.epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
            builder.appendLine(
                "\n体重（$day）：${w.weightKg} kg" +
                    (w.bmi?.let { "，BMI ${"%.1f".format(it)}" } ?: "")
            )
        }
        data.weights.filter { it.hasComposition }.maxByOrNull { it.epochMs }?.let { c ->
            val day = Instant.ofEpochMilli(c.epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
            builder.appendLine(
                "身体成分（$day，与上面的体重可能不是同一天）：" + listOfNotNull(
                    c.bodyFatPct?.let { "体脂 ${"%.1f".format(it)}%" },
                    c.muscleKg?.let { "肌肉 ${"%.1f".format(it)} kg" },
                    c.bodyWaterPct?.let { "水分 ${"%.1f".format(it)}%" },
                    c.boneMassKg?.let { "骨量 ${"%.2f".format(it)} kg" },
                    c.visceralFat?.let { "内脏脂肪 ${it.toInt()}" },
                    c.metabolismKcal?.let { "基础代谢 ${it.toInt()} kcal" },
                    c.proteinPct?.let { "蛋白质 ${"%.1f".format(it)}%" }
                ).joinToString("，")
            )
        }
        if (data.spo2.isNotEmpty()) {
            val recent = data.spo2.takeLast(20).map { it.value }
            builder.appendLine("近期血氧：平均 ${recent.average().toInt()}%，最低 ${recent.min()}%")
        }
        today?.let { builder.appendLine("\n数据截止：$it") }
        builder.appendLine("注意：没有出现的指标就是没有数据，不要推测具体数值。")
        return builder.toString()
    }
}


