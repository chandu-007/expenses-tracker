package app.expenses.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.border
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.DragHandle
import app.expenses.AccountRowUi
import app.expenses.core.domain.HomeLayout
import app.expenses.core.domain.HomeSection

/** Which blocks the home screen shows and in what order. Changes apply at once; nothing is deleted by hiding a block. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLayoutScreen(layout: HomeLayout, onChange: (HomeLayout) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { ScreenHeader("Customise home", onBack, "home_layout_back") }) { pad ->
        val feel = rememberFeel()
        var dragging by remember { mutableStateOf<HomeSection?>(null) }
        var dragDy by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val rowHeights = remember { HashMap<HomeSection, Float>() }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Choose what the home screen shows and move blocks: press and hold the handle and drag, or use the arrows. The headline total and the list of entries are always there.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            layout.order.forEachIndexed { i, section ->
                Row(Modifier.fillMaxWidth().zIndex(if (dragging == section) 1f else 0f).graphicsLayer { translationY = if (dragging == section) dragDy else 0f; shadowElevation = if (dragging == section) 12f else 0f }.background(if (dragging == section) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent).onSizeChanged { rowHeights[section] = it.height.toFloat() }.padding(horizontal = 16.dp, vertical = 4.dp).testTag("home_row_${section.name}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Switch(checked = section !in layout.hidden, onCheckedChange = { onChange(layout.withHidden(section, !it)) }, modifier = Modifier.testTag("home_toggle_${section.name}").semantics { contentDescription = "Show ${section.label}" })
                    Text(section.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    // press and hold the handle, then drag to another place (UI S5); the arrows stay for anyone who cannot drag (WCAG 2.5.7)
                    Box(
                        Modifier.size(44.dp).testTag("home_drag_${section.name}").semantics { contentDescription = "Drag to move ${section.label}, or use the arrows" }.pointerInput(section, i, layout) {
                            var last = 0
                            fun steps(): Int { val h = rowHeights[section] ?: return 0; return (dragDy / h).let { if (it < 0) -kotlin.math.floor(-it + 0.5f).toInt() else kotlin.math.floor(it + 0.5f).toInt() }.coerceIn(-i, layout.order.size - 1 - i) }
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = section; dragDy = 0f; last = 0; feel(Feel.SELECT) },
                                onDrag = { change, amount -> change.consume(); dragDy += amount.y; val t = steps(); if (t != last) { last = t; feel(Feel.TICK) } },
                                onDragEnd = { val n = steps(); dragging = null; dragDy = 0f; if (n != 0) { var l = layout; repeat(kotlin.math.abs(n)) { l = l.move(section, if (n < 0) -1 else 1) }; onChange(l) } },
                                onDragCancel = { dragging = null; dragDy = 0f },
                            )
                        },
                        contentAlignment = Alignment.Center,
                    ) { androidx.compose.material3.Icon(Icons.Outlined.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { onChange(layout.move(section, -1)) }, enabled = i > 0, modifier = Modifier.testTag("home_up_${section.name}").semantics { contentDescription = "Move ${section.label} up" }) { Text("↑") }
                    TextButton(onClick = { onChange(layout.move(section, 1)) }, enabled = i < layout.order.size - 1, modifier = Modifier.testTag("home_down_${section.name}").semantics { contentDescription = "Move ${section.label} down" }) { Text("↓") }
                }
                HorizontalDivider()
            }
        }
    }
}
