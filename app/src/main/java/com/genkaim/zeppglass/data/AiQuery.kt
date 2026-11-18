package com.genkaim.zeppglass.data

/**
 * Pulls the data-agent query out of a model reply.
 *
 * This used to be one regular expression with a lazy brace group. Android's
 * regex engine rejects that pattern ("Syntax error in regexp pattern near
 * index 26"), which surfaced as "请求失败" on every message, so the scan is
 * plain text now: it walks fenced blocks first, then balances braces while
 * skipping over JSON string literals.
 */
object AiQuery {

    private const val FENCE = "```"

    fun extract(text: String): String? {
        var cursor = 0
        while (true) {
            val fence = text.indexOf(FENCE, cursor)
            if (fence < 0) break
            val bodyStart = text.indexOf('\n', fence)
            if (bodyStart < 0) break
            val end = text.indexOf(FENCE, bodyStart)
            if (end < 0) break
            val body = text.substring(bodyStart + 1, end)
            if (body.contains("\"table\"")) {
                firstObject(body)?.let { return it }
            }
            cursor = end + FENCE.length
        }
        return firstObject(text)
    }

    /** First complete JSON object that names a table, or null. */
    fun firstObject(text: String): String? {
        var start = text.indexOf('{')
        while (start >= 0) {
            var depth = 0
            var inString = false
            var escaped = false
            for (index in start until text.length) {
                val c = text[index]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = text.substring(start, index + 1)
                            if (candidate.contains("\"table\"")) return candidate
                            break
                        }
                    }
                }
            }
            // No balanced object here; try the next opening brace.
            start = text.indexOf('{', start + 1)
        }
        return null
    }
}
