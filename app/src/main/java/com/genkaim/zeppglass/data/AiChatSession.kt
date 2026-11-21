package com.genkaim.zeppglass.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Conversation state and the data agent loop, owned by the app rather than the
 * AI tab so switching pages or turning the screen off does not cancel an answer.
 *
 * Every conversation is archived to a JSON file in the app's private directory:
 * the chat list lives behind the history button in the AI header, and nothing
 * is dropped when a new chat starts.
 */
object AiChatSession {

    data class Message(
        val role: String,
        val content: String,
        val error: Boolean = false
    )

    /** Header line of the history sheet; messages stay in [messages]. */
    data class Session(
        val id: String,
        val title: String,
        val preview: String,
        val updatedAt: Long,
        val count: Int
    )

    private data class Stored(
        val id: String,
        val updatedAt: Long,
        val messages: List<Message>
    )

    val messages = mutableStateListOf<Message>()
    val sessions = mutableStateListOf<Session>()
    val suggestions = mutableStateListOf<String>()

    var currentId by mutableStateOf<String?>(null)
        private set

    /**
     * Bumped whenever the whole transcript is swapped for another one (a
     * history entry, a new chat, another account). The page uses it to tell
     * "this conversation opened" from "a message just arrived", so only the
     * latter is animated.
     */
    var sessionEpoch by mutableStateOf(0)
        private set

    /**
     * The bulky system prompt (role, tool catalogue, seven-day digest) goes out
     * once per conversation. Repeating it on every turn drowned short questions
     * in data and made the model ignore the instructions.
     */
    private var contextSent = false

    /** Draft text survives tab switches, like the conversation itself. */
    var draft by mutableStateOf("")

    var sending by mutableStateOf(false)
        private set
    var suggestionsLoading by mutableStateOf(false)
        private set

    /** Short status shown in the answer bubble while waiting for the first token. */
    var phase by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var suggestionsJob: Job? = null

    /** Logical day the questions in [suggestions] were written for. */
    private var suggestionsDay = ""

    private var appContext: Context? = null
    private var loaded = false

    /** Newest first; the archive is capped so the file stays small. */
    private val stored = LinkedHashMap<String, Stored>()
    private var writeJob: Job? = null

    /** Fallback starter questions, also used while the model has not answered yet. */
    val defaultPrompts get() = listOf(
        "今天适合练什么？",
        "解释一下我的准备度",
        "睡眠哪里可以改善？",
        "这周负荷是不是偏高？"
    )

    private const val QUERY_PROTOCOL = """
你可以查询本机数据，但不要要求用户粘贴全部数据。需要数据时，回复里只输出一个 query 代码块：
```query
{"table":"sleep","from":"2026-11-10","to":"2026-11-17"}
```
table 只能取 daily / sleep / charge / readiness / stress / pai / load / weight / spo2 / respiration / breathing / workouts / strain 之一；也可以只写 {"table":"sleep","days":7}。一次最多一个查询，最多查 1 次；已有摘要里有的数据不要重复查。
最终回答使用 Markdown。需要展示图形化卡片时，在正文后追加 card 代码块，type 可以是 metric / sleep / charge / activity：
```card
{"type":"sleep","date":"2026-11-16"}
```
```card
{"type":"metric","title":"本周恢复","value":"94","unit":"分","items":[{"label":"静息心率","value":"51 bpm"}]}
```
卡片由 App 用本机数据渲染，你只需要给出 type 和日期（date 可用 today 或 YYYY-MM-DD）。
"""

    // ---------------- archive ----------------

    fun attach(context: Context) {
        if (loaded) return
        loaded = true
        appContext = context.applicationContext
        // The digest reads the diet log, so make sure it is in memory even when
        // the AI page is opened before the home screen ever was.
        DietStore.attach(context.applicationContext)
        load()
    }

    /** One archive per account, so two people never see each other's chats. */
    private fun archiveFile(): File? =
        appContext?.let { AccountScope.file(it, "ai-chats", AppSettings.get(it)) }

    private fun load() {
        val file = archiveFile() ?: return
        if (!file.isFile) return
        runCatching {
            val json = JSONObject(file.readText())
            val array = json.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val list = mutableListOf<Message>()
                val items = item.optJSONArray("messages") ?: continue
                for (j in 0 until items.length()) {
                    val message = items.optJSONObject(j) ?: continue
                    val role = message.optString("role")
                    if (role != "user" && role != "assistant") continue
                    // Builds before 0.2.10 saved the literal string "null" for
                    // every chunk that carried only reasoning, leaving bubbles
                    // of nullnullnull in the history. Drop those on load.
                    val body = message.text("content").withoutNullChunks().trim()
                    if (body.isBlank()) continue
                    list.add(
                        Message(
                            role = role,
                            content = body,
                            error = message.optBoolean("error", false)
                        )
                    )
                }
                if (list.isEmpty()) continue
                stored[id] = Stored(id, item.optLong("updatedAt"), list)
            }
            // A fresh launch always starts a new conversation; the archive keeps
            // every earlier one behind the history button.
            currentId = null
            messages.clear()
            trimArchive()
            rebuildList()
        }
    }

    /** Writes the whole archive; small enough to do off the main thread. */
    private fun write() {
        val file = archiveFile() ?: return
        val current = currentId
        val payload = JSONObject().apply {
            put("current", current ?: "")
            put("sessions", JSONArray().apply {
                orderedSessions().forEach { session ->
                    put(
                        JSONObject().apply {
                            put("id", session.id)
                            put("updatedAt", session.updatedAt)
                            put("messages", JSONArray().apply {
                                session.messages.forEach { message ->
                                    put(
                                        JSONObject().apply {
                                            put("role", message.role)
                                            put("content", message.content)
                                            if (message.error) put("error", true)
                                        }
                                    )
                                }
                            })
                        }
                    )
                }
            })
        }
        val text = payload.toString()
        writeJob?.cancel()
        writeJob = scope.launch {
            withContext(Dispatchers.IO) { runCatching { file.writeText(text) } }
        }
    }

    private fun orderedSessions(): List<Stored> =
        stored.values.sortedByDescending { it.updatedAt }

    private fun trimArchive() {
        val list = orderedSessions()
        if (list.size <= MAX_SESSIONS) return
        list.drop(MAX_SESSIONS).forEach { stored.remove(it.id) }
    }

    private fun rebuildList() {
        val list = orderedSessions().map { session ->
            val firstUser = session.messages.firstOrNull { it.role == "user" }?.content.orEmpty()
            val last = session.messages.lastOrNull()?.content.orEmpty()
            Session(
                id = session.id,
                title = firstUser.lineSequence().firstOrNull().orEmpty().take(28).ifBlank { "未命名对话" },
                preview = last.replace('\n', ' ').take(60),
                updatedAt = session.updatedAt,
                count = session.messages.size
            )
        }
        sessions.clear()
        sessions.addAll(list)
    }

    /** Copies the live conversation into the archive and rewrites the file. */
    private fun snapshot() {
        val id = currentId
        if (id != null) {
            if (messages.isEmpty()) {
                stored.remove(id)
            } else {
                stored[id] = Stored(id, System.currentTimeMillis(), messages.toList())
            }
        }
        rebuildList()
        write()
    }

    private fun ensureCurrent() {
        if (currentId == null) currentId = UUID.randomUUID().toString()
    }

    // ---------------- conversation actions ----------------

    fun newChat() {
        stop()
        snapshot()
        sessionEpoch++
        contextSent = false
        currentId = null
        messages.clear()
        draft = ""
        // Starter questions are a read of today's data, not of this
        // conversation: a new chat keeps them, and the strip's refresh button
        // is the one way to ask for a different set.
        rebuildList()
        write()
    }

    fun open(id: String) {
        if (id == currentId) return
        stop()
        snapshot()
        val session = stored[id] ?: return
        sessionEpoch++
        contextSent = false
        messages.clear()
        messages.addAll(session.messages)
        currentId = id
        write()
    }

    fun delete(id: String) {
        stop()
        if (id == currentId) contextSent = false
        stored.remove(id)
        if (id == currentId) {
            currentId = null
            messages.clear()
        }
        rebuildList()
        write()
    }

    /** Clears the open conversation without touching the others. */
    fun clear() {
        stop()
        val id = currentId
        messages.clear()
        if (id != null) stored.remove(id)
        currentId = null
        rebuildList()
        write()
    }

    /**
     * The account changed: the archive in memory belongs to the previous
     * person, so everything is dropped and the file of the account that is
     * open now is read instead. Nothing on disk is touched.
     */
    fun switchUser(context: Context) {
        sessionEpoch++
        resetInMemory()
        appContext = context.applicationContext
        loaded = true
        load()
    }

    private fun resetInMemory() {
        stop()
        job?.cancel()
        job = null
        suggestionsJob?.cancel()
        suggestionsJob = null
        writeJob?.cancel()
        writeJob = null
        sending = false
        suggestionsLoading = false
        phase = ""
        contextSent = false
        currentId = null
        messages.clear()
        sessions.clear()
        suggestions.clear()
        suggestionsDay = ""
        stored.clear()
        draft = ""
    }

    /**
     * Signing out: the whole archive is user data, so the live lists and the
     * file on disk both go. Nothing here is reachable without the account.
     */
    fun clearAll() {
        resetInMemory()
        runCatching { archiveFile()?.delete() }
    }

    // ---------------- data agent ----------------

    fun send(settings: AppSettings, prompt: String, data: HealthData?) {
        val text = prompt.trim()
        if (text.isEmpty() || sending) return
        if (settings.aiApiKey.isBlank()) return

        ensureCurrent()
        messages.add(Message("user", text))
        draft = ""
        snapshot()
        request(settings, data)
    }

    /** Sends the last question again after a failed answer. */
    fun retry(settings: AppSettings, data: HealthData?) {
        if (sending) return
        if (messages.none { it.role == "user" }) return
        // Drop the failed (or empty) answer, keep the question.
        while (messages.isNotEmpty()) {
            val last = messages.last()
            if (last.role == "assistant" && (last.error || last.content.isBlank())) {
                messages.removeAt(messages.lastIndex)
            } else break
        }
        ensureCurrent()
        snapshot()
        request(settings, data)
    }

    /**
     * "重新生成": asks the same question again and replaces the answer that is
     * already on screen, the way every chat client does it. The question stays
     * where it is, so the list does not jump.
     */
    fun regenerate(settings: AppSettings, data: HealthData?) {
        if (sending) return
        if (messages.none { it.role == "user" }) return
        while (messages.isNotEmpty() && messages.last().role == "assistant") {
            messages.removeAt(messages.lastIndex)
        }
        if (messages.isEmpty()) return
        ensureCurrent()
        snapshot()
        request(settings, data)
    }

    private fun request(settings: AppSettings, data: HealthData?) {
        val assistantIndex = messages.size
        messages.add(Message("assistant", ""))
        sending = true

        val history = messages
            .filterIndexed { index, message ->
                index != assistantIndex && (message.role == "user" || message.content.isNotBlank())
            }
            .takeLast(12)

        val client = AiClient(settings)
        job = scope.launch {
            try {
                // Hand over the whole digest instead of making the model fetch
                // every table itself. The agent round used to stop after the
                // first couple of tables, so answers claimed that steps, sleep
                // and other rows were unavailable.
                val digest = AiContext.digest(data, goal = settings.dietGoalOrCut)
                // First turn of the conversation carries the full briefing; later
                // turns only carry the role prompt, so a short question is not
                // buried under a wall of data.
                val firstTurn = !contextSent
                val system = ChatMessage(
                    "system",
                    if (firstTurn) {
                        AppSettings.SYSTEM_PROMPT + "\n\n" + AiTools.catalog(data) +
                            "\n\n以下是已经读取好的本机数据摘要，直接当作已知事实使用：" +
                            "\n" + digest + "\n" + QUERY_PROTOCOL
                    } else {
                        AppSettings.SYSTEM_PROMPT +
                            "\n\n本会话开头已经提供过本机数据摘要，直接沿用前文出现过的数值；" +
                            "需要更早或更细的行时再查一次。" +
                            "\n" + QUERY_PROTOCOL
                    }
                )
                contextSent = true
                val conversation = mutableListOf(system)
                history.forEach { conversation.add(ChatMessage(it.role, it.content)) }
                val question = history.lastOrNull { it.role == "user" }?.content.orEmpty()
                val smallTalk = isAcknowledgement(question)

                // The digest above already holds the last seven days, so a
                // planning round trip used to add a second of dead air to every
                // single question. It now runs only when the question points at
                // a period the digest cannot cover.
                if (asksForOlderData(question)) {
                    phase = "正在读取本机数据…"
                    val plan = withContext(Dispatchers.IO) {
                        client.complete(
                            listOf(
                                ChatMessage(
                                    "system",
                                    "你是数据查询规划器。已有摘要通常足够；只有需要更早或更细的行时，才输出一个 query 代码块。判断为不需要就只输出 READY。不要写最终回答。\n" +
                                        QUERY_PROTOCOL
                                ),
                                ChatMessage(
                                    "user",
                                    "用户问题：$question\n\n" + AiTools.catalog(data) +
                                        "\n\n已有摘要：\n" + digest
                                )
                            ),
                            maxTokens = 160
                        )
                    }
                    extractQuery(plan)?.let { query ->
                        val result = withContext(Dispatchers.IO) { AiTools.execute(query, data, settings.dietGoalOrCut) }
                        conversation.add(ChatMessage("assistant", "（查询本机数据：$query）"))
                        conversation.add(ChatMessage("user", "查询结果：\n$result"))
                    }
                }

                phase = "深度思考中…"
                conversation.add(
                    ChatMessage(
                        "user",
                        if (smallTalk) {
                            // An acknowledgement is not a new analysis request.
                            // The instruction below used to be appended to every
                            // turn, so "好的" was answered with the whole report
                            // again, card included.
                            "用户这句话只是应答或寒暄，不是新的数据分析请求。用一两句话自然回应即可：" +
                                "不要重新分析数据、不要重复已经给过的结论、不要再罗列数字、不要输出 card 块。" +
                                "如果是在确认刚才的建议，就简短确认；只有用户提出新问题才展开。"
                        } else {
                            "请基于上面的数据摘要和查询结果，直接给出最终回答，使用 Markdown。" +
                                "问题涉及的指标都要核对，但正文只写关键或异常的那几个数值，其余用一句概括；" +
                                "不要把整周每天的指标逐条罗列，也不要把同一组数字在正文和 card 里重复一遍；全在正常范围就直接说正常。" +
                                "只有摘要和查询里确实没有的指标才说没有，不要笼统地说看不到数据。" +
                                "需要图形卡片时用 card 块。"
                        }
                    )
                )
                // Always stream the final answer. The old plan turn was capped
                // at 500 tokens and its text became the answer, which is why
                // long replies stopped mid-sentence.
                var round = 0
                while (round < 2) {
                    client.stream(conversation).collect { chunk ->
                        phase = ""
                        val current = messages.getOrNull(assistantIndex) ?: return@collect
                        messages[assistantIndex] = current.copy(content = current.content + chunk)
                    }
                    // Safety net: the model may answer the protocol with nothing
                    // but a query block. Run it once and let it write the real
                    // answer instead of leaving JSON code in the bubble.
                    val pending = onlyQuery(messages.getOrNull(assistantIndex)?.content.orEmpty())
                    if (pending == null || round > 0) break
                    phase = "正在读取本机数据…"
                    val result = withContext(Dispatchers.IO) { AiTools.execute(pending, data, settings.dietGoalOrCut) }
                    conversation.add(ChatMessage("assistant", "（查询本机数据：$pending）"))
                    conversation.add(ChatMessage("user", "查询结果：\n$result\n请据此给出最终回答。"))
                    messages[assistantIndex] = Message("assistant", "")
                    round++
                }
                if (messages.getOrNull(assistantIndex)?.content.isNullOrBlank()) {
                    messages[assistantIndex] = Message("assistant", "（没有收到内容）")
                }
                phase = ""
            } catch (e: Exception) {
                val reason = e.message ?: "请求失败"
                phase = ""
                messages.getOrNull(assistantIndex)?.let {
                    if (it.content.isBlank()) {
                        messages[assistantIndex] = Message("assistant", "请求失败：$reason", error = true)
                    }
                } ?: messages.add(Message("assistant", "请求失败：$reason", error = true))
            } finally {
                sending = false
                job = null
                phase = ""
                snapshot()
            }
        }
    }

    /**
     * Starter questions written by the model from the digest.
     *
     * They belong to the logical day (05:00 boundary), not to the conversation:
     * the same set stays on screen all day and survives a restart, so opening
     * the tab with unchanged data costs no request. [force] is the refresh
     * button on the strip and always asks the model again.
     */
    fun loadSuggestions(settings: AppSettings, data: HealthData?, force: Boolean = false) {
        if (force) {
            suggestionsJob?.cancel()
            suggestionsJob = null
            suggestionsLoading = false
        } else {
            if (suggestionsLoading) return
            val today = DailySummary.dayKey()
            // Already holding today's set: nothing to do. A set from before the
            // 05:00 rollover is dropped, so an app left open overnight does not
            // keep showing yesterday's questions.
            if (suggestions.isNotEmpty() && suggestionsDay == today) return
            // Written earlier today by a build that already asked: reuse it
            // instead of spending a call on the same digest.
            val saved = settings.savedSuggestions()
            if (saved.size >= 2 && settings.aiSuggestionsDay == today) {
                suggestions.clear()
                suggestions.addAll(saved)
                suggestionsDay = today
                return
            }
        }
        if (suggestionsLoading || settings.aiApiKey.isBlank()) return
        suggestionsLoading = true
        // Pinned before the request: the answer is filed under the day it was
        // asked on, even if midnight or the 05:00 rollover happens meanwhile.
        val day = DailySummary.dayKey()
        suggestionsJob = scope.launch {
            val digest = AiContext.digest(data, goal = settings.dietGoalOrCut)
            // Everything the user has already seen today, plus what the chips
            // are showing right now. "换一批" that hands back the same idea in
            // different words is the one outcome the button must not produce,
            // and the model does it often, so the ban list is enforced locally
            // instead of trusted to the prompt.
            val avoid = LinkedHashSet<String>()
            avoid.addAll(suggestions)
            if (settings.aiSuggestionsDay == day) avoid.addAll(settings.seenSuggestions())

            var parsed = emptyList<String>()
            // Two rounds at most: usually the first answer is clean once the
            // model has seen the ban list; a second pass only happens when it
            // still collided with something.
            for (round in 0..1) {
                val messages = listOf(
                    ChatMessage("system", suggestionSystemPrompt(avoid)),
                    ChatMessage(
                        "user",
                        digest + "\n按上面的要求写出这位用户此刻会想知道的 4 个问题。"
                    )
                )
                // A reasoning model spends part of the budget before writing
                // anything, so a small cap comes back empty; start high and
                // retry once with more room instead of falling straight back
                // to defaults.
                var text = ""
                for (budget in intArrayOf(1024, 2048)) {
                    text = runCatching {
                        withContext(Dispatchers.IO) {
                            AiClient(settings).complete(messages, maxTokens = budget)
                        }
                    }.getOrDefault("")
                    if (text.isNotBlank()) break
                }
                val lines = parseSuggestionLines(text)
                parsed = lines.filter { line -> avoid.none { isSameQuestion(line, it) } }
                if (parsed.size >= 2) break
                // Whatever it offered may not be offered again, so the second
                // round starts from a strictly larger ban list.
                if (round == 0) {
                    avoid.addAll(lines)
                    if (text.isBlank()) break
                }
            }
            // Only a usable set is filed for the day; a failed attempt leaves
            // whatever is on screen alone and is retried on the next open.
            if (parsed.size >= 2) {
                suggestions.clear()
                suggestions.addAll(parsed)
                suggestionsDay = day
                settings.saveSuggestions(parsed, day)
            } else if (suggestions.isEmpty()) {
                suggestions.addAll(
                    defaultPrompts.filter { line -> avoid.none { isSameQuestion(line, it) } }
                        .ifEmpty { defaultPrompts }
                )
            }
            suggestionsLoading = false
            suggestionsJob = null
        }
    }

    /** The question-writing prompt, with today's already-seen chips banned. */
    private fun suggestionSystemPrompt(avoid: Collection<String>): String {
        val base = "你在帮用户想接下来可以问健康助手的问题。只输出 4 行中文问题，每行一个，不带编号、符号或解释，" +
            "每条 8–16 个字，要短，像用户随口说的一句话。" +
            // Chips are a prompt to type, not a report: naming exact numbers
            // made every starter question a paragraph.
            "硬性要求：不要写出任何具体数字、日期或数值，也不要点名某个指标的读数；" +
            "用「最近」「这段时间」「上周」这类模糊说法代替，" +
            "例如写「最近睡眠是不是有点浅」而不是「昨晚只睡 5 小时怎么办」。" +
            "四条分别覆盖睡眠、恢复或准备度、训练负荷、饮食里最值得关注的一两件事，不要四条都问同一个指标。" +
            "不要写“今天怎么样”“需要注意什么”“如何改善健康”这类放在谁身上都成立的空问题，" +
            "也不要做疾病诊断。"
        if (avoid.isEmpty()) return base
        // Twelve is enough to convey the flavour of what has been asked; the
        // whole ledger would bloat every refresh.
        val banned = avoid.toList().takeLast(12).joinToString("\n") { "- $it" }
        return base + "\n以下是今天已经问过、不能再出现的问题。既要换指标，也要换角度，" +
            "不许换个说法重复同一条意思：\n" + banned
    }

    /**
     * Model output to chips: one per line, no bullets, no numbering, no
     * numbers inside the question, no headings.
     */
    private fun parseSuggestionLines(text: String): List<String> = text.lines()
        .map { line ->
            line.trim()
                .trimStart('-', '•', '*', '·')
                .replace(Regex("^\\d+[.、)）]\\s*"), "")
                .trim()
                .trim('"', '\'', '“', '”', '「', '」', '《', '》')
        }
        // A chatty model sometimes answers with a whole document instead of
        // four questions; heading lines are not questions.
        .filter { it.length in 5..20 && !it.startsWith("#") && !it.contains("**") }
        // Chips never quote numbers: the model drifts back to "睡 5 小时" no
        // matter what the prompt says, so those lines go.
        .filter { line -> line.none { it.isDigit() } }
        .distinct()
        .take(4)

    /**
     * Same question in different words. Exact match after stripping punctuation
     * catches most of it; the bigram overlap catches "最近睡眠有点浅" against
     * "最近的睡眠是不是浅了一些".
     */
    internal fun isSameQuestion(a: String, b: String): Boolean {
        val x = questionKey(a)
        val y = questionKey(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        if (x.contains(y) || y.contains(x)) return true
        if (x.length < 4 || y.length < 4) return false
        val left = x.windowed(2).toSet()
        val right = y.windowed(2).toSet()
        val shared = left.count { it in right }
        return shared * 2f / (left.size + right.size) >= 0.62f
    }

    private fun questionKey(s: String): String = s.filter { it.isLetterOrDigit() }

    /**
     * Finds the `{"table":…}` query in a model reply. The parsing lives in
     * [AiQuery] because Android's regex engine rejected the old pattern and
     * because the scan is easier to unit test as plain code.
     */
    private fun extractQuery(text: String): String? = AiQuery.extract(text)

    /** The reply is nothing but a query block — no prose to keep. */
    private fun onlyQuery(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return null
        if (trimmed.lines().size > 8) return null
        return AiQuery.extract(trimmed)
    }

    /**
     * True when the question names a period outside the seven-day digest, which
     * is the only case where a lookup before answering pays for itself.
     */
    /**
     * "好的"、"谢谢"、"嗯" — an acknowledgement, not a new question. It only
     * matches a short message that is nothing but those words: anything with a
     * number, a question mark or real content is a request and gets the full
     * answer path.
     */
    internal fun isAcknowledgement(question: String): Boolean {
        val text = question.trim().trim('。', '，', '！', '!', '~', '～', '.', '，', ' ')
        if (text.isEmpty() || text.length > 6) return false
        if (text.any { it.isDigit() }) return false
        if (text.any { it == '?' || it == '？' || it == '吗' }) return false
        val lower = text.lowercase()
        val words = listOf(
            "好", "行", "可以", "嗯", "哦", "噢", "收到", "谢谢", "多谢",
            "明白", "明白啦", "明白了", "懂了", "知道", "知道了", "了解",
            "ok", "okay", "thanks", "thx", "got it", "好的", "好滴", "好呀",
            "好吧", "就这样", "不用了", "辛苦了"
        )
        return words.any { lower == it || lower == it + "的" || lower == it + "呀" }
    }

    private fun asksForOlderData(question: String): Boolean {
        val keys = listOf(
            "上个月", "上月", "去年", "前年", "更早", "之前", "历史",
            "前几周", "几周前", "几个月", "春季", "夏季", "秋季", "冬季"
        )
        if (keys.any { question.contains(it) }) return true
        return Regex("\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号]").containsMatchIn(question)
    }

    fun stop() {
        job?.cancel()
        job = null
        sending = false
        phase = ""
        // A stop before the first token would leave an empty bubble behind.
        val last = messages.lastOrNull()
        if (last != null && last.role == "assistant" && last.content.isBlank() && !last.error) {
            messages.removeAt(messages.lastIndex)
        }
        snapshot()
    }

    private const val MAX_SESSIONS = 200
}

