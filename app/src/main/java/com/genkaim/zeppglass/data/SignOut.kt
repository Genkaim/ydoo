package com.genkaim.zeppglass.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import java.io.File

/**
 * Leaving the account has to leave nothing behind.
 *
 * The token in [AppSettings] is only the key: the pages the login WebView
 * opened keep their own cookie jar and local storage, and the app keeps a
 * health snapshot, the AI chat archive and the diet log with its photos. All of
 * it belongs to the account, so all of it goes.
 *
 * Configuration is not data and stays: the AI endpoint, the theme and the tab
 * layout are the user's setup, not the account's contents.
 */
object SignOut {

    fun run(context: Context, settings: AppSettings, store: HealthStore) {
        val app = context.applicationContext
        clearWebSession(app)
        AiChatSession.clearAll()
        DietStore.wipe()
        store.reset()
        settings.clearDerivedData()
        settings.clearSession()
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
