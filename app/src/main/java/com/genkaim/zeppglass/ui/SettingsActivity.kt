package com.genkaim.zeppglass.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.ui.screens.LoginScreen
import com.genkaim.zeppglass.ui.screens.SettingsScreen
import com.genkaim.zeppglass.ui.theme.ZeppGlassTheme
import com.genkaim.zeppglass.ui.theme.applyZeppChrome

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings.get(this)
        setContent {
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) { applyZeppChrome(dark) }
            ZeppGlassTheme(darkTheme = dark) {
                var showLogin by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier.statusBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    if (showLogin) {
                        LoginScreen(
                            settings = settings,
                            onDone = { showLogin = false },
                            onCancel = { showLogin = false }
                        )
                    } else {
                        SettingsScreen(
                            settings = settings,
                            onBack = { finish() },
                            onLogin = { showLogin = true },
                            onOpenAbout = {
                                startActivity(AboutActivity.intent(this@SettingsActivity))
                            }
                        )
                    }
                }
            }
        }
    }
}

