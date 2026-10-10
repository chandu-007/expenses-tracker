package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.CategoryUi
import app.expenses.core.domain.CategoryCatalog

/**
 * Ready-made categories, grouped by kind, to tick and add. A group the person already has shows what is under it, and only what is missing can be
 * ticked. Nothing is created until "Add" is pressed. [onAdd] gets, for each ticked group, the subcategories wanted under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedCategoriesScreen(existing: List<CategoryUi>, onAdd: (Map<String, Set<String>>) -> Unit, onBack: () -> Unit) {
    val domain = existing.asDomain()
    // "Group|Child" for a ticked subcategory and "Group|" for a ticked group: a plain set of strings survives a rotation
    var ticked by rememberSaveable { mutableStateOf(setOf<String>()) }
    val picks = remember(ticked) {
        ticked.groupBy({ it.substringBefore('|') }, { it.substringAfter('|') }).mapValues { (_, v) -> v.filter { it.isNotEmpty() }.toSet() }
    }
    val count = remember(picks, existing) { CategoryCatalog.plan(domain, picks).size }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Suggested categories") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("suggested_back")) { Text("Back") } }) },
        bottomBar = {
            Button(
                onClick = { onAdd(picks) }, enabled = count > 0,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("suggested_add"),
            ) { Text(if (count == 0) "Pick some to add" else if (count == 1) "Add 1 category" else "Add $count categories") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tap a heading to take the whole group, or pick single ones. What you already have is marked and left alone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            for (type in CategoryCatalog.types) {
                Text(type.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                for (g in CategoryCatalog.groups.filter { it.type == type }) {
                    val parent = domain.firstOrNull { it.parentId == null && it.name.equals(g.name, ignoreCase = true) }
                    val have = parent?.let { p -> domain.filter { it.parentId == p.id }.map { it.name.lowercase() }.toSet() }.orEmpty()
                    val missing = g.children.filter { it.lowercase() !in have }
                    val groupTicked = "${g.name}|" in ticked || missing.isNotEmpty() && missing.all { "${g.name}|$it" in ticked }
                    Column(Modifier.testTag("suggested_group_${g.name}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            FilterChip(
                                selected = groupTicked || (parent != null && missing.isEmpty()), enabled = parent == null || missing.isNotEmpty(),
                                onClick = {
                                    ticked = if (groupTicked) ticked.filterNot { it.substringBefore('|') == g.name }.toSet()
                                    else ticked + "${g.name}|" + missing.map { "${g.name}|$it" }
                                },
                                label = { Text(g.name + if (parent != null) "  (you have this)" else "", fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("suggested_pick_${g.name}"),
                            )
                        }
                        if (g.children.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp)) {
                            g.children.forEach { c ->
                                val there = c.lowercase() in have
                                FilterChip(
                                    selected = there || "${g.name}|$c" in ticked, enabled = !there,
                                    onClick = { val k = "${g.name}|$c"; ticked = if (k in ticked) ticked - k else ticked + k },
                                    label = { Text(if (there) "$c ✓" else c) }, modifier = Modifier.testTag("suggested_child_${g.name}_$c"),
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
