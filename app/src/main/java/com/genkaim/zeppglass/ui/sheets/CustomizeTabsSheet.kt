package com.genkaim.zeppglass.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.ui.tabs.AppTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeTabsSheet(settings: AppSettings, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val order = settings.tabOrder
    val hidden = settings.hiddenTabs

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("自定义底栏", style = MaterialTheme.typography.headlineSmall)
            Text(
                "调整顺序，或暂时收起某个入口。至少保留一个。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(6.dp))

            order.forEachIndexed { index, tab ->
                val visible = tab !in hidden
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = tab.accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(tab.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    settings.updateTabOrder(
                                        order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    )
                                }
                            },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "上移")
                        }
                        IconButton(
                            onClick = {
                                if (index < order.lastIndex) {
                                    settings.updateTabOrder(
                                        order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    )
                                }
                            },
                            enabled = index < order.lastIndex
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "下移")
                        }
                        Switch(
                            checked = visible,
                            onCheckedChange = { on ->
                                val next = hidden.toMutableSet()
                                if (on) next.remove(tab) else next.add(tab)
                                settings.updateHidden(next)
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        settings.updateTabOrder(AppTab.entries.toList())
                        settings.updateHidden(emptySet())
                    }
                ) { Text("恢复默认") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        }
    }
}
