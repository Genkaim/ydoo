package com.genkaim.zeppglass.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.data.ZeppApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val LOGIN_URL = "https://watchface.zepp.com/"
private const val FALLBACK_LOGIN_URL = "https://user.huami.com/privacy2/index.html"

/**
 * Reads the Zepp session out of the login page.
 *
 * The page keeps the signed-in session in `hm-user-login-info` (localStorage or
 * a cookie). The script returns everything that looks related; parsing happens
 * in Kotlin so a nested JSON string is handled the same way twice.
 */
private const val EXTRACT_SCRIPT = """
(function(){
  try {
    var wanted = ['hm-user-login-info','hm_user_login_info','userid','user_id','apptoken','app_token','app-token','token_info','loginInfo','domains','cname','region','country_code','wf_baseUrl'];
    var out = {};
    [window.localStorage, window.sessionStorage].forEach(function(store){
      if (!store) return;
      for (var i = 0; i < store.length; i++) {
        var key = store.key(i);
        if (!key) continue;
        var lowered = key.toLowerCase();
        var value = store.getItem(key) || '';
        if (wanted.some(function(name){ return lowered.indexOf(name) !== -1; })) {
          if (!(key in out)) out[key] = value;
        } else if (value && value.length < 20000 && value.indexOf('apptoken') !== -1) {
          // Some builds keep the session under a key we do not know by name.
          if (!(key in out)) out[key] = value;
        }
      }
    });
    return JSON.stringify({ storage: out, cookie: document.cookie || '', href: location.href });
  } catch (e) { return JSON.stringify({ error: String(e) }); }
})()
"""

/**
 * Google refuses account sign-in inside an Android WebView (`disallowed_useragent`),
 * so its button is removed instead of leading to a guaranteed dead end.
 * Apple and Xiaomi run as ordinary web flows and stay visible.
 */
private const val HIDE_GOOGLE_SCRIPT = """
(function(){
  try {
    var hide = function(el){
      try {
        var target = (el.closest && el.closest('a, button, [role="button"], li')) || el;
        target.style.setProperty('display','none','important');
      } catch (e) {}
    };
    var scan = function(){
      try {
        var nodes = document.querySelectorAll(
          '[class*="google" i], [id*="google" i], a[href*="google" i], [data-provider*="google" i], [data-type*="google" i]'
        );
        for (var i = 0; i < nodes.length; i++) hide(nodes[i]);
        var clickable = document.querySelectorAll('a, button, [role="button"]');
        for (var j = 0; j < clickable.length; j++) {
          var text = (clickable[j].textContent || '').trim().toLowerCase();
          if (text.indexOf('google') !== -1 && text.length < 30) hide(clickable[j]);
        }
      } catch (e) {}
    };
    if (!window.__zgHideGoogle) {
      window.__zgHideGoogle = true;
      new MutationObserver(scan).observe(document, { childList: true, subtree: true });
      document.addEventListener('DOMContentLoaded', scan);
    }
    scan();
  } catch (e) {}
})()
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    settings: AppSettings,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { HealthStore.get(context, settings) }
    val scope = rememberCoroutineScope()

    var manual by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在打开 Zepp 登录页…") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    var manualToken by remember { mutableStateOf("") }
    var manualUserId by remember { mutableStateOf("") }
    var manualHost by remember { mutableStateOf(AppSettings.DEFAULT_HOST) }

    fun finish(token: String, userId: String, host: String?) {
        if (busy) return
        busy = true
        error = null
        status = "正在验证账号…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                verifyToken(settings, token, userId, host)
            }
            busy = false
            result.onSuccess { verified ->
                settings.saveSession(token, userId, verified.host, verified.nickname, verified.avatar)
                store.onLoginChanged()
                onDone()
            }.onFailure {
                error = it.message ?: "验证失败"
                status = "登录页已读取，但账号验证没通过"
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun extract(webView: WebView, announce: Boolean) {
        webView.evaluateJavascript(HIDE_GOOGLE_SCRIPT, null)
        webView.evaluateJavascript(EXTRACT_SCRIPT) { raw ->
            val text = unquoteJson(raw) ?: return@evaluateJavascript
            val parsed = runCatching { JSONObject(text) }.getOrNull() ?: return@evaluateJavascript
            if (parsed.has("error")) return@evaluateJavascript
            // document.cookie misses HttpOnly cookies; the WebView cookie store
            // still has them, and Zepp sometimes keeps the session there.
            val cookieJar = listOf(
                LOGIN_URL,
                "https://user.zepp.com/",
                "https://api-mifit-cn.huami.com/",
                "https://api-mifit-cn.zepp.com/"
            ).mapNotNull { url -> CookieManager.getInstance().getCookie(url) }
                .joinToString("; ")
            if (cookieJar.isNotBlank()) {
                parsed.put("cookie", parsed.optString("cookie") + "; " + cookieJar)
            }
            val found = com.genkaim.zeppglass.data.ZeppLogin.parse(parsed)
            if (found != null) {
                status = "已读到 Zepp 凭据，正在验证…"
                finish(found.appToken, found.userId, found.hostHint)
            } else if (announce && !busy) {
                status = "请在下方登录 Zepp 账号（邮箱或手机号 + 密码）"
            }
        }
    }

    // SPA 登录成功后常常不触发新的页面加载，只在 localStorage 里写入会话；
    // 仅靠 onPageFinished 会一直读不到 token，所以这里持续轮询。
    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1200)
            if (!busy && !manual) extract(view, announce = false)
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("连接 Zepp", style = MaterialTheme.typography.titleLarge)
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                Box(Modifier.weight(1f)) {
                    if (!manual) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    val ws = this.settings
                                    ws.javaScriptEnabled = true
                                    ws.domStorageEnabled = true
                                    ws.databaseEnabled = true
                                    ws.loadsImagesAutomatically = true
                                    ws.mediaPlaybackRequiresUserGesture = true
                                    // Third-party sign-in (Xiaomi, Google, Apple) opens a
                                    // popup. Keep the popup supported, but load its URL in
                                    // this same WebView so the session lands on the page we
                                    // poll for hm-user-login-info.
                                    ws.setSupportMultipleWindows(true)
                                    ws.javaScriptCanOpenWindowsAutomatically = true
                                    ws.userAgentString = ws.userAgentString.replace("; wv", "")
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: Bitmap?
                                        ) {
                                            status = "正在加载 ${url?.let(::hostOf) ?: "Zepp"}"
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            view?.let { extract(it, announce = true) }
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val target = request?.url ?: return false
                                            return if (target.scheme == "https" || target.scheme == "http") {
                                                false
                                            } else {
                                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, target)) }
                                                true
                                            }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCreateWindow(
                                            view: WebView?,
                                            isDialog: Boolean,
                                            isUserGesture: Boolean,
                                            resultMsg: Message
                                        ): Boolean {
                                            val popup = WebView(context)
                                            popup.settings.javaScriptEnabled = true
                                            popup.settings.domStorageEnabled = true
                                            val transport = resultMsg.obj as? WebView.WebViewTransport
                                                ?: return false
                                            popup.webViewClient = object : WebViewClient() {
                                                override fun onPageStarted(
                                                    p: WebView?,
                                                    url: String?,
                                                    icon: Bitmap?
                                                ) {
                                                    if (url != null && url.startsWith("http")) {
                                                        this@apply.loadUrl(url)
                                                        p?.destroy()
                                                    }
                                                }
                                            }
                                            transport.webView = popup
                                            resultMsg.sendToTarget()
                                            return true
                                        }
                                    }
                                    webView = this
                                    loadUrl(LOGIN_URL)
                                }
                            }
                        )
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .imePadding()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("手动填写 App Token", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "如果登录页一直空白，可以先在电脑上用 ZeppBridge 登录，然后把 apptoken 与 user id 填到这里。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = manualToken,
                                onValueChange = { manualToken = it },
                                label = { Text("apptoken") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = manualUserId,
                                onValueChange = { manualUserId = it },
                                label = { Text("user id") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = manualHost,
                                onValueChange = { manualHost = it },
                                label = { Text("区域接口地址") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (manualToken.isBlank() || manualUserId.isBlank()) {
                                        error = "apptoken 和 user id 都要填"
                                    } else {
                                        finish(manualToken.trim(), manualUserId.trim(), manualHost.trim())
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("验证并保存") }
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { manual = !manual }) {
                        Text(if (manual) "改用网页登录" else "手动填写 Token")
                    }
                    Spacer(Modifier.weight(1f))
                    if (!manual) {
                        TextButton(onClick = { webView?.let { extract(it, announce = true) } }) {
                            Text("重新读取")
                        }
                        TextButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("重新加载")
                        }
                    }
                }
            }
        }
    }
}

private fun hostOf(url: String): String = runCatching { Uri.parse(url).host }.getOrNull() ?: "Zepp"

private fun unquoteJson(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return runCatching {
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            JSONArray("[$raw]").getString(0)
        } else raw
    }.getOrNull()
}

private val HOSTS = listOf(
    "https://api-mifit-cn.huami.com",
    "https://api-mifit-cn2.huami.com",
    "https://api-mifit-cn.zepp.com",
    "https://api-mifit.huami.com",
    "https://api-mifit.zepp.com",
    "https://api-mifit-us.huami.com",
    "https://api-mifit-de.huami.com",
    "https://api-mifit-sg.huami.com"
)

private class Verified(
    val host: String,
    val nickname: String?,
    val avatar: String?
)

/**
 * Confirms the token against each candidate regional host and keeps the first
 * host that knows this user id. A rejected token stops the search at once.
 *
 * Nothing is written to settings here: the caller saves the session only after
 * a host has answered for this exact user id.
 */
private suspend fun verifyToken(
    settings: AppSettings,
    token: String,
    userId: String,
    hintedHost: String?
): Result<Verified> = withContext(Dispatchers.IO) {
    val candidates = buildList {
        hintedHost?.takeIf { it.startsWith("https://") }?.let(::add)
        addAll(HOSTS)
    }.distinct()

    var rejected = 0
    var lastError: Throwable? = null
    for (host in candidates) {
        try {
            val profile = ZeppApi(settings, overrideToken = token, overrideHost = host).profile(userId)
            val id = profile.optString("userId")
            if (id.isNotBlank() && id != userId) continue
            val nickname = profile.optString("nickName").ifBlank { null }
            val avatar = profile.optString("iconUrl").ifBlank { null }
            return@withContext Result.success(Verified(host, nickname, avatar))
        } catch (e: Exception) {
            lastError = e
            if (e is com.genkaim.zeppglass.data.ZeppException && e.code in listOf(401, 403)) {
                rejected++
                if (rejected >= (if (hintedHost != null) 2 else 3)) {
                    return@withContext Result.failure(Exception("Zepp 拒绝了这次登录凭据，请退出后重新登录"))
                }
            }
            delay(120)
        }
    }
    Result.failure(lastError ?: Exception("无法确认账号所在区域"))
}

