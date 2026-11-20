package com.genkaim.zeppglass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.ui.ZgApp
import com.genkaim.zeppglass.ui.theme.ZeppGlassTheme
import com.genkaim.zeppglass.ui.theme.applyZeppChrome
import com.genkaim.zeppglass.ui.theme.resolveDark

class MainActivity : ComponentActivity() {
    /** One store for the activity, so onResume can ask the same instance to sync. */
    private val store: HealthStore by lazy { HealthStore.get(this, AppSettings.get(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings.get(this)
        // The diet log is read by screens and by the AI coach; loading it here
        // means the coach sees today's meals even when the diet page was never
        // opened in this session.
        com.genkaim.zeppglass.data.DietStore.attach(this)
        // The snapshot is read once, before the first frame, so opening the app
        // draws the last data instead of flashing a "syncing" card.
        store
        // Push a fresh RemoteViews set on every cold start: a launcher may still
        // be showing views inflated from an older build's layout.
        Thread { com.genkaim.zeppglass.ui.widget.YdooRingsWidget.updateAll(this) }.start()
        // Edge-to-edge has to be in place before the view is attached. Running
        // it from a LaunchedEffect (after the first frame) left every window
        // inset at 0 — the status bar, the navigation bar and the keyboard
        // height — so imePadding() did nothing and the AI composer slid behind
        // the keyboard. Chrome is applied again below when the theme changes.
        applyZeppChrome(resolveDark(this, settings))
        setContent {
            // Read the live setting and the live system configuration, so
            // 跟随系统 / 浅色 / 深色 apply without restarting the app.
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            LaunchedEffect(dark) { applyZeppChrome(dark) }
            ZeppGlassTheme(darkTheme = dark) {
                ZgApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening (or returning to) the app is a sync point. The one-minute
        // guard keeps a quick tab switch from firing another request.
        val last = store.state.lastSyncedAt ?: 0L
        if (System.currentTimeMillis() - last > 60_000L) store.refresh()
    }
}
