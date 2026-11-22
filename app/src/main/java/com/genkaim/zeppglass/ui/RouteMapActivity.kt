package com.genkaim.zeppglass.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.GeoPoint
import com.genkaim.zeppglass.ui.components.FullscreenRouteMap
import com.genkaim.zeppglass.ui.theme.ZeppGlassTheme
import com.genkaim.zeppglass.ui.theme.applyZeppChrome

/**
 * The workout route as a page of its own.
 *
 * A map wants the whole screen and its own back behaviour, so it is an Activity
 * like every other second-level page: the system transition, the recents entry
 * and the back gesture all come for free. The coordinates ride along in the
 * Intent — a track is a few kilobytes, far below the binder limit.
 */
class RouteMapActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings.get(this)
        val points = intent.toPoints()
        if (points.size < 2) {
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
                FullscreenRouteMap(points = points, onClose = { finish() })
            }
        }
    }

    /** Coordinates travel as one flat array: lat, lon, lat, lon, … */
    private fun Intent.toPoints(): List<GeoPoint> {
        val coords = getDoubleArrayExtra(EXTRA_COORDS) ?: return emptyList()
        if (coords.size < 4) return emptyList()
        return buildList(coords.size / 2) {
            var i = 0
            while (i + 1 < coords.size) {
                add(GeoPoint(coords[i], coords[i + 1]))
                i += 2
            }
        }
    }

    companion object {
        private const val EXTRA_COORDS = "route_coords"

        fun intent(context: Context, points: List<GeoPoint>): Intent =
            Intent(context, RouteMapActivity::class.java).apply {
                val coords = DoubleArray(points.size * 2)
                points.forEachIndexed { index, point ->
                    coords[index * 2] = point.lat
                    coords[index * 2 + 1] = point.lon
                }
                putExtra(EXTRA_COORDS, coords)
            }
    }
}
