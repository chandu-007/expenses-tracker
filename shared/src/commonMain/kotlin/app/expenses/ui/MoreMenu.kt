package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** One place in the More page. [switchOn] non-null makes the row a switch (Hide amounts) instead of a way in. [tag] is the tag the plain tile had. */
class MoreRow(val tag: String, val icon: ImageVector, val title: String, val subtitle: String, val switchOn: Boolean? = null, val onClick: () -> Unit)

/** A group of rows in one card, with one colour for all its icon tiles. */
class MoreSection(val heading: String, val top: Color, val bottom: Color, val rows: List<MoreRow>)

/** Something that is waiting for the person: a big card at the top while [count] is above zero. Its [tag] is the same as its row's, which then is not shown twice. */
class MoreNudge(val tag: String, val icon: ImageVector, val title: String, val line: String, val count: Int, val top: Color, val bottom: Color, val onClick: () -> Unit)

/**
 * The More page: a search box, what needs attention, the backup reminder, then every place in the app as rows in cards, each group with its own colour.
 * Typing in the box lists only the rows that match (in the nudges' place too), so any setting is two taps away. Back from a screen opened here returns to this page.
 */
@Composable
fun MorePage(modifier: Modifier = Modifier, nudges: List<MoreNudge>, reminder: (@Composable () -> Unit)?, sections: List<MoreSection>) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val nudgeTags = nudges.map { it.tag }.toSet()
    val shown = if (q.isEmpty()) sections.mapNotNull { s -> s.rows.filter { it.tag !in nudgeTags }.takeIf { it.isNotEmpty() }?.let { r -> MoreSection(s.heading, s.top, s.bottom, r) } }
    else sections.mapNotNull { s ->
        s.rows.filter { r -> r.title.contains(q, ignoreCase = true) || r.subtitle.contains(q, ignoreCase = true) || s.heading.contains(q, ignoreCase = true) }
            .takeIf { it.isNotEmpty() }?.let { r -> MoreSection(s.heading, s.top, s.bottom, r) }
    }
    val focus = LocalFocusManager.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        FadeIn {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag("more_page")) {
                Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search settings and tools") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.testTag("more_search_clear")) { Icon(Icons.Outlined.Close, contentDescription = "Clear") } },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("more_search"),
                )
                if (q.isEmpty()) {
                    if (nudges.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        nudges.take(2).forEach { n -> NudgeCard(n, Modifier.weight(1f)) }
                        if (nudges.size == 1) Box(Modifier.weight(1f))
                    }
                    if (reminder != null) Box(Modifier.padding(top = 4.dp)) { reminder() }
                }
                if (shown.isEmpty()) Text("Nothing matches \"$q\".", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp).testTag("more_nothing"))
                shown.forEach { s ->
                    Text(s.heading.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp, top = 16.dp, bottom = 6.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                        s.rows.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.padding(start = 70.dp, end = 14.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
                            RowView(r, s.top, s.bottom)
                        }
                    }
                }
                Text("Your data stays on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 20.dp))
            }
        }
    }
}

@Composable
private fun RowView(r: MoreRow, top: Color, bottom: Color) {
    Row(Modifier.fillMaxWidth().clickable(onClick = r.onClick).padding(horizontal = 14.dp, vertical = 11.dp).testTag(r.tag), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(top, bottom))), contentAlignment = Alignment.Center) {
            Icon(r.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (r.switchOn != null) Switch(checked = r.switchOn, onCheckedChange = null)
        else Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun NudgeCard(n: MoreNudge, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(n.top, n.bottom))).clickable(onClick = n.onClick).padding(14.dp).testTag(n.tag)) {
        Column {
            Icon(n.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Text(n.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Text(n.line, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(n.count.toString(), color = n.bottom, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}
