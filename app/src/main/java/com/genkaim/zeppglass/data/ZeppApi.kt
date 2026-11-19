package com.genkaim.zeppglass.data

import android.os.Build
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin client for the Zepp app API.
 *
 * This is the same transport ZeppBridge uses for its "advanced data"
 * connection: an `apptoken` captured from the Zepp login page, sent with the
 * iOS app's own identifying headers. Only read endpoints are called.
 */
class ZeppApi(
    private val settings: AppSettings,
    /** Set only while verifying a freshly captured token. */
    private val overrideToken: String? = null,
    /** Set only while probing candidate regional hosts. */
    private val overrideHost: String? = null
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean
        get() = settings.isLoggedIn && settings.token != null && settings.userId != null

    fun get(
        path: String,
        params: Map<String, String> = emptyMap(),
        host: String = overrideHost ?: settings.regionHost
    ): JSONObject {
        val token = overrideToken ?: settings.token ?: error("未登录")
        val base = host.trimEnd('/')
        val url = (base + path).toHttpUrlOrNull()?.newBuilder()?.apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("r", "ZEPPGLASS-" + System.currentTimeMillis())
        }?.build() ?: error("接口地址无效：$base$path")

        val request = Request.Builder()
            .url(url)
            .header("apptoken", token)
            .header("appname", "com.huami.midong")
            .header("appplatform", "ios_phone")
            .header("accept", "*/*")
            .header("v", "2.0")
            .header("vn", "10.2.5")
            .header("cv", "1722_10.2.5")
            .header("vb", "202604132257")
            .header("lang", "zh")
            .header("country", "CN")
            .header("timezone", "Asia/Shanghai")
            .header("user-agent", "ZeppGlass/0.1 (Android ${Build.VERSION.RELEASE})")
            .get()
            .build()

        val startedAt = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(
                    TAG,
                    "GET $base$path -> ${response.code} in ${System.currentTimeMillis() - startedAt}ms"
                )
                if (!response.isSuccessful) {
                    val code = response.code
                    val message = when (code) {
                        401, 403 -> "登录已失效，请重新连接 Zepp 账号"
                        429 -> "请求太频繁，稍后再试"
                        else -> "Zepp 返回 HTTP $code"
                    }
                    throw ZeppException(code, message)
                }
                if (body.isBlank()) throw ZeppException(response.code, "Zepp 返回了空响应")
                return JSONObject(body)
            }
        } catch (e: ZeppException) {
            throw e
        } catch (e: Exception) {
            Log.w(
                TAG,
                "GET $base$path failed after ${System.currentTimeMillis() - startedAt}ms: " +
                    "${e.javaClass.simpleName}: ${e.message}"
            )
            throw e
        }
    }

    private companion object {
        const val TAG = "ZeppApi"
    }

    fun profile(userId: String): JSONObject = get("/users/$userId")

    fun bandData(userId: String, fromDate: String, toDate: String): JSONObject = get(
        "/v1/data/band_data.json",
        mapOf(
            "userid" to userId,
            "from_date" to fromDate,
            "to_date" to toDate,
            "query_type" to "detail",
            "byteLength" to "8",
            "device_type" to "0"
        )
    )

    fun events(
        userId: String,
        eventType: String,
        subType: String?,
        fromMs: Long,
        toMs: Long,
        limit: Int = 200,
        reverse: Boolean = true
    ): JSONObject {
        val params = linkedMapOf(
            "eventType" to eventType,
            "from" to fromMs.toString(),
            "to" to toMs.toString(),
            "limit" to limit.toString(),
            "reverse" to if (reverse) "1" else "0",
            "userId" to userId
        )
        if (subType != null) params["subType"] = subType
        return get("/users/$userId/events", params)
    }

    fun v2Events(
        eventType: String,
        subType: String?,
        fromMs: Long,
        toMs: Long,
        limit: Int = 200
    ): JSONObject {
        val params = linkedMapOf(
            "eventType" to eventType,
            "from" to fromMs.toString(),
            "to" to toMs.toString(),
            "limit" to limit.toString(),
            "reverse" to "1"
        )
        if (subType != null) params["subType"] = subType
        return get("/v2/users/me/events", params)
    }

    fun watchStatistics(
        userId: String,
        statistic: String,
        startDay: String,
        endDay: String,
        limit: Int = 900
    ): JSONObject = get(        "/v2/watch/users/$userId/WatchSportStatistics/$statistic",
        mapOf(
            "startDay" to startDay,
            "endDay" to endDay,
            "limit" to limit.toString(),
            "isReverse" to "true"
        )
    )

    /**
     * All-day HRV (rMSSD). The app's own event is `HRVRMSSD` / `real_data`:
     * one item per day, samples as `{hrv, s}` with `s` an offset in the day.
     */
    fun hrv(fromMs: Long, toMs: Long, limit: Int = 200): JSONObject =
        v2Events("HRVRMSSD", "real_data", fromMs, toMs, limit)

    fun weights(userId: String, fromSeconds: Long, toSeconds: Long): JSONObject = get(
        "/users/$userId/members/-1/weightRecords",
        mapOf(
            "fromTime" to fromSeconds.toString(),
            "toTime" to toSeconds.toString(),
            "limit" to "120",
            "isForward" to "0"
        )
    )

    fun workouts(userId: String, startSeconds: Long, stopSeconds: Long): JSONObject = get(
        "/v1/sport/run/history.json",
        mapOf(
            "userid" to userId,
            "startTrackId" to startSeconds.toString(),
            "stopTrackId" to stopSeconds.toString(),
            "need_sub_data" to "1",
            "type" to ""
        )
    )

    /**
     * Per-workout detail: the GPS track ("longitude_latitude"), heart rate and
     * sport specific fields. `source` comes from the history row.
     */
    fun workoutDetail(trackId: String, source: String): JSONObject = get(
        "/v1/sport/run/detail.json",
        mapOf("trackid" to trackId, "source" to source)
    )
}

class ZeppException(val code: Int, message: String) : Exception(message)
