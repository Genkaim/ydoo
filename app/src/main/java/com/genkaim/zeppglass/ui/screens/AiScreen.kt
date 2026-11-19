package com.genkaim.zeppglass.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.AiChatSession
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.components.EmptyCard
import com.genkaim.zeppglass.ui.components.MarkdownView
import com.genkaim.zeppglass.ui.components.PageHeader
import com.genkaim.zeppglass.ui.components.PinnedPageHeader
import com.genkaim.zeppglass.ui.components.Rule
import com.genkaim.zeppglass.ui.components.TypingDots
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiScreen(
    settings: AppSettings,
    store: HealthStore,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val input = AiChatSession.draft
    val messages = AiChatSession.messages
    val sending = AiChatSession.sending
    val phase = AiChatSession.phase
    val listState = rememberLazyListState()
    val configured = settings.aiApiKey.isNotBlank()
    // The AI reads the raw snapshot: the three-hour rule is about what the UI
    // presents as "now", not about which rows the model may examine.
    val data = store.state.data

    // The composer keeps one stable gap below the list: the floating tab bar's
    // height when the keyboard is closed, a small gap above the keyboard when it
    // is open. Deriving it from live insets made the first frame land under the
    // bar and then jump up; animating it on close dipped behind the bar again.
    val density = LocalDensity.current
    // Keep the capsule at least 84dp above the bar. As the keyboard rises, the
    // gap shrinks by exactly the IME height, so the capsule follows the keyboard
    // continuously and never dips behind the tab bar on close.
    val imeHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    // Keep at least 10dp above the keyboard so the capsule never touches it.
    val composerGap = maxOf(10.dp, 84.dp - imeHeight)

    var showHistory by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Keep following the newest content only while the reader is at the bottom.
    // Dragging up during streaming must leave the page where the finger left it,
    // otherwise every chunk would pull the list back down.
    var followBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScroll) ->
                if (scrolling) followBottom = !canScroll
            }
    }
    LaunchedEffect(AiChatSession.currentId) { followBottom = true }

    // Attach once, then (re)generate the starter questions whenever the list is
    // empty — the first visit, and right after 新对话 clears them — or when a
    // fresh data snapshot arrives after an earlier attempt failed.
    LaunchedEffect(settings.aiApiKey, AiChatSession.suggestions.isEmpty(), data?.fetchedAt) {
        AiChatSession.attach(context)
        AiChatSession.loadSuggestions(settings, data)
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!followBottom) return@LaunchedEffect
        // Pin the newest message's bottom edge to the viewport. Scrolling to
        // the item alone aligns its top, which overshoots for a long answer and
        // made the list flash while streaming.
        listState.scrollToItem(messages.lastIndex)
        val last = listState.layoutInfo.visibleItemsInfo
            .lastOrNull { it.index == messages.lastIndex } ?: return@LaunchedEffect
        val end = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
        val overflow = last.offset + last.size - end
        if (overflow > 0) listState.scrollBy(overflow.toFloat())
    }

    fun send(text: String) {
        if (text.isBlank() || AiChatSession.sending) return
        if (!configured) {
            onOpenSettings()
            return
        }
        // A fresh question always lands at the bottom, even if the reader had
        // scrolled up in the previous answer.
        followBottom = true
        AiChatSession.send(settings, text, data)
    }

    var headerHeight by remember { mutableStateOf(0.dp) }
    Box(
        modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = headerHeight + 8.dp,
                bottom = composerGap + 84.dp
            ),
            verticalArrangement = if (messages.isEmpty()) {
                Arrangement.spacedBy(14.dp)
            } else {
                // A short conversation starts at the bottom of the page, like a
                // chat should: sending a message grows it upward instead of
                // leaving the newest bubble in the middle of an empty screen.
                Arrangement.spacedBy(14.dp, Alignment.Bottom)
            }
        ) {
            if (!configured) {
                item {
                    EmptyCard(
                        title = "先填入自己的 AI 密钥",
                        body = "在设置里填兼容 OpenAI 的接入点、密钥和模型。密钥只保存在本机，不会发给 Zepp。",
                        actionLabel = "去设置",
                        onAction = onOpenSettings
                    )
                }
            }
            item {
                AnimatedVisibility(
                    visible = messages.isEmpty(),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
                ) {
                    IntroCard(
                        suggestions = AiChatSession.suggestions.ifEmpty {
                            listOf(
                                "今天适合练什么？",
                                "解释一下我的准备度",
                                "睡眠哪里可以改善？",
                                "这周负荷是不是偏高？"
                            )
                        },
                        onPick = { send(it) }
                    )
                }
            }
            items(messages.size, key = { index -> "$index-${messages[index].role}" }) { index ->
                val message = messages[index]
                val streamingHere = sending && index == messages.lastIndex && message.role == "assistant"
                BubbleAppear(animate = sending && index >= messages.lastIndex - 1) {
                    if (message.role == "user") {
                        UserBubble(message.content)
                    } else {
                        AssistantBubble(
                            content = message.content,
                            error = message.error,
                            streaming = streamingHere,
                            phase = if (streamingHere) phase else "",
                            data = data,
                            onRetry = {
                                AiChatSession.retry(settings, data)
                            }
                        )
                    }
                }
            }
        }
        }

        PinnedPageHeader(onHeight = { headerHeight = it }) {
            PageHeader(
                title = "AI 教练",
                subtitle = settings.aiModel.ifBlank { "未配置模型" },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { AiChatSession.newChat() }) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "新建对话",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { showHistory = true }) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = "历史对话",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "AI 设置", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = composerGap),
            verticalAlignment = Alignment.Bottom
        ) {
            ComposerField(
                value = input,
                onValueChange = { AiChatSession.draft = it },
                sending = sending,
                onSend = { send(input) },
                onStop = { AiChatSession.stop() },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            HistorySheet(
                sessions = AiChatSession.sessions,
                currentId = AiChatSession.currentId,
                onOpen = {
                    AiChatSession.open(it)
                    showHistory = false
                },
                onDelete = { AiChatSession.delete(it) },
                onNew = {
                    AiChatSession.newChat()
                    showHistory = false
                }
            )
        }
    }
}

/**
 * The composer is a white capsule on the page background. Its inner padding is
 * deliberately larger than a stock OutlinedTextField, and the area outside the
 * capsule stays transparent.
 */
@Composable
private fun ComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.shadow(
            elevation = 10.dp,
            shape = RoundedCornerShape(28.dp),
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.12f),
            spotColor = Color.Black.copy(alpha = 0.18f)
        ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "问问今天的训练或恢复…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
            )
            if (sending) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = "停止", modifier = Modifier.size(20.dp))
                }
            } else {
                IconButton(onClick = onSend, enabled = value.isNotBlank()) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = "发送",
                        modifier = Modifier.size(20.dp),
                        tint = if (value.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Fades and lifts a bubble in when it first appears. Only bubbles that arrive
 * while the chat is sending animate; opening an old conversation draws them
 * immediately, so switching tabs never replays the animation.
 */
@Composable
private fun BubbleAppear(animate: Boolean, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { if (animate) visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { height -> height / 5 }
    ) {
        content()
    }
}

/** Fades and lifts a bubble in when it first appears. */@Composable
private fun MessageAppear(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { height -> height / 5 }
    ) {
        content()
    }
}

@Composable
private fun HistorySheet(
    sessions: List<AiChatSession.Session>,
    currentId: String?,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit
) {
    Column(Modifier.fillMaxHeight(0.72f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "历史对话",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onNew) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("新对话")
            }
        }
        if (sessions.isEmpty()) {
            Text(
                "还没有历史对话。发出的第一条消息会自动存到这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(sessions, key = { it.id }) { session ->
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(session.id) }
                                .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (session.id == currentId) FontWeight.SemiBold
                                    else FontWeight.Normal,
                                    maxLines = 1
                                )
                                Text(
                                    "${Fmt.dayAndTime(session.updatedAt)} · ${session.count} 条" +
                                        if (session.id == currentId) " · 当前" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (session.preview.isNotBlank()) {
                                    Text(
                                        session.preview,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(session.id) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除对话",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Rule(Modifier.padding(start = 20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IntroCard(suggestions: List<String>, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("问点什么", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        // The list is replaced once the model's own four questions arrive, so
        // cross-fade instead of snapping.
        AnimatedContent(
            targetState = suggestions,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(140)) },
            label = "suggestions"
        ) { list ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEach { text ->
                    Surface(
                        onClick = { onPick(text) },
                        shape = RoundedCornerShape(10.dp),
                        // Light enough to read as a hint, not as a filled button.
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Text(
                            text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AssistantBubble(
    content: String,
    error: Boolean,
    streaming: Boolean,
    phase: String,
    data: com.genkaim.zeppglass.data.HealthData?,
    onRetry: () -> Unit
) {
    // Answers are page text, not a bubble: no surface, no inset box, full width
    // of the list so long paragraphs use the whole line.
    Column(Modifier.fillMaxWidth()) {
        if (content.isBlank() && streaming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypingDots(Modifier.padding(vertical = 6.dp))
                if (phase.isNotBlank()) {
                    Spacer(Modifier.size(10.dp))
                    Text(
                        phase,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            MarkdownView(content, data = data)
            if (streaming) {
                TypingDots(Modifier.padding(top = 8.dp))
            }
        }
        if (error) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("重试")
                }
            }
        }
    }
}


