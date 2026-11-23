package com.genkaim.zeppglass.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.genkaim.zeppglass.data.AiChatSession
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.components.FlatPanel
import com.genkaim.zeppglass.ui.components.LocalTabBarSpace
import com.genkaim.zeppglass.ui.components.MarkdownView
import com.genkaim.zeppglass.ui.components.PageHeader
import com.genkaim.zeppglass.ui.components.PinnedPageHeader
import com.genkaim.zeppglass.ui.components.RoundIconButton
import com.genkaim.zeppglass.ui.components.TypingDots
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import com.genkaim.zeppglass.ui.theme.popupCardColor
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Floating tab bar footprint, used when the shell has not measured itself yet.
 * The real value comes from [LocalTabBarSpace].
 */
private val FallbackBarSpace = 86.dp

/**
 * Air between the composer and whatever is below it: the floating bar when the
 * keyboard is down, the top edge of the keyboard when it is up.
 */
private val ComposerAir = 8.dp


/**
 * Chat page, laid out like a mainstream assistant client: a fixed title bar on
 * top, one scrolling transcript, and a composer that lives in the page flow at
 * the bottom instead of floating over the list.
 *
 * The transcript follows the newest text while an answer streams in, stops the
 * moment the reader drags upward, and offers a jump-to-bottom button afterwards.
 */
/**
 * The one conversation's scroll position, remembered across tab switches.
 *
 * Only the last one is kept: returning to the tab restores it, and any other
 * session (history, a new chat) deliberately starts at its bottom.
 */
private var lastScroll: Triple<String?, Int, Int>? = null

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
    val sessionId = AiChatSession.currentId
    // Where the conversation was left when the tab was switched away from. Kept
    // for one conversation only: coming back to the tab puts the page exactly
    // where it was, while opening a conversation from history starts at the
    // newest text, as a chat should.
    val listState = remember(sessionId) {
        val memory = lastScroll
        if (memory != null && memory.first == sessionId) {
            LazyListState(memory.second, memory.third)
        } else {
            // A conversation that was not on screen before opens on its newest
            // message, measured straight away instead of scrolling after the
            // first frame.
            LazyListState(messages.lastIndex.coerceAtLeast(0))
        }
    }
    val restoredScroll = remember(sessionId) { lastScroll?.first == sessionId }
    DisposableEffect(sessionId) {
        onDispose {
            lastScroll = Triple(
                sessionId,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }
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

    // ---- bottom spacing ---------------------------------------------------
    // The composer has to clear two different things: the floating tab bar
    // while the keyboard is down, and the keyboard itself while it is up.
    // Its position follows the live IME inset, not a "keyboard is open" flag:
    // the flag only flips when the keyboard has finished moving, so the field
    // used to stand still until the keyboard was gone and then jump home.
    // Reading the inset means it travels with the keyboard, both ways.
    val density = LocalDensity.current
    val imePx = with(density) { WindowInsets.ime.getBottom(this) }
    val navBarPx = with(density) { WindowInsets.navigationBars.getBottom(this) }
    val imeVisible = WindowInsets.isImeVisible
    // The keyboard keeps its height for the whole session; remembering the
    // tallest value keeps the travel distance stable once the inset is back at
    // zero.
    var tallestImePx by remember { mutableIntStateOf(0) }
    // A few builds never move the IME inset (older ROMs, and AVDs without a
    // SystemUI): there the flag drives the animation instead.
    var insetTravels by remember { mutableStateOf(false) }
    SideEffect {
        if (imePx > tallestImePx) tallestImePx = imePx
        if (imePx > navBarPx + 1) insetTravels = true
    }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    // A few builds report no IME inset at all (the window resizes instead).
    // A rough keyboard height beats hiding the field behind the keyboard.
    val keyboardPx = tallestImePx.takeIf { it > 0 }
        ?: with(density) { (screenHeightDp * 0.40f).dp.roundToPx() }
    val keyboardUp = imeVisible || imePx > navBarPx
    // Fallback for the builds that never animate the inset.
    val fallbackTyping by animateFloatAsState(
        targetValue = if (keyboardUp) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "typing"
    )
    val travelPx = (keyboardPx - navBarPx).coerceAtLeast(1)
    val insetTyping = ((imePx - navBarPx).toFloat() / travelPx).coerceIn(0f, 1f)
    val typing = if (insetTravels) insetTyping else fallbackTyping
    // True only while the keyboard is between the two ends.
    val keyboardMoving = rememberUpdatedState(typing > 0.01f && typing < 0.99f)
    // Measured by the shell, so the field clears the bar on every device no
    // matter which system-bar insets it reports.
    val barSpace = LocalTabBarSpace.current.takeIf { it > 0.dp } ?: FallbackBarSpace
    val bottomSpace = lerp(
        start = barSpace + ComposerAir,
        stop = with(density) { keyboardPx.toDp() } + ComposerAir,
        fraction = typing
    )

    // True while the deck (composer + the air under it) is changing size; those
    // frames keep the list attached instead of animating it.
    var trackingDeck by remember { mutableStateOf(false) }
    val trackingNow = rememberUpdatedState(trackingDeck)

    // ---- following the bottom -------------------------------------------
    // Dragging up during streaming must leave the page where the finger left
    // it; anything else pulls the reader back down on every chunk.
    // Switching conversations is immediate: the words change on the frame the
    // drawer's entry is tapped, and the drawer slides away beside them. A mask
    // or a fade in front of this only delayed the page the reader asked for.

    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    /**
     * The IME lives in its own window, above every app window, and the drawer's
     * dialog window never receives an IME inset — padding cannot lift it clear.
     *
     * One request only: the window's inset controller, which starts the system
     * hide animation on this frame. Focus is left alone here so the platform has
     * nothing to undo and the keyboard cannot come back mid-animation.
     */
    fun hideKeyboard() {
        val window = findActivity(view.context)?.window ?: return
        runCatching {
            WindowCompat.getInsetsController(window, view)
                .hide(WindowInsetsCompat.Type.ime())
        }
    }

    // The drawer is painted in a dialog window, which the IME always covers, so a
    // tap only asks the IME to leave and the sheet is shown once it is really
    // gone. With the field unfocused that is the same frame, so history still
    // opens on the tap.
    var historyPending by remember { mutableStateOf(false) }
    LaunchedEffect(historyPending, imeVisible) {
        if (!historyPending || imeVisible) return@LaunchedEffect
        // Nothing left to hide: the field may as well let go of focus too, so it
        // does not pull the IME back when the drawer closes.
        focusManager.clearFocus(force = true)
        showHistory = true
        historyPending = false
    }
    LaunchedEffect(historyPending) {
        if (!historyPending) return@LaunchedEffect
        // A ROM that never reports the IME as gone must not eat the tap.
        kotlinx.coroutines.delay(400)
        if (historyPending) {
            focusManager.clearFocus(force = true)
            showHistory = true
            historyPending = false
        }
    }

    var followBottom by remember { mutableStateOf(true) }
    // The follow animation scrolls the list itself. Without this flag the
    // gesture observer below would read that as a drag and stop following.
    var autoScroll by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (scrolling && !autoScroll) followBottom = !canScrollForward
            }
    }
    LaunchedEffect(AiChatSession.currentId) { followBottom = true }

    // A new message jumps to its top; the layout observer below then pins its
    // bottom edge. Doing both in one pass computed the overflow from the frame
    // before the new text was measured, which is what made the list flicker
    // while a long answer streamed in.
    // The first pass lands without animation: a restored page must not move, and
    // a conversation opened from history must simply be at its bottom, not
    // slide there. The follow below is eased only from the second frame on.
    var firstPass by remember(sessionId) { mutableStateOf(true) }
    var followAnimated by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(sessionId) {
        withFrameNanos { }
        withFrameNanos { }
        followAnimated = true
    }
    LaunchedEffect(messages.size) {
        val landing = firstPass
        firstPass = false
        if (landing && restoredScroll) return@LaunchedEffect
        if (!followBottom || messages.isEmpty()) return@LaunchedEffect
        autoScroll = true
        try {
            listState.scrollToItem(messages.lastIndex)
        } finally {
            autoScroll = false
        }
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
            // A streamed answer grows in blocks every few tens of milliseconds.
            // Jumping the list by the overflow lifted the page in a single
            // frame; animating it keeps the text rising instead of snapping up.
            if (overflow > 1) {
                autoScroll = true
                try {
                    // While the keyboard is on its way the field itself is moving;
                    // animating the list as well made it arrive in two or three
                    // separate steps. Those frames just keep the list attached.
                    if (followAnimated && !keyboardMoving.value && !trackingNow.value) {
                        listState.animateScrollBy(
                            overflow.toFloat(),
                            // Eased at both ends: the text rises, settles, and
                            // rises again with the next block instead of ticking up.
                            tween(durationMillis = 220, easing = FastOutSlowInEasing)
                        )
                    } else {
                        listState.scrollBy(overflow.toFloat())
                    }
                } finally {
                    autoScroll = false
                }
            }
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


    // Everything already on screen when the page opens is simply there; only
    // messages that arrive while it is open slide up into place. The baseline
    // is rebuilt when a whole conversation is swapped in, and shrinks when the
    // list is emptied (new chat, deleted session).
    var settled by remember(AiChatSession.sessionEpoch) { mutableIntStateOf(messages.size) }
    LaunchedEffect(messages.size) { if (messages.size < settled) settled = messages.size }
    // Which rows have already played their entrance. Without this a row that
    // scrolls out of view and back would fade in again.
    val playedEntrance = remember(AiChatSession.sessionEpoch) { mutableSetOf<String>() }

    // The header floats over the transcript, the way every other page does it.
    // Its gradient needs something travelling underneath to be visible at all;
    // when the header sat in the column flow its lower half simply faded into
    // the same page colour and the fade could not be seen.
    var headerHeight by remember { mutableStateOf(0.dp) }

    Box(
        modifier
            .fillMaxSize()
    ) {
        // The transcript runs to the bottom edge of the window and the deck
        // (suggestions, composer, the gap under them) floats over it. Nothing
        // paints a band behind the deck any more, so while the list scrolls its
        // messages stay visible in the margin around the capsules. The list also
        // runs under the header — only its first rows are pushed below it — so
        // the header's fade has text travelling underneath it.
        Box(
            Modifier
                .fillMaxSize()
        ) {
            // Seeded with an estimate of the deck (composer + the bar's space):
            // starting at zero made the list sit lower for one frame, then jump
            // up when the composer was measured — that was the "messages fly and
            // come back" on entering a conversation.
            var deckPx by remember {
                mutableIntStateOf(with(density) { (bottomSpace + 68.dp).roundToPx() })
            }
            val deckSpace = with(density) { deckPx.toDp() }
            // A deck jump — the keyboard rising, the suggestion strip folding
            // away — is followed by layout, not by a tween. Animating those
            // frames on top of each other is what made the page climb in several
            // separate pieces; only the quiet growth of a reply animates.
            var lastDeckDp by remember { mutableStateOf(deckSpace) }
            LaunchedEffect(deckSpace) {
                if (abs((deckSpace - lastDeckDp).value) > 6f) {
                    trackingDeck = true
                    kotlinx.coroutines.delay(280)
                    trackingDeck = false
                }
                lastDeckDp = deckSpace
            }

            // Greeting and transcript cross-fade instead of swapping in one
            // frame, so the first message lands while the empty state is still
            // leaving instead of cutting it off.
            FadeLayer(visible = messages.isEmpty()) {
                EmptyTranscript(
                    configured = configured,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = headerHeight, bottom = deckSpace)
                )
            }

            FadeLayer(visible = messages.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = headerHeight + 4.dp,
                        bottom = deckSpace + 8.dp
                    ),
                    // A short conversation sits at the bottom of the page, the
                    // way a chat should: sending grows it upward.
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom)
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { index, message -> "$index-${message.role}" }
                    ) { index, message ->
                        val streamingHere = sending && index == messages.lastIndex && message.role == "assistant"
                        // A row gets its entrance exactly once. The answer waits
                        // a beat after the question so the pair does not climb
                        // in unison while the suggestions are still folding up.
                        val rowKey = "$index-${message.role}"
                        val fresh = index >= settled && playedEntrance.add(rowKey)
                        AppearFromBelow(
                            animate = fresh,
                            delayMillis = if (fresh && message.role == "assistant") 70 else 0
                        ) {
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
                    .padding(bottom = deckSpace + 6.dp)
            )

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Starter questions belong next to the composer: they are
                // shortcuts for what to type, not page content to scroll past.
                // They rise out of the composer and are drawn back up into it,
                // so nothing travels downward while the first message lands.
                AnimatedVisibility(
                    visible = messages.isEmpty() && configured,
                    enter = fadeIn(tween(160)) +
                        expandVertically(tween(240, easing = FastOutSlowInEasing), expandFrom = Alignment.Bottom),
                    exit = fadeOut(tween(120)) +
                        shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
                ) {
                    SuggestionStrip(
                        suggestions = AiChatSession.suggestions.ifEmpty { AiChatSession.defaultPrompts },
                        loading = AiChatSession.suggestionsLoading,
                        onPick = { send(it) },
                        onRefresh = { AiChatSession.loadSuggestions(settings, data, force = true) }
                    )
                }

                // Only the composer and the air under it are measured. The
                // suggestion row folds away on its own: letting it feed the
                // measured height would shrink the transcript's bottom padding
                // frame by frame and drag the list down while the new bubble is
                // still rising — the two animations fighting each other.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { deckPx = it.height }
                ) {
                    Composer(
                        value = input,
                        onValueChange = { AiChatSession.draft = it },
                        sending = sending,
                        enabled = configured,
                        onSend = { send(input) },
                        onStop = { AiChatSession.stop() },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
                    )
                    Spacer(Modifier.height(bottomSpace))
                }
            }
        }

        PinnedPageHeader(
            modifier = Modifier.align(Alignment.TopCenter),
            onHeight = { headerHeight = it },
            // The AI page has no title line, so its fade stops earlier than the
            // other pages': a long tail over the transcript washed out the first
            // message.
            tail = 8.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { AiChatSession.newChat() }) {
                    Icon(Icons.Rounded.Add, contentDescription = "新建对话", modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = {
                        // Ask the IME down now; the sheet follows once it is out
                        // of the way (usually the next few frames).
                        hideKeyboard()
                        historyPending = true
                    }
                ) {
                    Icon(Icons.Rounded.History, contentDescription = "历史对话", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "AI 设置", modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = sheetState,
            // The drawer is a raised panel, not a hole in the page: the same
            // lighter card tone the app's popups use in dark mode.
            containerColor = popupCardColor()
        ) {
            HistorySheet(
                sessions = AiChatSession.sessions,
                currentId = AiChatSession.currentId,
                onOpen = { id ->
                    // The conversation changes right here, on the tap; the drawer
                    // folds away on its own animation beside it instead of
                    // holding the switch up.
                    AiChatSession.open(id)
                    scope.launch {
                        runCatching { sheetState.hide() }
                        showHistory = false
                    }
                },
                onDelete = { AiChatSession.delete(it) },
                onDeleteAll = { AiChatSession.deleteAll() },
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
    // One line: the key centres on the text. Once the field grows the key drops
    // to the bottom, which is where a chat composer keeps it. Without this the
    // row was bottom-aligned, so a single line left the 40 dp circle sitting
    // inside a 48 dp touch slot and it read as 4 dp too high.
    var lineCount by remember { mutableStateOf(1) }
    val keyAlign = if (lineCount <= 1) Alignment.CenterVertically else Alignment.Bottom
    // Sending empties the field and drops the whole capsule back to one line;
    // both the height and the returning placeholder are eased so the composer
    // settles instead of snapping.
    val hintAlpha by animateFloatAsState(
        targetValue = if (value.isEmpty()) 1f else 0f,
        animationSpec = tween(140),
        label = "composerHint"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(160)),
        shape = RoundedCornerShape(26.dp),
        // Same block as the floating tab bar in dark mode, so the field and the
        // bar read as one control layer over the page.
        color = if (LocalZgPalette.current.isDark) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
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
                onTextLayout = { lineCount = it.lineCount },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                decorationBox = { inner ->
                    Box {
                        if (hintAlpha > 0.01f) {
                            Text(
                                if (enabled) "问问训练、恢复或饮食…" else "先填写 AI 接入点",
                                modifier = Modifier.alpha(hintAlpha),
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
                    modifier = Modifier.align(keyAlign),
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
                    modifier = Modifier.align(keyAlign),
                    size = 40.dp,
                    iconSize = 18.dp
                )
            }
        }
    }
}

/**
 * Fade wrapper for the two states of the transcript area. It exists as its own
 * composable so [AnimatedVisibility] resolves to the plain version instead of
 * the ColumnScope one inherited from the page column, which cannot be called
 * from inside the Box.
 */
@Composable
private fun FadeLayer(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(130))
    ) {
        content()
    }
}

/**
 * A message that arrived while the page was open fades in and rises a few dp
 * into place. Messages that were already there when the page opened are simply
 * drawn.
 *
 * Only opacity and offset move: the row is measured at its final height from the
 * first frame, so the bubble never grows and nothing above it gets bumped while
 * it settles. [delayMillis] staggers the user bubble and the answer it triggers
 * so the two do not climb at the same instant.
 */
@Composable
private fun AppearFromBelow(
    animate: Boolean,
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!animate) {
        Box(modifier) { content() }
        return
    }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 240,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing
            )
        )
    }
    Box(
        modifier.graphicsLayer {
            alpha = appear.value
            translationY = rise * (1f - appear.value)
        }
    ) {
        content()
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
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * A streamed answer, revealed in sentence-sized pieces.
 *
 * The model sends a few characters at a time; painting every chunk as it lands
 * made the answer crawl in one character at a time. Here the text only moves on
 * at a stop (a line end or a sentence mark, at most 90 characters), and each
 * Markdown block that appears on the way fades in, so it reads as paragraphs
 * arriving rather than as a spinner of letters.
 */
@Composable
private fun StreamingAnswer(content: String, streaming: Boolean, data: HealthData?) {
    // What is shown is only what the model has finished: the paragraph it is
    // still writing stays hidden, so the answer arrives in whole pieces instead
    // of growing a character at a time.
    val visible = if (streaming) finishedParagraphs(content) else content
    MarkdownView(visible, data = data, animateNewBlocks = streaming)
}

/**
 * Everything up to the last finished paragraph.
 *
 * A line break is what finishes a paragraph here; an unfinished fenced block is
 * dropped back to its own opening fence, otherwise half a card's JSON would be
 * drawn as a code box.
 */
private fun finishedParagraphs(text: String): String {
    val cut = text.lastIndexOf('\n')
    if (cut <= 0) return ""
    var shown = text.substring(0, cut)
    while (shown.isNotEmpty() && Regex("```").findAll(shown).count() % 2 == 1) {
        val fence = shown.lastIndexOf("```")
        if (fence <= 0) return ""
        shown = shown.substring(0, fence).trimEnd('\n')
    }
    return shown
}

/** The three faces one answer can have; swapping them is animated. */
private enum class AnswerMode { WAITING, ERROR, TEXT }

/**
 * Answers are page text, not a bubble: full width, so long paragraphs use the
 * whole line, with the copy / regenerate pair underneath once the reply is done.
 *
 * Waiting, failing and answering are three different states of the same block,
 * so they cross-fade instead of snapping: a request that dies turns its dots
 * into an error line, and the first token turns them into text.
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
    val mode = when {
        message.error -> AnswerMode.ERROR
        message.content.isBlank() && streaming -> AnswerMode.WAITING
        else -> AnswerMode.TEXT
    }
    AnimatedContent(
        targetState = mode,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(130)) },
        label = "answerMode"
    ) { current ->
        when (current) {
            AnswerMode.ERROR -> Column(Modifier.fillMaxWidth()) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyLarge,
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

            AnswerMode.WAITING -> Row(verticalAlignment = Alignment.CenterVertically) {
                TypingDots(Modifier.padding(vertical = 8.dp))
                if (phase.isNotBlank()) {
                    Spacer(Modifier.size(10.dp))
                    // The status changes as the request moves on (查数据 →
                    // 组织回答); each change is a small fade, not a swap.
                    AnimatedContent(
                        targetState = phase,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "phase"
                    ) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnswerMode.TEXT -> Column(Modifier.fillMaxWidth()) {
                StreamingAnswer(message.content, streaming, data)
                AnimatedVisibility(
                    visible = streaming,
                    enter = fadeIn(tween(160)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top)
                ) {
                    TypingDots(Modifier.padding(top = 8.dp))
                }
                AnimatedVisibility(
                    visible = !streaming,
                    enter = fadeIn(tween(200)) + expandVertically(tween(220), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(140), shrinkTowards = Alignment.Top)
                ) {
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
 * Nothing said yet: just the greeting, dead centre of the empty transcript. The
 * line explaining what the assistant reads was removed — the chips below say it
 * better, and one short line reads calmer.
 */
@Composable
private fun EmptyTranscript(
    configured: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!configured) {
            // No card surface here: on an empty page a panel reads as content
            // that failed to load. Plain centred text plus one pill reads as a
            // first-run hint instead.
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "先填入自己的 AI 接入点",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "在设置里填兼容 OpenAI 的地址、密钥和模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Surface(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        "去设置",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        } else {
            Text(
                "今天想聊点什么？",
                // Lifted off the dead centre: with the composer and the starter
                // chips below, the middle of the free space is about 48 dp too low.
                modifier = Modifier
                    .offset(y = (-48).dp)
                    .padding(horizontal = 28.dp),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Starter questions as one horizontally scrolling row of chips, parked right
 * above the composer where the next tap goes. The set belongs to the logical
 * day, so it is not rebuilt on every visit: the refresh key above the chips is
 * how the user asks for a different four.
 */
@Composable
private fun SuggestionStrip(
    suggestions: List<String>,
    loading: Boolean,
    onPick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        // One fixed-height row: the header swaps in place, so the chips below
        // never move under the finger that just tapped refresh. The refresh key
        // sits at the left edge — the same spot the "writing them" line takes —
        // so the two read as one control that changes state.
        Row(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(start = 20.dp, end = 14.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = loading,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "suggestionHeader"
            ) { busy ->
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "正在根据最近的数据准备问题",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        // Three dots where the ellipsis used to be: a static
                        // "…" read as finished text, this reads as working.
                        Spacer(Modifier.size(6.dp))
                        TypingDots(dotSize = 4.dp)
                    }
                } else {
                    RefreshSuggestionsKey(onClick = onRefresh)
                }
            }
        }
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
    }
}

/**
 * "换一批" as plain text on the page background: it belongs to the header line,
 * not to the chips it replaces. Hidden while the model is writing, because the
 * loading line takes its place.
 */
@Composable
private fun RefreshSuggestionsKey(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Autorenew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.size(5.dp))
        Text(
            "换一批",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = "刷新问题" }
        )
    }
}

@Composable
private fun HistorySheet(
    sessions: List<AiChatSession.Session>,
    currentId: String?,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onNew: () -> Unit
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val dark = LocalZgPalette.current.isDark
    // The drawer is a light card in dark mode and white paper in light mode, so
    // its ink is pinned rather than inherited from the page.
    val ink = if (dark) Color.White else Color.Black
    val inkMuted = ink.copy(alpha = 0.62f)
    // The open conversation: solid green with white words in light mode; in dark
    // mode the same green laid over the drawer, so white still reads on it.
    val currentFill = if (dark) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.primary
    val currentInk = Color.White
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
                color = ink,
                modifier = Modifier.weight(1f)
            )
            if (sessions.isNotEmpty()) {
                TextButton(onClick = { confirmDeleteAll = true }) {
                    Text("全部删除", color = MaterialTheme.colorScheme.error)
                }
            }
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
                color = inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    // One card per conversation: the whole card opens it, and the
                    // delete key is a circle of its own so a long press near the
                    // edge cannot remove a chat by accident.
                    val selected = session.id == currentId
                    FlatPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        // One step lighter than the drawer, so each conversation
                        // still reads as a card on it; the open one is green.
                        color = if (selected) currentFill
                        else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { onOpen(session.id) }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    session.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) currentInk else ink,
                                    fontWeight = if (selected) FontWeight.SemiBold
                                    else FontWeight.Normal,
                                    maxLines = 1
                                )
                                Text(
                                    "${Fmt.dayAndTime(session.updatedAt)} · ${session.count} 条" +
                                        if (session.id == currentId) " · 当前" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) currentInk.copy(alpha = 0.85f) else inkMuted
                                )
                                if (session.preview.isNotBlank()) {
                                    Text(
                                        session.preview,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) currentInk.copy(alpha = 0.85f) else inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // The key takes the card's full inner height, so the
                            // touch target matches the card instead of floating
                            // in the middle of it.
                            Surface(
                                onClick = { onDelete(session.id) },
                                shape = CircleShape,
                                color = if (selected) currentInk.copy(alpha = 0.18f)
                                else ink.copy(alpha = 0.08f),
                                contentColor = if (selected) currentInk else ink.copy(alpha = 0.8f),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .height(44.dp)
                            ) {
                                Box(
                                    Modifier
                                        .height(44.dp)
                                        .width(44.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "删除对话",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            containerColor = popupCardColor(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            title = { Text("删除全部历史对话？") },
            text = { Text("本机保存的所有对话都会删除，删掉之后找不回来。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAll()
                        confirmDeleteAll = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("取消") }
            }
        )
    }
}

/** Walks out of any context wrappers to the activity that owns the window. */
private fun findActivity(context: Context): Activity? {
    var current: Context? = context
    while (current != null) {
        if (current is Activity) return current
        current = (current as? android.content.ContextWrapper)?.baseContext
    }
    return null
}
