package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.Appearance
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.Density
import app.expenses.core.domain.ThemeMode

/** Light or dark, an accent colour, and how tight the lists are. Changes apply at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(current: Appearance, onChange: (Appearance) -> Unit, onBack: () -> Unit, motionSpeed: String = "relaxed", onMotionSpeed: (String) -> Unit = {}) {
    Scaffold(topBar = { ScreenHeader("Appearance", onBack, "appearance_back") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Theme", style = MaterialTheme.typography.labelMedium)
            SegmentedChoice(ThemeMode.entries.map { Segment(it, it.label, "theme_${it.name}") }, current.mode, { onChange(current.copy(mode = it)) })
            Text("Accent colour", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Appearances.ACCENTS.forEach { a ->
                    val chosen = current.accent == a
                    Box(
                        Modifier.size(40.dp).background(Color(0xFF000000.toInt() or a.rgb), CircleShape).clickable { onChange(current.copy(accent = a)) }
                            .testTag("accent_${a.key}").semantics { contentDescription = a.label + if (chosen) ", chosen" else ""; selected = chosen },
                        contentAlignment = Alignment.Center,
                    ) { if (chosen) Text("✓", color = Color(0xFF000000.toInt() or Appearances.onColor(a.rgb))) }
                }
            }
            Text("List spacing", style = MaterialTheme.typography.labelMedium)
            SegmentedChoice(Density.entries.map { Segment(it, it.label, "density_${it.name}") }, current.density, { onChange(current.copy(density = it)) })
            Text("Motion", style = MaterialTheme.typography.labelMedium)
            SegmentedChoice(listOf("off" to "Off", "quick" to "Quick", "relaxed" to "Relaxed", "slow" to "Slow").map { (key, label) -> Segment(key, label, "motion_$key") }, motionSpeed, onMotionSpeed)
            Details("appearance_details") {
                Text("How long screens, totals and charts take to move. Relaxed is the default; Off changes everything at once, as the phone's own \"Remove animations\" setting does.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Compact fits more entries on the screen: the rows of the list, search and report entries are about a third tighter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
