package com.genkaim.zeppglass.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.genkaim.zeppglass.data.AppSettings

/** Light/dark for this app, not for the system: the setting can override it. */
fun resolveDark(context: Context, settings: AppSettings): Boolean =
    when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

/**
 * Edge-to-edge window chrome that follows the app theme.
 *
 * The system bar icons and the window background used to follow the *system*
 * theme, so forcing dark mode on a light phone left a white status bar strip
 * for a frame (and light icons on a dark page). Setting both here keeps the
 * strip the same colour as the page.
 */
fun ComponentActivity.applyZeppChrome(dark: Boolean) {
    val background = if (dark) 0xFF07080A.toInt() else 0xFFF2F3F5.toInt()
    enableEdgeToEdge(
        statusBarStyle = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        },
        navigationBarStyle = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
    )
    window.setBackgroundDrawable(ColorDrawable(background))
    window.decorView.setBackgroundColor(background)
}
