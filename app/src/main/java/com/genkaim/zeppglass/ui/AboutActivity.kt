package com.genkaim.zeppglass.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.ui.screens.AboutScreen
import com.genkaim.zeppglass.ui.theme.ZeppGlassTheme
import com.genkaim.zeppglass.ui.theme.applyZeppChrome

/** About page: its own Activity, opened from settings. */
class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings.get(this)
        setContent {
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            SideEffect { applyZeppChrome(dark) }
            ZeppGlassTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.statusBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    AboutScreen(onBack = { finish() })
                }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AboutActivity::class.java)
    }
}
