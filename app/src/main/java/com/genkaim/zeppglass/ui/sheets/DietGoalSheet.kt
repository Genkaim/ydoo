package com.genkaim.zeppglass.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.DietGoal

/**
 * The one question the diet page asks before it can budget: what is the food
 * for? It appears once (also for installs upgraded from a build that had no
 * such setting) and can be reopened from the page afterwards.
 *
 * Three large circles instead of a list: each option is a single, unmistakable
 * target to hit with a thumb, and the chosen one is filled so the current
 * answer is visible the moment the sheet opens again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietGoalSheet(
    current: DietGoal?,
    onPick: (DietGoal) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("饮食目标", style = MaterialTheme.typography.headlineSmall)
            Text(
                "减脂在消耗上留出余量，维持吃回消耗，增肌多吃一点。之后随时能改。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DietGoal.entries.forEach { goal ->
                    GoalCircle(
                        goal = goal,
                        selected = goal == current,
                        onClick = { onPick(goal) }
                    )
                }
            }

            Spacer(Modifier.size(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(if (current == null) "稍后再说" else "完成")
                }
            }
        }
    }
}

/** One large circular target: icon and word, filled when it is the current goal. */
@Composable
private fun GoalCircle(goal: DietGoal, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val icon: ImageVector = when (goal) {
        DietGoal.CUT -> Icons.Rounded.TrendingDown
        DietGoal.HOLD -> Icons.Rounded.TrendingFlat
        DietGoal.GAIN -> Icons.Rounded.TrendingUp
    }

    Box(
        Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(4.dp))
            Text(
                goal.label,
                style = MaterialTheme.typography.titleMedium,
                color = content
            )
        }
    }
}
