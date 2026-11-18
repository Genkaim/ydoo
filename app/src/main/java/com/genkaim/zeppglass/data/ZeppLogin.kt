package com.genkaim.zeppglass.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Reads the Zepp app token out of what the login WebView exposes.
 *
 * The page stores the signed-in session as `hm-user-login-info`, sometimes in
 * localStorage, sometimes as a cookie, and the blob has had several spellings
 * over the years (`user_id` / `userid`, `apptoken` / `app_token`). The search is
 * therefore by key name and depth, not by a fixed path.
 */
object ZeppLogin {

    data class Credentials(
        val appToken: String,
        val userId: String,
        val hostHint: String?
    )

    private val TOKEN_KEYS = setOf("apptoken", "app_token", "app-token")
    private val USER_KEYS = setOf("user_id", "userid", "user")
    /** The web login keeps its session in these `.zepp.com` cookies. */
    private val TOKEN_COOKIES = setOf("apptoken", "app_token", "app-token", "token", "access_token")
    private val USER_COOKIES = setOf("userid", "user_id", "uid")
    private val REGION_HOSTS = mapOf(
        "cn" to "https://api-mifit-cn.huami.com",
        "china" to "https://api-mifit-cn.huami.com",
        "us" to "https://api-mifit-us.huami.com",
        "de" to "https://api-mifit-de.huami.com",
        "eu" to "https://api-mifit-de.huami.com",
        "sg" to "https://api-mifit-sg.huami.com",
        "in" to "https://api-mifit-in.huami.com",
        "ru" to "https://api-mifit-ru.huami.com"
    )

    fun parse(pageResult: JSONObject): Credentials? {
        val found = Found()
        var domainsRaw: String? = null
        scan(pageResult, 0, found)

        // The signed-in web session really lives in plain `.zepp.com` cookies
        // named apptoken / userid — not in hm-user-login-info. Read them by name
        // as well, or a successful web login never yields a token.
        if (found.token == null || found.userId == null) {
            val cookie = pageResult.optString("cookie")
            cookie.split(';').forEach { part ->
                val entry = part.trim()
                if (entry.isEmpty()) return@forEach
                val name = entry.substringBefore('=').trim().lowercase()
                val raw = entry.substringAfter('=', "")
                if (raw.isBlank()) return@forEach
                when {
                    name in TOKEN_COOKIES && found.token == null -> {
                        cleanCookie(raw)?.takeIf { it.length > 20 }?.let { found.token = it }
                    }
                    name in USER_COOKIES && found.userId == null -> {
                        cleanCookie(raw)?.takeIf { it.all(Char::isDigit) && it.length in 5..20 }
                            ?.let { found.userId = it }
                    }
                    name == "region" && found.region == null -> {
                        found.region = cleanCookie(raw)?.lowercase()
                    }
                    name == "domains" -> domainsRaw = raw
                    name.contains("login") || raw.trimStart().startsWith("{") ||
                        raw.contains("%7B", ignoreCase = true) -> decodeAndScan(raw, found)
                }
            }
        }
        val token = found.token ?: return null
        val userId = found.userId ?: return null
        val hint = found.cname?.let { "https://$it" }
            ?: hostFromDomains(domainsRaw)
            ?: REGION_HOSTS[found.region]
        return Credentials(token, userId, hint)
    }

    /**
     * The `domains` cookie lists this account's regional hosts, so the token can
     * be checked against the right one first instead of trying all of them.
     */
    private fun hostFromDomains(raw: String?): String? {
        if (raw == null) return null
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        val array = runCatching { JSONArray(decoded) }.getOrNull() ?: return null
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (!item.optString("host").startsWith("api-mifit")) continue
            val cname = item.optJSONArray("cnames")?.optString(0)?.takeIf { it.isNotBlank() }
            if (cname != null) return "https://$cname"
        }
        return null
    }

    /** Cookie values are sometimes quoted or percent-encoded. */
    private fun cleanCookie(raw: String): String? {
        // Only percent-decode when there is an escape: URLDecoder would turn a
        // literal '+' inside a base64 token into a space.
        val decoded = if (raw.contains('%')) {
            runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        } else raw
        return decoded.trim().trim('"').takeIf { it.isNotEmpty() }
    }

    private class Found {
        var token: String? = null
        var userId: String? = null
        var region: String? = null
        var cname: String? = null
    }

    private fun decodeAndScan(raw: String, found: Found) {
        if (raw.isBlank()) return
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        if (decoded.trimStart().startsWith("{") || decoded.trimStart().startsWith("[")) {
            runCatching { scan(JSONObject(decoded), 0, found) }
                .onFailure { runCatching { scan(JSONArray(decoded), 0, found) } }
        } else {
            // A bare value under a known cookie name: assume it is the token.
            if (found.token == null && decoded.length > 20) found.token = decoded.trim()
        }
    }

    private fun scan(value: Any?, depth: Int, found: Found) {
        if (depth > 8) return
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    val lower = key.lowercase()
                    if (child is String) {
                        val text = child.trim()
                        if (text.isNotEmpty()) inspectString(lower, text, depth, found)
                    } else {
                        scan(child, depth + 1, found)
                    }
                }
            }
            is JSONArray -> for (i in 0 until value.length()) scan(value.opt(i), depth + 1, found)
        }
    }

    private fun inspectString(lower: String, text: String, depth: Int, found: Found) {
        when {
            lower in TOKEN_KEYS && found.token == null && text.length > 20 -> found.token = text
            lower in USER_KEYS && found.userId == null && text.all { it.isDigit() } &&
                text.length in 5..20 -> found.userId = text
            lower == "region" && text.length <= 8 -> found.region = text.lowercase()
            lower == "cname" && text.contains(".") -> found.cname = text.removePrefix("https://").trimEnd('/')
        }
        val trimmed = text.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            runCatching { if (trimmed.startsWith("{")) scan(JSONObject(text), depth + 1, found) else scan(JSONArray(text), depth + 1, found) }
        }
        if (text.contains("%7B") || text.contains("%7b") || lower.contains("login")) {
            runCatching {
                val decoded = URLDecoder.decode(text, "UTF-8")
                if (decoded.trimStart().startsWith("{")) scan(JSONObject(decoded), depth + 1, found)
            }
        }
    }
}
