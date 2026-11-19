package com.genkaim.zeppglass.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.genkaim.zeppglass.ui.tabs.AppTab

/**
 * Small persisted settings surface for the shell.
 *
 * Everything here is user-owned configuration: which tabs are on the bar, the
 * AI endpoint, and the Zepp app token captured from the login WebView. The
 * token is kept in the app-private preferences file, which is readable only by
 * this app on a non-rooted device.
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("zepp_glass", Context.MODE_PRIVATE)

    var tabOrder: List<AppTab> by mutableStateOf(loadTabOrder())
        private set

    var hiddenTabs: Set<AppTab> by mutableStateOf(loadHidden())
        private set

    var token: String? by mutableStateOf(prefs.getString(KEY_TOKEN, null))
        private set

    var userId: String? by mutableStateOf(prefs.getString(KEY_USER_ID, null))
        private set

    var regionHost: String by mutableStateOf(prefs.getString(KEY_REGION_HOST, DEFAULT_HOST) ?: DEFAULT_HOST)
        private set

    var nickname: String? by mutableStateOf(prefs.getString(KEY_NICKNAME, null))
        private set

    var avatarUrl: String? by mutableStateOf(prefs.getString(KEY_AVATAR, null))
        private set

    // Left empty on a fresh install: there is no endpoint that is right for
    // everybody, so the field starts blank and the placeholder shows the shape
    // of a URL instead of pretending one is already configured.
    var aiBaseUrl: String by mutableStateOf(prefs.getString(KEY_AI_BASE, "") ?: "")
        private set

    var aiApiKey: String by mutableStateOf(prefs.getString(KEY_AI_KEY, "") ?: "")
        private set

    var aiModel: String by mutableStateOf(prefs.getString(KEY_AI_MODEL, "deepseek-v4.1-flash") ?: "deepseek-v4.1-flash")
        private set

    // The prompt is built in and not editable, so always take the current build's
    // text. Reading a saved copy would freeze the wording from the first install
    // and a prompt fix would never reach the phone.
    var aiSystemPrompt: String by mutableStateOf(SYSTEM_PROMPT)
        private set

    var aiHeaders: String by mutableStateOf(prefs.getString(KEY_AI_HEADERS, "") ?: "")

    /** Optional; left empty, photos go to the chat model. */
    var aiVisionModel: String by mutableStateOf(prefs.getString(KEY_AI_VISION, "") ?: "")
        private set

    /** system | light | dark */
    var themeMode: String by mutableStateOf(prefs.getString(KEY_THEME, "system") ?: "system")
        private set

    /** Stable per-install id for gateways that route by session header. */
    val aiSessionId: String by lazy {
        prefs.getString(KEY_AI_SESSION, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_AI_SESSION, it).apply()
        }
    }

    var aiIncludeHealth: Boolean by mutableStateOf(prefs.getBoolean(KEY_AI_CONTEXT, true))
        private set

    /**
     * Today's AI summary: text, the logical day it belongs to (05:00 boundary).
     * The summary is a reading of one person's data, so it is stored per
     * account and re-read whenever the account changes.
     */
    var dailySummaryText by mutableStateOf(prefs.getString(userKey(KEY_SUMMARY_TEXT), "") ?: "")
        private set

    var dailySummaryDay by mutableStateOf(prefs.getString(userKey(KEY_SUMMARY_DAY), "") ?: "")
        private set

    var dailySummaryAt by mutableStateOf(prefs.getLong(userKey(KEY_SUMMARY_AT), 0L))
        private set

    fun saveDailySummary(text: String, day: String, at: Long) {
        dailySummaryText = text
        dailySummaryDay = day
        dailySummaryAt = at
        prefs.edit()
            .putString(userKey(KEY_SUMMARY_TEXT), text)
            .putString(userKey(KEY_SUMMARY_DAY), day)
            .putLong(userKey(KEY_SUMMARY_AT), at)
            .apply()
    }

    val isLoggedIn: Boolean get() = !token.isNullOrBlank() && !userId.isNullOrBlank()

    fun visibleTabs(): List<AppTab> = tabOrder.filter { it !in hiddenTabs }.ifEmpty { listOf(AppTab.OVERVIEW) }

    fun updateTabOrder(order: List<AppTab>) {
        val complete = order.filter { it in AppTab.entries }.distinct() +
            AppTab.entries.filter { it !in order }
        tabOrder = complete
        prefs.edit().putString(KEY_TAB_ORDER, complete.joinToString(",") { it.id }).apply()
    }

    fun updateHidden(hidden: Set<AppTab>) {
        // Never let the user hide the last tab: the bar would have no way back.
        val all = AppTab.entries.toSet()
        if (hidden.containsAll(all)) return
        hiddenTabs = hidden
        prefs.edit().putString(KEY_HIDDEN, hidden.joinToString(",") { it.id }).apply()
    }

    fun saveSession(token: String, userId: String, regionHost: String, nickname: String?, avatar: String?) {
        this.token = token
        this.userId = userId
        this.regionHost = regionHost
        this.nickname = nickname
        this.avatarUrl = avatar
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_REGION_HOST, regionHost)
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_AVATAR, avatar)
            .apply()
        reloadForUser()
    }

    fun updateProfile(nickname: String?, avatar: String?) {
        this.nickname = nickname
        this.avatarUrl = avatar
        prefs.edit().putString(KEY_NICKNAME, nickname).putString(KEY_AVATAR, avatar).apply()
    }

    fun clearSession() {
        token = null
        userId = null
        nickname = null
        avatarUrl = null
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR)
            .apply()
        reloadForUser()
    }

    /**
     * Keys that belong to one account, not to the install: today's AI summary
     * is a reading of that person's health data.
     */
    private fun userKey(base: String): String =
        "$base-${userId?.takeIf { it.isNotBlank() } ?: AccountScope.GUEST}"

    /**
     * Re-reads the per-account pieces after a sign-in or a sign-out. Nothing is
     * cleared: the previous account's summary stays saved under its own key.
     */
    fun reloadForUser() {
        dailySummaryText = prefs.getString(userKey(KEY_SUMMARY_TEXT), "") ?: ""
        dailySummaryDay = prefs.getString(userKey(KEY_SUMMARY_DAY), "") ?: ""
        dailySummaryAt = prefs.getLong(userKey(KEY_SUMMARY_AT), 0L)
    }

    fun updateTheme(mode: String) {
        if (mode !in setOf("system", "light", "dark")) return
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode).apply()
    }

    fun saveAi(
        baseUrl: String,
        apiKey: String,
        model: String,
        visionModel: String = aiVisionModel,
        headers: String = aiHeaders
    ) {
        aiBaseUrl = baseUrl.trim().trimEnd('/')
        aiApiKey = apiKey.trim()
        aiModel = model.trim()
        aiVisionModel = visionModel.trim()
        aiSystemPrompt = SYSTEM_PROMPT
        aiIncludeHealth = true
        aiHeaders = headers.trim()
        prefs.edit()
            .putString(KEY_AI_BASE, aiBaseUrl)
            .putString(KEY_AI_KEY, aiApiKey)
            .putString(KEY_AI_MODEL, aiModel)
            .putString(KEY_AI_PROMPT, SYSTEM_PROMPT)
            .putBoolean(KEY_AI_CONTEXT, true)
            .putString(KEY_AI_HEADERS, aiHeaders)
            .putString(KEY_AI_VISION, aiVisionModel)
            .apply()
    }

    private fun loadTabOrder(): List<AppTab> {
        val raw = prefs.getString(KEY_TAB_ORDER, null) ?: return AppTab.entries.toList()
        // The sleep tab became the activity tab; keep its saved position.
        val parsed = raw.split(',').mapNotNull { id ->
            val key = id.trim()
            AppTab.fromId(key) ?: if (key == "sleep") AppTab.ACTIVITY else null
        }
        return (parsed.distinct() + AppTab.entries.filter { it !in parsed }).distinct()
    }

    private fun loadHidden(): Set<AppTab> {
        val raw = prefs.getString(KEY_HIDDEN, null) ?: return emptySet()
        return raw.split(',').mapNotNull { AppTab.fromId(it.trim()) }.toSet()
    }

    companion object {
        const val DEFAULT_HOST = "https://api-mifit.huami.com"

        const val SYSTEM_PROMPT =
            "你是一位懂训练负荷与恢复的运动数据分析助手。只根据用户提供的健康数据回答，" +
                "不要编造缺失的数值；没有数据的项目直接说不清楚。回答简短、给出依据，不做医疗诊断。" +
                // Long tables of numbers are noise: name what is decisive or off.
                "只引用关键或异常的数字（明显偏离平常的睡眠、静息心率、HRV、负荷、压力、Charge），" +
                "正常范围内的指标用一句概括，不要把整周每天的数据逐条罗列。\n\n" +
                "输出使用 Markdown：可以用小标题、无序列表、**加粗** 和 `代码`。\n" +
                "当你要集中展示一组指标时，在正文之后追加一个 card 代码块（合法 JSON，不要省略字段）：\n" +
                "```card\n" +
                "{\"title\":\"今日恢复\",\"value\":\"94\",\"unit\":\"分\"," +
                "\"items\":[{\"label\":\"静息心率\",\"value\":\"51 bpm\"}," +
                "{\"label\":\"睡眠 HRV\",\"value\":\"122 ms\"}]," +
                "\"caption\":\"来自 11 月 17 日\"}\n" +
                "```\n" +
                "card 只用于数据，不要放主观评价；一段回答最多两个。\n" +
                "缺失值保持缺失，永远不要把没有采样写成 0。" +
                "睡眠时长用「X 小时 Y 分」表达，不要只写分钟。" +
                // The app's own numbers have definitions the model cannot guess.
                "负荷是本机计算的 0–21 分：按一天的心率、步数与脑力负荷合成，" +
                "再按 HRV 与静息心率对基线的偏离调整，不是手表给出的分数。" +
                "饮食记录是本机估算（拍照识别，用户可改），讨论摄入和缺口时说明是估算值。"

        /** Built-in only: the prompt is not user-editable by design. */
        const val DEFAULT_AI_PROMPT = SYSTEM_PROMPT

        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "auth_user_id"
        private const val KEY_REGION_HOST = "auth_region_host"
        private const val KEY_NICKNAME = "profile_nickname"
        private const val KEY_AVATAR = "profile_avatar"
        private const val KEY_TAB_ORDER = "tabs_order"
        private const val KEY_HIDDEN = "tabs_hidden"
        private const val KEY_AI_BASE = "ai_base"
        private const val KEY_AI_KEY = "ai_key"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_PROMPT = "ai_prompt"
        private const val KEY_AI_CONTEXT = "ai_context"
        private const val KEY_AI_HEADERS = "ai_headers"
        private const val KEY_AI_VISION = "ai_vision_model"
        private const val KEY_AI_SESSION = "ai_session"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SUMMARY_TEXT = "daily_summary_text"
        private const val KEY_SUMMARY_DAY = "daily_summary_day"
        private const val KEY_SUMMARY_AT = "daily_summary_at"

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}



