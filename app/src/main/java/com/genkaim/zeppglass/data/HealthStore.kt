package com.genkaim.zeppglass.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

data class HealthUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val data: HealthData? = null,
    val error: String? = null,
    val lastSyncedAt: Long? = null
)

/**
 * Fetches the user's health data and keeps a snapshot on disk so the app opens
 * with last night's numbers even without a network.
 */
class HealthStore private constructor(
    context: Context,
    private val settings: AppSettings
) {
    private val appContext = context.applicationContext
    /** One snapshot per account: switching accounts swaps the whole file. */
    private fun cacheFile(): File = AccountScope.file(appContext, "health-snapshot", settings)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val api = ZeppApi(settings)

    /**
     * One refresh at a time. This is a transport-level flag, deliberately not
     * the UI's [HealthUiState.loading]: after a sign-in the state starts out
     * `loading = true` (there is nothing to show yet), and keying the guard on
     * that flag refused the very refresh that would have cleared it — the page
     * sat on its spinner and every pull-to-refresh was dropped on the floor.
     */
    private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Bumped whenever the account changes, so an old request cannot land. */
    private var generation = 0

    var state by mutableStateOf(loadInitialState())
        private set

    /**
     * Read once, synchronously, before the first frame: opening the app shows
     * the last snapshot immediately instead of flashing a "syncing" card. The
     * refresh still runs in the background and only the header spinner moves.
     */
    private fun loadInitialState(): HealthUiState {
        val cached = readSnapshot()
            ?: return HealthUiState(loading = settings.isLoggedIn)
        return HealthUiState(
            loading = settings.isLoggedIn,
            data = cached.data,
            lastSyncedAt = cached.fetchedAt
        )
    }

    /**
     * What screens and the AI read: the cached snapshot as it stands. There is
     * no age limit — an old reading is still the last real reading, and blanking
     * it to `--` only hid data the user already had.
     */
    val displayData: HealthData?
        get() = state.data

    init {
        // No sync from here on purpose. The only automatic pulls are the widget's
        // fifteen-minute alarm and opening the app, both routed through
        // [refreshIfStale]; an unconditional pull on construction made every
        // reopen fire a request even seconds after the last one.
    }

    /**
     * The one entry point for automatic syncs: refreshes only when the snapshot
     * is older than [minAgeMs]. A manual pull calls [refresh] directly, so it is
     * never throttled.
     */
    fun refreshIfStale(minAgeMs: Long = 60_000L) {
        val last = state.lastSyncedAt ?: 0L
        if (System.currentTimeMillis() - last > minAgeMs) refresh()
    }

    /**
     * Starts a full refresh and hands back the job. Callers that draw the result
     * (the widget) wait on it, so the new snapshot is on disk before the card is
     * rebuilt; the UI ignores the return value.
     */
    fun refresh(): Job? {
        // Only a refresh that is actually running may block another one. The UI
        // flags are not used here: `loading` stays true until the first answer
        // lands, which is exactly when the user wants a refresh to go out.
        if (!inFlight.compareAndSet(false, true)) return null
        if (!settings.isLoggedIn) {
            inFlight.set(false)
            state = state.copy(error = null, loading = false, refreshing = false)
            return null
        }
        // Flip the flag now, not inside the coroutine: the pull-to-refresh
        // gesture reads it on the same frame and returns the page at once.
        state = state.copy(refreshing = true)
        // Remember which account this run belongs to. A sign-in during the
        // request bumps [generation] and clears the flag itself, so the old run
        // must not unlock a newer one when it finally finishes.
        val owner = generation
        return scope.launch {
            try {
                doRefreshSafely()
                // A freshly captured token is sometimes not propagated on the
                // first round, and the whole page comes back empty. One silent
                // retry is cheaper than making the reader pull several times.
                val deadToken = state.error?.contains("登录已失效") == true
                if (owner == generation && state.data == null && !deadToken && settings.isLoggedIn) {
                    delay(1200)
                    if (owner == generation && state.data == null && settings.isLoggedIn) {
                        doRefreshSafely()
                    }
                }
            } finally {
                if (owner == generation) inFlight.set(false)
            }
        }
    }

    /**
     * The account changed — a sign-in, a sign-out, or a different person on the
     * same phone. Nothing is deleted: memory is dropped, the new account's
     * snapshot file is read, and the network refresh starts again from there.
     */
    fun switchUser() {
        // Requests that started before the switch must not write their answer
        // into the file of the account that is now open.
        generation++
        // Whatever was in flight belongs to the previous account; let the new
        // one refresh straight away instead of waiting for it to time out.
        inFlight.set(false)
        workoutCache.clear()
        // Reading and parsing the snapshot is a few megabytes of JSON; doing it
        // on the caller's thread is what made signing in feel like a freeze.
        // The screen gets a plain loader, the file is read, then the network.
        state = HealthUiState(loading = settings.isLoggedIn)
        if (!settings.isLoggedIn) return
        scope.launch {
            val cached = withContext(Dispatchers.IO) { readSnapshot() }
            if (cached != null && state.data == null) {
                state = state.copy(
                    data = cached.data,
                    lastSyncedAt = cached.fetchedAt
                )
            }
            refresh()
        }
    }

    fun onLoginChanged() = switchUser()

    /**
     * Workout detail is fetched on demand (the list payload has no GPS), then
     * kept in memory so reopening a workout does not hit the network again.
     */
    private val workoutCache = java.util.concurrent.ConcurrentHashMap<String, WorkoutDetail>()

    suspend fun loadWorkoutDetail(workout: Workout): WorkoutDetail? {
        workoutCache[workout.id]?.let { return it }
        if (!api.isConfigured) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val json = api.workoutDetail(
                    workout.id,
                    workout.source?.takeIf { it.isNotBlank() } ?: DEFAULT_WORKOUT_SOURCE
                )
                HealthParser.parseWorkoutDetail(json)
            }.getOrNull()?.also { workoutCache[workout.id] = it }
        }
    }

    /**
     * Anything that escapes the request coroutines would otherwise leave the
     * screen on its loader forever, so failures fall back to a retryable error.
     */
    private suspend fun doRefreshSafely() {
        try {
            doRefresh()
        } catch (c: kotlinx.coroutines.CancellationException) {
            // The page may have been left or the account switched: not an error.
            throw c
        } catch (t: Throwable) {
            state = state.copy(
                loading = false,
                refreshing = false,
                error = t.friendly()
            )
        }
    }

    private suspend fun doRefresh() {
        val userId = settings.userId
        if (userId == null) {
            // Signed out between the guard and the request: clear the loader
            // instead of leaving the page spinning forever.
            state = state.copy(loading = false, refreshing = false)
            return
        }
        val gen = generation
        val previous = state.data
        state = state.copy(loading = previous == null, refreshing = true, error = null)

        val warnings = mutableListOf<String>()
        var profile: JSONObject? = null
        val band = mutableListOf<JSONObject>()
        var readiness: JSONObject? = null
        var pai: JSONObject? = null
        var stress: JSONObject? = null
        var spo2: JSONObject? = null
        var charge: JSONObject? = null
        var load: JSONObject? = null
        var vo2: JSONObject? = null
        var weights: JSONObject? = null
        var workouts: JSONObject? = null
        var hrv: JSONObject? = null
        var dailyHealth: JSONObject? = null
        var respiration: JSONObject? = null
        var nightBreathing: JSONObject? = null

        val today = LocalDate.now(zone)
        val start = today.minusDays(34)
        val fromMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        coroutineScope {
            val profileJob = async {
                runCatching { api.profile(userId) }
                    .onSuccess { profile = it }
                    .onFailure { warnings.add("个人信息：${it.friendly()}") }
            }
            // Band detail is chunked: one week fits comfortably in one response.
            val bandJobs = (0..4).map { index ->
                val chunkStart = start.plusDays(index * 7L)
                val chunkEnd = minOf(chunkStart.plusDays(6), today)
                async {
                    runCatching {
                        api.bandData(userId, chunkStart.toString(), chunkEnd.toString())
                    }.onSuccess { band.add(it) }.onFailure {
                        if (warnings.size < 6) warnings.add("手环明细：${it.friendly()}")
                    }
                }
            }
            val readinessJob = async {
                runCatching { api.events(userId, "readiness", "watch_score", fromMs, toMs, 200) }
                    .onSuccess { readiness = it }.onFailure { warnings.add("准备度：${it.friendly()}") }
            }
            val paiJob = async {
                runCatching { api.events(userId, "PaiHealthInfo", null, fromMs, toMs, 200) }
                    .onSuccess { pai = it }.onFailure { warnings.add("PAI：${it.friendly()}") }
            }
            val stressJob = async {
                runCatching { api.events(userId, "all_day_stress", null, fromMs, toMs, 200) }
                    .onSuccess { stress = it }.onFailure { warnings.add("压力：${it.friendly()}") }
            }
            val spO2Job = async {
                runCatching {
                    val merged = JSONArray()
                    for (i in 0 until 3) {
                        val chunkStart = today.minusDays(2L + i * 3L)
                        val chunkEnd = minOf(chunkStart.plusDays(2), today)
                        val page = api.events(
                            userId, "blood_oxygen", null,
                            chunkStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                            chunkEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                            1000
                        )
                        page.optJSONArray("items")?.let { items ->
                            for (j in 0 until items.length()) merged.put(items.get(j))
                        }
                    }
                    JSONObject().put("items", merged)
                }.onSuccess { spo2 = it }.onFailure { warnings.add("血氧：${it.friendly()}") }
            }
            val chargeJob = async {
                runCatching { api.v2Events("Charge", "real_data", fromMs, toMs, 200) }
                    .onSuccess { charge = it }.onFailure { warnings.add("Charge：${it.friendly()}") }
            }
            val loadJob = async {
                runCatching {
                    // The fitness model needs a full 42-day window, so ask for
                    // more history than the page itself shows.
                    api.watchStatistics(
                        userId,
                        "SPORT_LOAD",
                        today.minusDays(59).toString(),
                        today.plusDays(1).toString()
                    )
                }.onSuccess { load = it }.onFailure { warnings.add("训练负荷：${it.friendly()}") }
            }
            val vo2Job = async {
                runCatching {
                    // VO2 max changes rarely; the API only lists days on which
                    // it was re-estimated, so ask for a long window and keep the
                    // newest non-zero value (the official app shows it forever).
                    api.watchStatistics(
                        userId,
                        "VO2_MAX",
                        today.minusDays(730).toString(),
                        today.plusDays(1).toString()
                    )
                }.onSuccess { vo2 = it }.onFailure { warnings.add("最大摄氧量：${it.friendly()}") }
            }
            val weightJob = async {
                runCatching {
                    api.weights(
                        userId,
                        // Three years: the body-composition card needs the last
                        // scale reading even when it is older than a year (the
                        // official app shows an August 2025 reading in 2026).
                        today.minusDays(1090).atStartOfDay(zone).toInstant().epochSecond,
                        today.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
                    )
                }.onSuccess { weights = it }.onFailure { warnings.add("体重：${it.friendly()}") }
            }
            val hrvJob = async {
                runCatching { api.hrv(fromMs, toMs, 200) }
                    .onSuccess { hrv = it }
                    .onFailure { warnings.add("HRV：${it.friendly()}") }
            }
            val dailyHealthJob = async {
                runCatching { api.v2Events("DailyHealth", "summary", fromMs, toMs, 200) }
                    .onSuccess { dailyHealth = it }
                    .onFailure { warnings.add("每日健康：${it.friendly()}") }
            }
            // Both streams are optional: firmware without the sensor answers
            // with an empty list, and the sleep page simply omits those cards.
            val respirationJob = async {
                runCatching { api.respiration(fromMs, toMs, 200) }.onSuccess { respiration = it }
            }
            val nightBreathingJob = async {
                runCatching { api.nightBreathing(userId, fromMs, toMs, 200) }
                    .onSuccess { nightBreathing = it }
            }
            val workoutJob = async {
                runCatching {
                    api.workouts(
                        userId,
                        today.minusDays(60).atStartOfDay(zone).toInstant().epochSecond,
                        today.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
                    )
                }.onSuccess { workouts = it }.onFailure { warnings.add("运动：${it.friendly()}") }
            }
            listOf(
                profileJob, readinessJob, paiJob, stressJob, spO2Job, chargeJob, loadJob, vo2Job,
                weightJob, workoutJob, hrvJob, dailyHealthJob, respirationJob,
                nightBreathingJob
            ).forEach { it.await() }
            bandJobs.forEach { it.await() }
        }

        val bandParsed = band.mapNotNull { page ->
            runCatching { HealthParser.parseBand(page, zone) }.getOrNull()
        }
        // 活动时长 and its goal live in DailyHealth, not in the band summary;
        // walking is excluded there, which is the official definition.
        val activityByDate = dailyHealth
            ?.let { runCatching { HealthParser.parseDailyHealth(it, zone) }.getOrNull() }
            .orEmpty()
        val previousDays = previous?.days.orEmpty().associateBy { it.date }
        val days = bandParsed.flatMap { it.days }.distinctBy { it.date }.sortedBy { it.date }
            .map { day ->
                val previousDay = previousDays[day.date]
                day.copy(
                    activityMinutes = activityByDate[day.date]?.minutes ?: previousDay?.activityMinutes,
                    activityGoal = activityByDate[day.date]?.goal ?: previousDay?.activityGoal
                )
            }
        val nights = bandParsed.flatMap { it.nights }.distinctBy { it.date }.sortedBy { it.date }
        val heartRate = bandParsed.flatMap { it.heartRate }.distinctBy { it.first }.sortedBy { it.first }

        val nicknameFromProfile = profile?.let { HealthParser.parseProfile(it) }
        val now = System.currentTimeMillis()
        // The hand-band pull carries steps, sleep and heart rate: only when it
        // lands does the snapshot count as "just fetched". A partial refresh
        // must not relabel hours-old numbers as current.
        val coreOk = band.isNotEmpty()
        val anyOk = coreOk || readiness != null || pai != null || stress != null || spo2 != null ||
            charge != null || load != null || vo2 != null || weights != null || workouts != null || hrv != null ||
            dailyHealth != null || profile != null

        if (!anyOk) {
            state = state.copy(
                loading = false,
                refreshing = false,
                error = warnings.firstOrNull() ?: "暂时没有取到数据",
                data = previous
            )
            return
        }

        val fetchedAt = if (coreOk) now else (previous?.fetchedAt ?: 0L)
        val data = HealthData(
            fetchedAt = fetchedAt,
            nickname = nicknameFromProfile?.first ?: previous?.nickname ?: settings.nickname,
            avatarUrl = nicknameFromProfile?.second ?: previous?.avatarUrl ?: settings.avatarUrl,
            days = days.ifEmpty { previous?.days.orEmpty() },
            nights = nights.ifEmpty { previous?.nights.orEmpty() },
            chargeDays = charge?.let { HealthParser.parseCharge(it, zone) }
                ?.ifEmpty { previous?.chargeDays.orEmpty() } ?: previous?.chargeDays.orEmpty(),
            readiness = readiness?.let { HealthParser.parseReadiness(it, zone) }
                ?.ifEmpty { previous?.readiness.orEmpty() } ?: previous?.readiness.orEmpty(),
            pai = pai?.let { HealthParser.parsePai(it, zone) }
                ?.ifEmpty { previous?.pai.orEmpty() } ?: previous?.pai.orEmpty(),
            stress = stress?.let { HealthParser.parseStress(it, zone) }
                ?.ifEmpty { previous?.stress.orEmpty() } ?: previous?.stress.orEmpty(),
            spo2 = spo2?.let { HealthParser.parseSpo2(it) }
                ?.ifEmpty { previous?.spo2.orEmpty() } ?: previous?.spo2.orEmpty(),
            weights = weights?.let { HealthParser.parseWeights(it) }
                ?.ifEmpty { previous?.weights.orEmpty() } ?: previous?.weights.orEmpty(),
            workouts = workouts?.let { HealthParser.parseWorkouts(it) }
                ?.ifEmpty { previous?.workouts.orEmpty() } ?: previous?.workouts.orEmpty(),
            load = load?.let { HealthParser.parseLoad(it, zone) }
                ?.ifEmpty { previous?.load.orEmpty() } ?: previous?.load.orEmpty(),
            heartRate = heartRate.ifEmpty { previous?.heartRate.orEmpty() },
            hrv = hrv?.let { HealthParser.parseHrv(it) }.orEmpty().ifEmpty { previous?.hrv.orEmpty() },
            respiration = respiration?.let { HealthParser.parseRespiration(it, zone) }
                .orEmpty().ifEmpty { previous?.respiration.orEmpty() },
            nightBreathing = nightBreathing?.let { HealthParser.parseNightBreathing(it, zone) }
                .orEmpty().ifEmpty { previous?.nightBreathing.orEmpty() },
            vo2Max = vo2?.let { HealthParser.parseVo2Max(it) } ?: previous?.vo2Max,
            body = profile?.let { HealthParser.parseBodyProfile(it) } ?: previous?.body ?: BodyProfile(),
            warnings = warnings.distinct()
        ).let { it.copy(strain = StrainCalculator.compute(it.days, it.heartRate, it.readiness, it.workouts, it.body, it.stress, it.chargeDays, it.nights, zone)) }
        // The two night streams are optional; the count is how a missing
        // sensor is told apart from a parser that did not understand the reply.
        Log.d("ZeppApi", "respiration=${data.respiration.size} apnea=${data.nightBreathing.size}")

        // Signed out while this request was in flight: its answer belongs to a
        // session that no longer exists, so neither the page nor the file keeps
        // it. [reset] bumped the generation.
        if (gen != generation) return

        state = state.copy(
            loading = false,
            refreshing = false,
            data = data,
            error = if (coreOk) null else (warnings.firstOrNull() ?: "手环明细没有取到"),
            lastSyncedAt = if (coreOk) now else state.lastSyncedAt
        )

        // Keep whatever this run could not fetch from the previous snapshot, so
        // a partially failed sync never wipes the local cache.
        val previousJson = withContext(Dispatchers.IO) {
            runCatching { JSONObject(cacheFile().readText()) }.getOrNull()
        }
        val snapshot = JSONObject()
        snapshot.put("fetchedAt", data.fetchedAt)
        snapshot.put("nickname", data.nickname)
        snapshot.put("avatarUrl", data.avatarUrl)
        fun carrySection(key: String, fresh: JSONObject?) {
            val value = fresh ?: previousJson?.optJSONObject(key) ?: return
            snapshot.put(key, value)
        }
        carrySection("profile", profile)
        snapshot.put("band", JSONArray().apply {
            if (band.isNotEmpty()) {
                band.forEach { put(it) }
            } else {
                previousJson?.optJSONArray("band")?.let { arr ->
                    for (i in 0 until arr.length()) put(arr.get(i))
                }
            }
        })
        carrySection("readiness", readiness)
        carrySection("pai", pai)
        carrySection("stress", stress)
        carrySection("spo2", spo2)
        carrySection("charge", charge)
        carrySection("load", load)
        carrySection("vo2", vo2)
        carrySection("weights", weights)
        carrySection("workouts", workouts)
        carrySection("hrv", hrv)
        carrySection("dailyHealth", dailyHealth)
        carrySection("respiration", respiration)
        carrySection("nightBreathing", nightBreathing)

        withContext(Dispatchers.IO) {
            if (gen == generation) {
                runCatching { cacheFile().writeText(snapshot.toString()) }
                // The home-screen rings read this same file: redraw them now
                // instead of waiting for the widget's own half-hour tick.
                runCatching { com.genkaim.zeppglass.ui.widget.YdooRingsWidget.updateAll(appContext) }
            }
        }
    }

    private class Cached(val data: HealthData, val fetchedAt: Long)

    private fun readSnapshot(): Cached? =
        readSnapshotFile(cacheFile(), zone)?.let { Cached(it, it.fetchedAt) }

    companion object {
        /** Fallback bucket for workouts whose history row had no `source`. */
        private const val DEFAULT_WORKOUT_SOURCE = "run.mifit.huami.com"


        @Volatile
        private var instance: HealthStore? = null

        fun get(context: Context, settings: AppSettings): HealthStore =
            instance ?: synchronized(this) {
                instance ?: HealthStore(context, settings).also { instance = it }
            }

        /**
         * The snapshot the app opens with, for surfaces that run outside the
         * app's own store — the home-screen widget reads this instead of
         * spinning up [HealthStore] (which owns a network refresh loop).
         */
        fun readCached(context: Context, settings: AppSettings): HealthData? =
            readSnapshotFile(
                AccountScope.file(context, "health-snapshot", settings),
                ZoneId.systemDefault()
            )
    }
}

/**
 * Turns the on-disk snapshot back into [HealthData]. Extracted from the store
 * so the widget can read the same numbers the home page shows, and so both
 * paths parse the file exactly one way.
 */
private fun readSnapshotFile(file: File, zone: ZoneId): HealthData? {
    if (!file.isFile) return null
    return runCatching {
        val json = JSONObject(file.readText())
        val band = mutableListOf<JSONObject>()
        json.optJSONArray("band")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(band::add)
        }
        val parsed = band.map { HealthParser.parseBand(it, zone) }
        val profile = json.optJSONObject("profile")
        val nickname = profile?.let { HealthParser.parseProfile(it) }
        val activityByDate = json.optJSONObject("dailyHealth")
            ?.let { runCatching { HealthParser.parseDailyHealth(it, zone) }.getOrNull() }
            .orEmpty()
        val data = HealthData(
            fetchedAt = json.optLong("fetchedAt"),
            nickname = json.optString("nickname").ifBlank { nickname?.first },
            avatarUrl = json.optString("avatarUrl").ifBlank { nickname?.second },
            days = parsed.flatMap { it.days }.distinctBy { it.date }.sortedBy { it.date }
                .map { day ->
                    day.copy(
                        activityMinutes = activityByDate[day.date]?.minutes ?: day.activityMinutes,
                        activityGoal = activityByDate[day.date]?.goal ?: day.activityGoal
                    )
                },
            nights = parsed.flatMap { it.nights }.distinctBy { it.date }.sortedBy { it.date },
            chargeDays = json.optJSONObject("charge")?.let { HealthParser.parseCharge(it, zone) }.orEmpty(),
            readiness = json.optJSONObject("readiness")?.let { HealthParser.parseReadiness(it, zone) }.orEmpty(),
            pai = json.optJSONObject("pai")?.let { HealthParser.parsePai(it, zone) }.orEmpty(),
            stress = json.optJSONObject("stress")?.let { HealthParser.parseStress(it, zone) }.orEmpty(),
            spo2 = json.optJSONObject("spo2")?.let { HealthParser.parseSpo2(it) }.orEmpty(),
            weights = json.optJSONObject("weights")?.let { HealthParser.parseWeights(it) }.orEmpty(),
            workouts = json.optJSONObject("workouts")?.let { HealthParser.parseWorkouts(it) }.orEmpty(),
            load = json.optJSONObject("load")?.let { HealthParser.parseLoad(it, zone) }.orEmpty(),
            hrv = json.optJSONObject("hrv")?.let { HealthParser.parseHrv(it) }.orEmpty(),
            respiration = json.optJSONObject("respiration")
                ?.let { HealthParser.parseRespiration(it, zone) }.orEmpty(),
            nightBreathing = json.optJSONObject("nightBreathing")
                ?.let { HealthParser.parseNightBreathing(it, zone) }.orEmpty(),
            vo2Max = json.optJSONObject("vo2")?.let { HealthParser.parseVo2Max(it) },
            body = profile?.let { HealthParser.parseBodyProfile(it) } ?: BodyProfile(),
            heartRate = parsed.flatMap { it.heartRate }.distinctBy { it.first }.sortedBy { it.first }
        ).let { it.copy(strain = StrainCalculator.compute(it.days, it.heartRate, it.readiness, it.workouts, it.body, it.stress, it.chargeDays, it.nights, zone)) }
        if (data.days.isEmpty() && data.nights.isEmpty() && data.chargeDays.isEmpty()) null else data
    }.getOrNull()
}

internal fun Throwable.friendly(): String = when (this) {
    is ZeppException -> message ?: "请求失败"
    else -> message?.take(80) ?: "网络错误"
}


