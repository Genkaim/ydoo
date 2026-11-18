package com.genkaim.zeppglass.ui.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.genkaim.zeppglass.ui.theme.AuroraBlue
import com.genkaim.zeppglass.ui.theme.AuroraMint
import com.genkaim.zeppglass.ui.theme.AuroraSun
import com.genkaim.zeppglass.ui.theme.AuroraViolet

/**
 * The four destinations of the shell. The id is what gets persisted when the
 * user rearranges or hides tabs, so it must never change.
 */
enum class AppTab(
    val id: String,
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val accent: Color
) {
    OVERVIEW("overview", "概览", "概览", Icons.Rounded.Dashboard, AuroraBlue),
    CHARGE("charge", "Hybrid Charge", "Charge", Icons.Rounded.Bolt, AuroraViolet),
    ACTIVITY("activity", "活动", "活动", Icons.Rounded.DirectionsRun, AuroraSun),
    AI("ai", "AI 教练", "AI", Icons.Rounded.AutoAwesome, AuroraMint);

    companion object {
        fun fromId(id: String): AppTab? = entries.firstOrNull { it.id == id }
    }
}
