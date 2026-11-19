package com.genkaim.zeppglass.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * The one-paragraph AI summary under the home rings.
 *
 * A "day" starts at 05:00 local time, so a summary generated at 01:00 belongs
 * to the previous day (that is when the night is still being scored). On app
 * open the summary is generated only when today's text is missing; the refresh
 * button on the card always regenerates.
 */
object DailySummary {

    private const val DAY_START_HOUR = 5L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var attachedTo: AppSettings? = null

    var text by mutableStateOf("")
        private set

    /** Logical day the current text belongs to, as `yyyy-MM-dd`. */
    var day by mutableStateOf("")
        private set

    var generatedAt by mutableStateOf(0L)
        private set

    var generating by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Reads the stored summary once; the settings object outlives the UI. */
    fun attach(settings: AppSettings) {
        if (attachedTo === settings) return
        attachedTo = settings
        // A summary saved by a build that turned JSON null into "null" is
        // nothing but that run; treat it as not generated so the card rebuilds
        // it instead of showing the junk again.
        val stored = settings.dailySummaryText.withoutNullChunks()
        text = stored
        day = if (stored.isBlank()) "" else settings.dailySummaryDay
        generatedAt = if (stored.isBlank()) 0L else settings.dailySummaryAt
    }

    /** A day rolls over at 05:00, not at midnight. */
    fun dayKey(now: LocalDateTime = LocalDateTime.now()): String =
        now.minusHours(DAY_START_HOUR).toLocalDate().toString()

    fun hasToday(): Boolean = text.isNotBlank() && day == dayKey()

    /** Auto path: generates once per logical day, otherwise does nothing. */
    fun ensureToday(settings: AppSettings, data: HealthData?) {
        attach(settings)
        if (data == null) return
        if (hasToday() || generating) return
        generate(settings, data)
    }

    /** Card refresh: always asks again, replacing the current text. */
    fun regenerate(settings: AppSettings, data: HealthData?) {
        attach(settings)
        if (data == null) {
            error = "还没有健康数据，先同步一次"
            return
        }
        job?.cancel()
        generating = false
        generate(settings, data)
    }

    private fun generate(settings: AppSettings, data: HealthData) {
        if (settings.aiApiKey.isBlank()) {
            error = "先在设置里填入 AI 密钥"
            return
        }
        if (generating) return
        generating = true
        error = null
        text = ""

        val key = dayKey()
        val client = AiClient(settings)
        val messages = listOf(
            ChatMessage("system", SYSTEM_PROMPT),
            ChatMessage(
                "user",
                AiContext.digest(data, 7, settings.dietGoalOrCut) + "\n\n请基于上面的数据写今天的摘要。"
            )
        )

        job = scope.launch {
            try {
                // Streaming keeps the card alive while the model thinks: the
                // first tokens normally land within a second or two.
                client.stream(messages).collect { chunk ->
                    text += chunk
                }
                val cleaned = text.withoutNullChunks().trim().trim('"').trim()
                if (cleaned.isBlank()) throw IllegalStateException("模型没有返回内容")
                text = cleaned
                day = key
                generatedAt = System.currentTimeMillis()
                settings.saveDailySummary(cleaned, key, generatedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                text = ""
                val reason = e.message?.take(140) ?: "网络错误"
                error = "摘要生成失败：$reason"
            } finally {
                generating = false
                job = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        generating = false
    }

    private const val SYSTEM_PROMPT =
        "你是用户手机里的运动与恢复教练。根据给出的本机健康数据，用 3-4 句中文写今天的摘要：" +
            "先说恢复（睡眠/准备度/静息心率），再说负荷与压力；" +
            "然后给两条建议：今天怎么练，以及今天工作/学习该用什么强度（能不能冲刺、要不要提前收工）；" +
            "最后和前几天比一比：准备度、睡眠分或恢复状态提高就明确鼓励一句，下降就提醒并点出可能的原因。" +
            // A wall of numbers is unreadable on a phone card: mention only what
            // is decisive or clearly off, and let the rest stay in the data pages.
            "只挑关键或异常的数字说：明显偏少或偏多的睡眠、偏高或偏低的准备度/静息心率、突增的负荷、" +
            "压力或 Charge 的明显下滑。正常范围内的指标不要逐个报数，也不要罗列整周的每日数据；" +
            "全在正常范围时就用一句话概括「各项都在正常范围」。" +
            "睡眠时长一律写成「X 小时 Y 分」，不要只报分钟。" +
            "只输出纯文本：不要 Markdown、不要标题、不要列表、不要表情、不要编造没有给出的数字。" +
            "没有数据的项目直接跳过。总长度不超过 120 个字。"
}
