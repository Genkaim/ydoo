package com.genkaim.zeppglass.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import java.io.File

/**
 * Signing out ends the web session; it does not delete anything.
 *
 * The pages the login WebView opened keep their own cookie jar, and that jar is
 * shared by whoever signs in next, so it is cleared here. Everything else — the
 * health snapshot, the diet log with its photos, the chat archive, today's AI
 * summary — is filed under the account that produced it (see [AccountScope]),
 * so the next account starts empty and this one gets its data back when it
 * signs in again.
 *
 * Configuration is not data and is never touched: the AI endpoint, the theme
 * and the tab layout belong to the install.
 */
object SignOut {

    fun run(context: Context, settings: AppSettings, store: HealthStore) {
        val app = context.applicationContext
        clearWebSession(app)
        settings.clearSession()
        Account.switch(app, settings, store)
    }

    /**
     * Cookies, storage and the WebView's own cache. A WebView that is still
     * alive holds its profile open, so failures here are best effort: the next
     * sign-in starts from a fresh jar anyway.
     */
    private fun clearWebSession(context: Context) {
        runCatching {
            val cookies = CookieManager.getInstance()
            // Flush after the removal lands, not before it: the jar on disk is
            // what the next launch reads back.
            cookies.removeAllCookies { runCatching { cookies.flush() } }
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
        webViewCaches.forEach { path ->
            runCatching { File(context.cacheDir, path).deleteRecursively() }
            runCatching { File(context.filesDir, path).deleteRecursively() }
        }
    }

    private val webViewCaches = listOf(
        "WebView",
        "app_webview",
        "org.chromium.android_webview",
        "webviewCacheChromium"
    )
}
