package com.genkaim.zeppglass.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.AiChatSession
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.components.EmptyCard
import com.genkaim.zeppglass.ui.components.MarkdownView
import com.genkaim.zeppglass.ui.components.PageHeader
import com.genkaim.zeppglass.ui.components.RoundIconButton
import com.genkaim.zeppglass.ui.components.Rule
import com.genkaim.zeppglass.ui.components.TypingDots
import kotlinx.coroutines.launch

/**
 * Floating tab bar (76 dp) plus its own outer margin and a little air. The old
 * 74 dp left the last two dp of the composer tucked under the bar.
 */
private val TabBarClearance = 86.dp

/**
 * Chat page, laid out like a mainstream assistant client: a fixed title bar on
 * top, one scrolling transcript, and a composer that lives in the page flow at
 * the bottom instead of floating over the list.
 *
 * The transcript follows the newest text while an answer streams in, stops the
 * moment the reader drags upward, and offers a jump-to-bottom button afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiScreen(
    settings: AppSettings,
    store: HealthStore,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = AiChatSession.messages
    val sending = AiChatSession.sending
    val phase = AiChatSession.phase
    val input = AiChatSession.draft
    val listState = rememberLazyListState()
    val configured = settings.aiApiKey.isNotBlank() && settings.aiBaseUrl.isNotBlank()
    // The AI reads the raw snapshot: the three-hour rule is about what the UI
    // presents as "now", not about which rows the model may examine.
    val data = store.state.data

    var showHistory by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Attach once, then refresh the starter questions whenever the data changes
    // or 新对话 emptied them.
    LaunchedEffect(settings.aiApiKey, settings.aiBaseUrl, data?.fetchedAt) {
        AiChatSession.attach(context)
        AiChatSession.loadSuggestions(settings, data)
    }

    // ---- following the bottom -------------------------------------------
    // Dragging up during streaming must leave the page where the finger left
    // it; anything else pulls the reader back down on every chunk.
    var followBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (scrolling) followBottom = !canScrollForward
            }
    }
    LaunchedEffect(AiChatSession.currentId) { followBottom = true }

    // A new message jumps to its top; the layout observer below then pins its
    // bottom edge. Doing both in one pass computed the overflow from the frame
    // before the new text was measured, which is what made the list flicker
    // while a long answer streamed in.
    LaunchedEffect(messages.size) {
        if (!followBottom || messages.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(messages.lastIndex)
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            Triple(
                last?.index ?: -1,
                last?.let { it.offset + it.size } ?: 0,
                info.viewportEndOffset - info.afterContentPadding
            )
        }.collect { (index, bottom, viewportEnd) ->
            if (!followBottom) return@collect
            if (index != messages.lastIndex) return@collect
            val overflow = bottom - viewportEnd
            if (overflow > 0) listState.scrollBy(overflow.toFloat())
        }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || AiChatSession.sending) return
        if (!configured) {
            onOpenSettings()
            return
        }
        followBottom = true
        AiChatSession.send(settings, prompt, data)
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("AI 回答", text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    // The keyboard replaces the tab-bar gap: while typing the composer sits just
    // above the IME, and the floating tab bar is out of the way.
    val imeVisible = WindowInsets.isImeVisible
    // Union, never a sum: the IME inset already covers the navigation bar, so
    // padding both leaves a gap under the composer and makes it jump when the
    // keyboard goes away. One value serves both states.
    val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)
    val bottomClearance by animateDpAsState(
        targetValue = if (imeVisible) 8.dp else TabBarClearance,
        animationSpec = tween(180),
        label = "composerGap"
    )

    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(bottomInsets)
    ) {
        PageHeader(
            title = "AI 教练",
            subtitle = if (configured) settings.aiModel.ifBlank { "默认模型" } else "未接入",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { AiChatSession.newChat() }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建对话", modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Rounded.History, contentDescription = "历史对话", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "AI 设置", modifier = Modifier.size(20.dp))
                    }
                }
            }
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                EmptyTranscript(
                    configured = configured,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                    // A short conversation sits at the bottom of the page, the
                    // way a chat should: sending grows it upward.
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom)
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { index, message -> "$index-${message.role}" }
                    ) { index, message ->
                        val streamingHere = sending && index == messages.lastIndex && message.role == "assistant"
                        if (message.role == "user") {
                            UserBubble(message.content)
                        } else {
                            AssistantMessage(
                                message = message,
                                streaming = streamingHere,
                                phase = if (streamingHere) phase else "",
                                data = data,
                                canRegenerate = !sending && index == messages.lastIndex,
                                onRetry = { AiChatSession.retry(settings, data) },
                                onRegenerate = { AiChatSession.regenerate(settings, data) },
                                onCopy = { copy(message.content) }
                            )
                        }
                    }
                }

                JumpToBottom(
                    visible = !followBottom && messages.isNotEmpty(),
                    onClick = {
                        followBottom = true
                        scope.launch {
                            listState.animateScrollToItem(messages.lastIndex)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                )
            }
        }

        // Starter questions belong next to the composer: they are shortcuts for
        // what to type, not page content to scroll past.
        if (messages.isEmpty() && configured) {
            SuggestionStrip(
                suggestions = AiChatSession.suggestions.ifEmpty { AiChatSession.defaultPrompts },
                loading = AiChatSession.suggestionsLoading,
                onPick = { send(it) }
            )
        }

        Composer(
            value = input,
            onValueChange = { AiChatSession.draft = it },
            sending = sending,
            enabled = configured,
            onSend = { send(input) },
            onStop = { AiChatSession.stop() },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
        )
        Spacer(Modifier.height(bottomClearance))
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
 * Shown once the reader has scrolled away from the newest text. It lives in its
 * own composable so [AnimatedVisibility] resolves to the plain version instead
 * of the ColumnScope one inherited from the page column.
 */
@Composable
private fun JumpToBottom(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.85f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    contentDescription = "回到最新",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Composer: one muted capsule holding the growing text field and the send key.
 * While an answer streams the same key becomes a stop button.
 */
@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = value.isNotBlank() && !sending
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                if (enabled) "问问训练、恢复或饮食…" else "先填写 AI 接入点",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            )
            if (sending) {
                RoundIconButton(
                    icon = Icons.Rounded.Stop,
                    contentDescription = "停止",
                    onClick = onStop,
                    primary = true,
                    size = 40.dp,
                    iconSize = 18.dp
                )
            } else {
                RoundIconButton(
                    icon = Icons.Rounded.ArrowUpward,
                    contentDescription = "发送",
                    onClick = { if (canSend) onSend() },
                    enabled = canSend,
                    primary = canSend,
                    size = 40.dp,
                    iconSize = 18.dp
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Answers are page text, not a bubble: full width, so long paragraphs use the
 * whole line, with the copy / regenerate pair underneath once the reply is done.
 */
@Composable
private fun AssistantMessage(
    message: AiChatSession.Message,
    streaming: Boolean,
    phase: String,
    data: HealthData?,
    canRegenerate: Boolean,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        when {
            message.error -> {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("重试")
                    }
                }
            }

            message.content.isBlank() && streaming -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypingDots(Modifier.padding(vertical = 8.dp))
                    if (phase.isNotBlank()) {
                        Spacer(Modifier.size(10.dp))
                        Text(
                            phase,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                MarkdownView(message.content, data = data)
                if (streaming) {
                    TypingDots(Modifier.padding(top = 8.dp))
                } else {
                    Row(
                        Modifier.padding(top = 2.dp, start = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MessageAction("复制", Icons.Rounded.ContentCopy, onCopy)
                        if (canRegenerate) {
                            MessageAction("重新生成", Icons.Rounded.Autorenew, onRegenerate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Nothing said yet: the greeting sits in the middle of the empty transcript,
 * not pinned to the top of the page.
 */
@Composable
private fun EmptyTranscript(
    configured: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!configured) {
            EmptyCard(
                title = "先填入自己的 AI 接入点",
                body = "在设置里填兼容 OpenAI 的地址、密钥和模型。密钥只保存在本机，不会发给 Zepp。",
                modifier = Modifier.padding(horizontal = 20.dp),
                actionLabel = "去设置",
                onAction = onOpenSettings
            )
        } else {
            Column(
                Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "今天想聊点什么？",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "回答会直接引用本机的睡眠、负荷与饮食记录；点下面的问题开始，或者自己输入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Starter questions as one horizontally scrolling row of chips, parked right
 * above the composer where the next tap goes.
 */
@Composable
private fun SuggestionStrip(
    suggestions: List<String>,
    loading: Boolean,
    onPick: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(suggestions) { text ->
                Surface(
                    onClick = { onPick(text) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text,
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (loading) {
            Text(
                "正在根据最近的数据准备问题…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp)
            )
        }
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
                                .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { onOpen(session.id) },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(vertical = 2.dp)) {
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
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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
