package com.genkaim.zeppglass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.genkaim.zeppglass.BuildConfig
import com.genkaim.zeppglass.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.genkaim.zeppglass.data.AiClient
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.data.SignOut
import com.genkaim.zeppglass.ui.components.CapsuleField
import com.genkaim.zeppglass.ui.components.FlatButton
import com.genkaim.zeppglass.ui.components.FlatPanel
import com.genkaim.zeppglass.ui.components.PinnedPageHeader
import com.genkaim.zeppglass.ui.components.SectionTitle
import com.genkaim.zeppglass.ui.sheets.CustomizeTabsSheet
import com.genkaim.zeppglass.ui.widget.YdooRingsWidget
import kotlinx.coroutines.launch

/**
 * Settings is a full page in its own Activity: account, appearance, tabs, the
 * AI endpoint and the about block. The bottom-bar card is itself the entry to
 * the tab editor; there is no separate button for it.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = remember { HealthStore.get(context, settings) }
    var showCustomize by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(0.dp) }

    var baseUrl by remember { mutableStateOf(settings.aiBaseUrl) }
    var apiKey by remember { mutableStateOf(settings.aiApiKey) }
    var model by remember { mutableStateOf(settings.aiModel) }
    var visionModel by remember { mutableStateOf(settings.aiVisionModel) }
    var headers by remember { mutableStateOf(settings.aiHeaders) }
    var aiOpen by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    // Xiaomi's own widget centre only lists widgets published through its
    // developer platform; a standard Android widget stays in the "安卓小部件"
    // section. The one-tap path that works on every launcher is the pin request.
    val widgetManager = remember { AppWidgetManager.getInstance(context) }
    val canPinWidget = remember { widgetManager?.isRequestPinAppWidgetSupported() == true }

    // The widget picks its palette from the app theme, not from the system, so a
    // theme change has to push a redraw; otherwise the card keeps the colours it
    // was last drawn with.
    fun refreshWidgetTheme() {
        Thread { YdooRingsWidget.updateAll(context) }.start()
    }

    val headerBackground = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Extra room under the last card so it does not sit against the
                // edge of the screen.
                .padding(start = 18.dp, end = 18.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(headerHeight + 4.dp))
            SectionTitle("Zepp 账号")
            FlatPanel {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (settings.isLoggedIn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (settings.isLoggedIn) "已连接 ${settings.nickname ?: "Zepp 账号"}" else "未连接",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                if (settings.isLoggedIn) "user ${settings.userId} · ${settings.regionHost.removePrefix("https://")}"
                                else "登录一次即可读取步数、睡眠、Charge 等数据",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlatButton(
                            text = if (settings.isLoggedIn) "重新登录" else "连接 Zepp",
                            onClick = onLogin,
                            modifier = Modifier.weight(1f)
                        )
                        if (settings.isLoggedIn) {
                            FlatButton(
                                text = "退出登录",
                                icon = Icons.Rounded.Logout,
                                primary = false,
                                // Only the web session ends here; the data stays
                                // filed under this account, so it still asks.
                                onClick = { confirmSignOut = true }
                            )
                        }
                    }
                }
            }

            // Title on the left, three icon-only keys on the right: the labels
            // were only repeating the icons, so they are gone from the row.
            // Same band height as the 自定义底栏 card below: its two lines of
            // text plus 16 dp padding is what sets the rhythm here.
            FlatPanel {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "颜色主题",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChoice(
                            icon = Icons.Rounded.BrightnessAuto,
                            label = "跟随系统",
                            selected = settings.themeMode == "system"
                        ) {
                            settings.updateTheme("system")
                            refreshWidgetTheme()
                        }
                        ThemeChoice(
                            icon = Icons.Rounded.LightMode,
                            label = "浅色",
                            selected = settings.themeMode == "light"
                        ) {
                            settings.updateTheme("light")
                            refreshWidgetTheme()
                        }
                        ThemeChoice(
                            icon = Icons.Rounded.DarkMode,
                            label = "深色",
                            selected = settings.themeMode == "dark"
                        ) {
                            settings.updateTheme("dark")
                            refreshWidgetTheme()
                        }
                    }
                }
            }

            SectionTitle("底栏")
            // The whole card is the button: tapping anywhere opens the editor.
            FlatPanel(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                onClick = { showCustomize = true }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("自定义底栏", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "排序与显示 / 隐藏，长按底栏也能进入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(-90f)
                    )
                }
            }

            SectionTitle("桌面小组件")
            FlatPanel {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Widgets, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "今日概览",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable {
                                if (canPinWidget) {
                                    runCatching {
                                        widgetManager?.requestPinAppWidget(
                                            ComponentName(context, YdooRingsWidget::class.java),
                                            null,
                                            null
                                        )
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    "已经添加，若没看见可手动在桌面小部件 → 安卓小部件添加",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "添加到桌面",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            SectionTitle("AI 接入点")
            // The header is the entry point now: the fields live in a dialog, so
            // this page keeps one card instead of growing a long form inline.
            FlatPanel(onClick = { aiOpen = true }) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("AI 接入点", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (settings.aiApiKey.isBlank()) "未配置" else settings.aiBaseUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (aiOpen) {
                Dialog(
                    onDismissRequest = { aiOpen = false },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .imePadding()
                            .padding(horizontal = 18.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FlatPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                            // Scrolls when the keyboard leaves too little room,
                            // so no field is ever unreachable.
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "AI 接入点",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                FieldLabel("Base URL")
                                CapsuleField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    placeholder = "https://api.openai.com/v1"
                                )
                                FieldLabel("API Key")
                                CapsuleField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    placeholder = "sk-…",
                                    visualTransformation = PasswordVisualTransformation()
                                )
                                FieldLabel("模型")
                                CapsuleField(
                                    value = model,
                                    onValueChange = { model = it },
                                    placeholder = "deepseek-v4.1-flash"
                                )
                                FieldLabel("识图模型（可留空）")
                                CapsuleField(
                                    value = visionModel,
                                    onValueChange = { visionModel = it },
                                    placeholder = "gpt-4o / gemini-2.0-flash / qwen-vl"
                                )
                                Text(
                                    "拍照识别食物时用这个模型；留空就沿用上面的模型",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FieldLabel("自定义请求头（每行 Key: Value）")
                                CapsuleField(
                                    value = headers,
                                    onValueChange = { headers = it },
                                    placeholder = "x-opencode-session: …",
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 4
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FlatButton(
                                        text = if (testing) "测试中…" else "测试连接",
                                        onClick = {
                                            settings.saveAi(
                                                baseUrl, apiKey, model,
                                                visionModel = visionModel, headers = headers
                                            )
                                            testing = true
                                            testResult = null
                                            scope.launch {
                                                val result = AiClient(settings).test()
                                                testing = false
                                                testOk = result.isSuccess
                                                testResult = result.fold(
                                                    onSuccess = { it },
                                                    onFailure = { it.message ?: "连接失败" }
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        primary = false,
                                        enabled = !testing
                                    )
                                    FlatButton(
                                        text = "保存",
                                        onClick = {
                                            settings.saveAi(
                                                baseUrl, apiKey, model,
                                                visionModel = visionModel, headers = headers
                                            )
                                            aiOpen = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                testResult?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (testOk) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    "兼容 OpenAI Chat Completions 格式的接入点。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle("关于")
            FlatPanel(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                onClick = onOpenAbout
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF2F2F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.brand_mark),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("关于 ydoo", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${BuildConfig.VERSION_NAME} · 数据来源、隐私与开源引用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Pinned header with the same fade as the other pages.
        PinnedPageHeader(
            modifier = Modifier.align(Alignment.TopCenter),
            onHeight = { headerHeight = it }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                }
                Text("设置", style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    if (showCustomize) {
        CustomizeTabsSheet(settings = settings, onDismiss = { showCustomize = false })
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("退出登录？") },
            confirmButton = {
                TextButton(onClick = {
                    SignOut.run(context, settings, store)
                    confirmSignOut = false
                    testOk = true
                    testResult = "已退出登录"
                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AboutRow(title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.size(width = 72.dp, height = 20.dp)
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/** One open-source reference: name, what it does, and its licence. */
@Composable
private fun ReferenceRow(name: String, body: String, licence: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            licence,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
    )
}

/**
 * Appearance switch: one circular icon per mode. The selected key is filled
 * with the accent colour; the others are muted fills with no outline at all.
 * The mode name lives in the content description, not in a caption.
 */
@Composable
private fun ThemeChoice(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}







