package com.genkaim.zeppglass.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

class ZeppLoginTest {

    private val token = "A".repeat(40) + "0123456789"

    @Test
    fun readsTheBlobFromLocalStorage() {
        val blob = JSONObject()
            .put("user_id", "1000000007")
            .put("apptoken", token)
            .put("region", "cn")
        val page = JSONObject()
            .put("storage", JSONObject().put("hm-user-login-info", blob.toString()))
            .put("cookie", "")

        val found = ZeppLogin.parse(page)!!
        assertEquals("1000000007", found.userId)
        assertEquals(token, found.appToken)
        assertEquals("https://api-mifit-cn.huami.com", found.hostHint)
    }

    @Test
    fun readsTheSameBlobFromAnEncodedCookie() {
        val blob = JSONObject()
            .put("userId", "1000000007")
            .put("appToken", token)
        val cookie = "hm-user-login-info=" + URLEncoder.encode(blob.toString(), "UTF-8")
        val page = JSONObject()
            .put("storage", JSONObject())
            .put("cookie", "other=1; $cookie; tail=2")

        val found = ZeppLogin.parse(page)!!
        assertEquals("1000000007", found.userId)
        assertEquals(token, found.appToken)
    }

    @Test
    fun looksThroughNestedLoginInfo() {
        val inner = JSONObject().put("userid", "1234567").put("app_token", token)
        val blob = JSONObject().put("token_info", inner.toString())
        val page = JSONObject()
            .put("storage", JSONObject().put("loginInfo", blob.toString()))
            .put("cookie", "")

        val found = ZeppLogin.parse(page)!!
        assertEquals("1234567", found.userId)
        assertEquals(token, found.appToken)
    }

    @Test
    fun readsTheSessionFromZeppCookies() {
        val domains = URLEncoder.encode(
            """[{"cnames":["api-mifit-cn3.zepp.com"],"host":"api-mifit.huami.com"}]""",
            "UTF-8"
        )
        val page = JSONObject()
            .put("storage", JSONObject())
            .put(
                "cookie",
                "name=Genkaim; apptoken=$token; userid=1000000007; region=1; domains=$domains"
            )

        val found = ZeppLogin.parse(page)!!
        assertEquals(token, found.appToken)
        assertEquals("1000000007", found.userId)
        assertEquals("https://api-mifit-cn3.zepp.com", found.hostHint)
    }

    @Test
    fun aPageWithoutCredentialsYieldsNull() {
        val page = JSONObject()
            .put("storage", JSONObject().put("theme", "dark"))
            .put("cookie", "a=b")
        assertNull(ZeppLogin.parse(page))
    }

    @Test
    fun doesNotMistakeShortValuesForAToken() {
        val blob = JSONObject().put("user_id", "1000000007").put("apptoken", "short")
        val page = JSONObject()
            .put("storage", JSONObject().put("hm-user-login-info", blob.toString()))
            .put("cookie", "")
        assertNull(ZeppLogin.parse(page))
    }
}
