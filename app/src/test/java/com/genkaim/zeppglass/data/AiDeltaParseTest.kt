package com.genkaim.zeppglass.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Real api.deepseek.com stream, captured 2026-11-19. The thinking phase sends
 * `"content": null` on every chunk; `optString` answers the string "null" for
 * that, which is how the summary card filled with nullnullnull.
 */
class AiDeltaParseTest {

    private val reasoningChunk = """
        {"id":"a","object":"chat.completion.chunk","model":"deepseek-flash",
         "choices":[{"index":0,"delta":{"role":"assistant","content":null,"reasoning_content":"The"},"finish_reason":null}]}
    """.trimIndent()

    private val answerChunk = """
        {"id":"a","object":"chat.completion.chunk","model":"deepseek-flash",
         "choices":[{"index":0,"delta":{"content":"你好","reasoning_content":null},"finish_reason":null}]}
    """.trimIndent()

    private val stopChunk = """
        {"id":"a","choices":[{"index":0,"delta":{"content":""},"finish_reason":"stop"}],
         "usage":{"completion_tokens_details":{"reasoning_tokens":27}}}
    """.trimIndent()

    @Test
    fun `json null reads as empty text`() {
        val json = JSONObject("""{"content":null,"reasoning_content":null}""")
        assertEquals("", json.text("content"))
        assertEquals("", json.text("reasoning_content"))
        assertEquals("", json.text("missing"))
    }

    @Test
    fun `numbers still come back as text`() {
        assertEquals("3", JSONObject("""{"v":3}""").text("v"))
    }

    @Test
    fun `thinking chunk carries no answer`() {
        val (content, reasoning) = JSONObject(reasoningChunk).sseDelta()
        assertEquals("", content)
        assertEquals("The", reasoning)
    }

    @Test
    fun `answer chunk carries no thinking`() {
        val (content, reasoning) = JSONObject(answerChunk).sseDelta()
        assertEquals("你好", content)
        assertEquals("", reasoning)
    }

    @Test
    fun `stop chunk is empty on both sides`() {
        val (content, reasoning) = JSONObject(stopChunk).sseDelta()
        assertEquals("", content)
        assertEquals("", reasoning)
    }

    @Test
    fun `chunk without choices does not throw`() {
        val (content, reasoning) = JSONObject("""{"error":{"message":"boom"}}""").sseDelta()
        assertEquals("", content)
        assertEquals("", reasoning)
    }

    @Test
    fun `stored null runs are dropped`() {
        assertEquals("今天恢复不错", "nullnullnull今天恢复不错".withoutNullChunks())
        assertEquals("", "nullnull".withoutNullChunks())
        assertEquals("", "null".withoutNullChunks())
        assertEquals("", "".withoutNullChunks())
        // A real word that merely contains the letters is left alone.
        assertEquals("nullable", "nullable".withoutNullChunks())
    }
}
