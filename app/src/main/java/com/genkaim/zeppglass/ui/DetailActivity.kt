package com.genkaim.zeppglass.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.ui.screens.DetailScreen
import com.genkaim.zeppglass.ui.theme.ZeppGlassTheme
import com.genkaim.zeppglass.ui.theme.applyZeppChrome
import java.time.LocalDate

/**
 * Second-level pages live in their own Activity, so the system back gesture,
 * recents entry and process-level state all behave like a normal Android app.
 */
class DetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings.get(this)
        val store = HealthStore.get(this, settings)
        val target = intent.toTarget() ?: run {
            finish()
            return
        }
        setContent {
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            SideEffect { applyZeppChrome(dark) }
            ZeppGlassTheme(darkTheme = dark) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    DetailScreen(
                        settings = settings,
                        store = store,
                        target = target,
                        onClose = { finish() },
                        // A card inside a detail page opens its own page on top,
                        // so back returns to the sleep page rather than home.
                        onOpen = { startActivity(intent(this@DetailActivity, it)) },
                        shoot = intent.getBooleanExtra(EXTRA_SHOOT, false),
                        photoPath = intent.getStringExtra(EXTRA_PHOTO)
                    )
                }
            }
        }
    }

    private fun Intent.toTarget(): DetailTarget? {
        val kindName = getStringExtra(EXTRA_KIND) ?: return null
        val kind = runCatching { DetailKind.valueOf(kindName) }.getOrNull() ?: return null
        val date = getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return DetailTarget(
            kind, date,
            getStringExtra(EXTRA_WORKOUT),
            getStringExtra(EXTRA_METRIC),
            getStringExtra(EXTRA_SOURCE)
        )
    }

    companion object {
        private const val EXTRA_KIND = "detail_kind"
        private const val EXTRA_DATE = "detail_date"
        private const val EXTRA_WORKOUT = "detail_workout"
        private const val EXTRA_METRIC = "detail_metric"
        private const val EXTRA_SOURCE = "detail_source"
        private const val EXTRA_SHOOT = "detail_shoot"
        private const val EXTRA_PHOTO = "detail_photo"

        fun intent(
            context: android.content.Context,
            target: DetailTarget,
            shoot: Boolean = false,
            photoPath: String? = null
        ): Intent =
            Intent(context, DetailActivity::class.java).apply {
                putExtra(EXTRA_KIND, target.kind.name)
                target.date?.let { putExtra(EXTRA_DATE, it.toString()) }
                target.workoutId?.let { putExtra(EXTRA_WORKOUT, it) }
                target.metric?.let { putExtra(EXTRA_METRIC, it) }
                target.source?.let { putExtra(EXTRA_SOURCE, it) }
                if (shoot) putExtra(EXTRA_SHOOT, true)
                photoPath?.let { putExtra(EXTRA_PHOTO, it) }
            }
    }
}
