package com.genkaim.zeppglass.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The data agent depends on pulling one `{"table":…}` object out of a reply.
 * This used to be a regex that Android's engine refused to compile, so the
 * cases below also guard against anything breaking messages again.
 */
class AiQueryTest {

    @Test
    fun readsQueryFromFencedBlock() {
        val reply = """
            先看一下睡眠数据。
            ```query
            {"table":"sleep","days":7}
            ```
        """.trimIndent()
        val query = AiQuery.extract(reply)
        assertEquals("sleep", JSONObject(query!!).getString("table"))
    }

    @Test
    fun readsQueryFromBareObject() {
        val reply = "我需要数据：{\"table\":\"daily\",\"from\":\"2026-11-10\",\"to\":\"2026-11-17\"}"
        val query = AiQuery.extract(reply)!!
        assertEquals("daily", JSONObject(query).getString("table"))
    }

    @Test
    fun ignoresProseBracesBeforeTheQuery() {
        val reply = "示例 {不是查询} 然后\n{\"table\":\"pai\",\"days\":3}"
        val query = AiQuery.extract(reply)!!
        assertEquals("pai", JSONObject(query).getString("table"))
    }

    @Test
    fun bracesInsideStringsDoNotCloseTheObject() {
        val reply = "{\"table\":\"sleep\",\"note\":\"a } b { c\",\"days\":7}"
        val query = AiQuery.extract(reply)!!
        assertEquals("sleep", JSONObject(query).getString("table"))
        assertEquals(7, JSONObject(query).getInt("days"))
    }

    @Test
    fun finalAnswerHasNoQuery() {
        assertNull(AiQuery.extract("你的准备度是 78 分，今天适合轻松跑。"))
    }

    @Test
    fun unclosedObjectIsIgnored() {
        assertNull(AiQuery.extract("{\"table\":\"sleep\""))
    }
}
