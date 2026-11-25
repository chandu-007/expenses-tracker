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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.Appearance
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.Density
import app.expenses.core.domain.ThemeMode

/**
 * Light or dark, an accent colour, and how tight the lists are. Changes apply at once. UI S9: a live preview at the top (U-18: the page shows what it changes), the true-black dark theme and,
 * where the phone offers one (Android 12 and later), the wallpaper's colour as an accent (D18). Both are part of the one appearance setting, which backups carry.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(current: Appearance, onChange: (Appearance) -> Unit, onBack: () -> Unit, motionSpeed: String = "relaxed", onMotionSpeed: (String) -> Unit = {}, showSpendingRed: Boolean = false, onShowSpendingRed: (Boolean) -> Unit = {}, onShowTipsAgain: () -> Unit = {}, hapticsOn: Boolean = true, onHaptics: (Boolean) -> Unit = {}, simplifyMode: String = "auto", onSimplifyMode: (String) -> Unit = {}, simplifyLine: String = "") {
    Scaffold(topBar = { ScreenHeader("Appearance", onBack, "appearance_back") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("How the app looks on this phone. Changes apply at once; the example below shows them.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("appearance_summary"))
            AppearancePreview()
            Text("Theme", style = MaterialTheme.typography.labelMedium)
            SegmentedChoice(ThemeMode.entries.map { Segment(it, it.label, "theme_${it.name}") }, current.mode, { onChange(current.copy(mode = it)) })
            // UI S9 (D18): true black for the dark theme (OLED screens); it does nothing while the app is light, which the line says
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = current.black, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { onChange(current.copy(black = it)) }).testTag("black_row"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Black dark theme", style = MaterialTheme.typography.bodyLarge)
                    Text("When the app is dark, the background is true black: easier on OLED screens at night. No change while it is light.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = current.black, onCheckedChange = null, modifier = Modifier.testTag("black_switch"))
            }
            Text("Accent colour", style = MaterialTheme.typography.labelMedium)
            val wallpaperRgb = systemAccentRgb()
            // more room than one row: 48 dp swatches that wrap at a large text size or on a narrow phone, the wallpaper's colour last where the phone has one
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Appearances.ACCENTS.forEach { a ->
                    val chosen = current.accent == a || (a == Appearances.ACCENTS.first() && current.accent == Appearances.WALLPAPER && wallpaperRgb == null)
                    AccentSwatch(a.rgb, a.label, chosen, "accent_${a.key}") { onChange(current.copy(accent = a)) }
                }
                if (wallpaperRgb != null) AccentSwatch(wallpaperRgb, "Wallpaper", current.accent == Appearances.WALLPAPER, "accent_wallpaper", wallpaper = true) { onChange(current.copy(accent = Appearances.WALLPAPER)) }
            }
            if (wallpaperRgb != null && current.accent == Appearances.WALLPAPER) Text("The colour your phone takes from its wallpaper; it changes when the wallpaper does. Money colours stay the same.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("accent_wallpaper_line"))
            Text("List spacing", style = MaterialTheme.typography.labelMedium)
            SegmentedChoice(Density.entries.map { Segment(it, it.label, "density_${it.name}") }, current.density, { onChange(current.copy(density = it)) })
            Text("Motion", style = MaterialTheme.typography.labelMedium)
            SegmentedChoice(listOf("off" to "Off", "quick" to "Quick", "relaxed" to "Relaxed", "slow" to "Slow").map { (key, label) -> Segment(key, label, "motion_$key") }, motionSpeed, onMotionSpeed)
            // UI S7 (D15): the app simplifies itself on a weak phone; the person can force it on or off, and the line says what is happening
            Text("Simplify for this phone", style = MaterialTheme.typography.labelMedium)
            SegmentedChoice(listOf("auto" to "Automatic", "on" to "Always", "off" to "Never").map { (key, label) -> Segment(key, label, "simplify_$key") }, simplifyMode, onSimplifyMode)
            if (simplifyLine.isNotEmpty()) Text(simplifyLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("simplify_line"))
            // UI S7 (D14): one switch for every haptic in the app
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = hapticsOn, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onHaptics).testTag("haptics_row"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Haptics", style = MaterialTheme.typography.bodyLarge)
                    Text("A small tap for presses, saves and swipes. Off silences every one of them. The phone's own touch-feedback setting applies as well.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = hapticsOn, onCheckedChange = null, modifier = Modifier.testTag("haptics_switch"))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = showSpendingRed, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onShowSpendingRed).testTag("spend_red_row"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show spending in red", style = MaterialTheme.typography.bodyLarge)
                    Text("Off: money you spend is in the ordinary text colour with a minus sign, and red is kept for what is wrong (over a limit, overdue). On: spending is red.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = showSpendingRed, onCheckedChange = null, modifier = Modifier.testTag("spend_red_switch"))
            }
            var tipsBack by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { onShowTipsAgain(); tipsBack = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("tips_reset")) { Text("Show tips again") }
                if (tipsBack) Text("Done. Each tip will show once more, the next time you open its screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("tips_reset_done").semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
            }
            Details("appearance_details") {
                Text("How long screens, totals and charts take to move. Relaxed is the default; Off changes everything at once, as the phone's own \"Remove animations\" setting does.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Compact fits more entries on the screen: the rows of the list, search and report entries are about a third tighter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One accent: a 48 dp round swatch with a tick when chosen; the wallpaper's carries a small palette mark so it reads as "the phone's colour". A screen reader hears the name and whether it is chosen. */
@Composable
private fun AccentSwatch(rgb: Int, label: String, chosen: Boolean, tag: String, wallpaper: Boolean = false, onClick: () -> Unit) {
    val fill = rgbColor(rgb)
    val on = rgbColor(Appearances.onColor(rgb))
    Box(
        Modifier.size(48.dp).background(fill, CircleShape).clickable(onClick = onClick)
            .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .testTag(tag).semantics { contentDescription = label + if (chosen) ", chosen" else ""; selected = chosen },
        contentAlignment = Alignment.Center,
    ) {
        if (chosen) Text("✓", color = on)
        else if (wallpaper) androidx.compose.material3.Icon(Icons.Outlined.Palette, contentDescription = null, tint = on, modifier = Modifier.size(20.dp))
    }
}

/**
 * UI S9 (U-18): what the choices on this page look like, drawn in the app's real theme (so it follows the accent, light or dark, black and the list spacing at once): a month card and two rows.
 * Invented figures; a screen reader hears only that it is an example, never the figures.
 */
@Composable
private fun AppearancePreview() {
    // the tag comes first: clearAndSetSemantics clears whatever is chained after it (the tag was lost and every test waiting for it timed out)
    Column(
        Modifier.fillMaxWidth().standardCard().padding(16.dp).testTag("appearance_preview").clearAndSetSemantics { contentDescription = "Example of the look you chose, not your data" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("This month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("On pace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text("₹12,480", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            Box(Modifier.fillMaxWidth(0.55f).height(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        listOf("Chai" to "−₹20", "Groceries" to "−₹1,240").forEach { (name, amount) ->
            Row(Modifier.fillMaxWidth().padding(vertical = rowPadding(12.dp)), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(amount, style = MaterialTheme.typography.bodyLarge, color = amountColor(Flow3.OUT))
            }
        }
    }
}
