package com.genkaim.zeppglass.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.ui.components.FlatBottomBar
import com.genkaim.zeppglass.ui.components.LocalTabBarSpace
import com.genkaim.zeppglass.ui.screens.AiScreen
import com.genkaim.zeppglass.ui.screens.ChargeScreen
import com.genkaim.zeppglass.ui.screens.OverviewScreen
import com.genkaim.zeppglass.ui.screens.ActivityScreen
import com.genkaim.zeppglass.ui.sheets.CustomizeTabsSheet
import com.genkaim.zeppglass.ui.sheets.LoginSheet
import com.genkaim.zeppglass.ui.tabs.AppTab
import kotlinx.coroutines.delay

@Composable
fun ZgApp() {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val store = remember { HealthStore.get(context, settings) }

    val tabs = settings.visibleTabs()
    var selected by remember { mutableStateOf(tabs.firstOrNull() ?: AppTab.OVERVIEW) }
    var showCustomize by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var showLoginSheet by remember { mutableStateOf(false) }
    var loginPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(tabs, selected) {
        if (selected !in tabs) selected = tabs.firstOrNull() ?: AppTab.OVERVIEW
    }
    // Without a token there is nothing to load, so invite the user once — with a
    // small sheet, not the full web page: the sign-in itself only opens after
    // they press the button, and the sheet can be dismissed.
    LaunchedEffect(settings.isLoggedIn) {
        if (!settings.isLoggedIn && !loginPrompted) {
            loginPrompted = true
            showLoginSheet = true
        }
    }

    val contentAlpha by animateFloatAsState(
        // Content is always readable: a failed or slow first sync must not
        // leave a blank page, and each screen has its own retry entry.
        targetValue = if (store.state.data != null || !settings.isLoggedIn) 1f else 0.82f,
        animationSpec = tween(420),
        label = "contentAlpha"
    )

    // The bar's real footprint, so the AI composer clears it on every device
    // instead of relying on a hand-tuned constant.
    val density = LocalDensity.current
    var barSpace by remember { mutableStateOf(86.dp) }

    fun openDetail(target: DetailTarget) {
        context.startActivity(DetailActivity.intent(context, target))
    }

    fun openSettings() {
        context.startActivity(Intent(context, SettingsActivity::class.java))
    }

    Surface(
        // The root is deliberately not ime-padded: the floating tab bar must
        // stay where it is when the keyboard opens. Only the AI composer
        // follows the IME (it applies imePadding itself).
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalTabBarSpace provides barSpace) {
                AnimatedContent(
                    targetState = selected,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(contentAlpha),
                    transitionSpec = { fadeIn(tween(150)).togetherWith(fadeOut(tween(110))) },
                    label = "tab"
                ) { tab ->
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    when (tab) {
                        AppTab.OVERVIEW -> OverviewScreen(
                            settings = settings,
                            store = store,
                            modifier = contentModifier,
                            onOpenSettings = ::openSettings,
                            onLogin = { showLoginSheet = true },
                            onOpenTab = { tab -> if (tab in tabs) selected = tab },
                            onOpenDetail = ::openDetail
                        )
                        AppTab.CHARGE -> ChargeScreen(
                            settings = settings,
                            store = store,
                            modifier = contentModifier
                        )
                        AppTab.ACTIVITY -> ActivityScreen(
                            settings = settings,
                            store = store,
                            modifier = contentModifier,
                            onOpenDetail = ::openDetail
                        )
                        AppTab.AI -> AiScreen(
                            settings = settings,
                            store = store,
                            modifier = contentModifier,
                            onOpenSettings = ::openSettings
                        )
                    }
                }

                FlatBottomBar(
                    tabs = tabs,
                    selected = selected,
                    onSelect = { selected = it },
                    onLongPress = { showCustomize = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Measured on the outside, before the bar's own padding,
                        // so it is the space the bar takes from the window.
                        .onSizeChanged { barSpace = with(density) { it.height.toDp() } }
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (showCustomize) {
        CustomizeTabsSheet(
            settings = settings,
            onDismiss = { showCustomize = false }
        )
    }
    if (showLoginSheet) {
        LoginSheet(
            onDismiss = { showLoginSheet = false },
            onLogin = {
                showLoginSheet = false
                showLogin = true
            }
        )
    }
    if (showLogin) {
        com.genkaim.zeppglass.ui.screens.LoginScreen(
            settings = settings,
            onDone = { showLogin = false },
            onCancel = { showLogin = false }
        )
    }
}

