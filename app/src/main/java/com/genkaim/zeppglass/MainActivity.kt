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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings.get(this)
        // The snapshot is read once, before the first frame, so opening the app
        // draws the last data instead of flashing a "syncing" card.
        HealthStore.get(this, settings)
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
}
