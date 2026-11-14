package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.CategoriseUi
import app.expenses.CategoryUi
import app.expenses.ScreenUndo
import app.expenses.core.domain.UncategorisedNudge

/**
 * The calm card on Home: how many expenses have no category, a Review button and the close ("Not now", quiet for a week). It holds a count and no amounts, so Hide amounts has nothing to hide in it.
 * Shown only when [UncategorisedNudge] says so; the count is worked out with Home's other slow figures, never on the save path. The look is every nudge's ([NudgeCard]).
 */
@Composable
fun CategoriseNudgeCard(count: Int, onReview: () -> Unit, onNotNow: () -> Unit) {
    NudgeCard(
        tag = "categorise_nudge", title = UncategorisedNudge.text(count), titleTag = "categorise_nudge_text", line = "Sort them one at a time.", lineTag = null,
        actionLabel = "Review", actionTag = "categorise_nudge_review", onAction = onReview,
        dismissTag = "categorise_nudge_later", dismissLabel = "Not now: hide for a week", onDismiss = onNotNow,
    )
}

/**
 * Expenses with no category, one at a time: the expense, what the app would suggest (with where that comes from; a guess is marked and is only ever a button, never applied), the first few
 * categories and "More", and Skip. A choice saves the expense and the app learns from it, like an edit of a captured entry; "N left" counts down; the end says what was filed and what was skipped.
 * The bar below offers Undo for the last choice.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CategoriseScreen(
    state: CategoriseUi?, categories: List<CategoryUi>,
    onChoose: (String) -> Unit, onSkip: () -> Unit, onBack: () -> Unit,
    undo: ScreenUndo? = state?.undo, onUndo: (String) -> Unit = {}, onUndoGone: (String) -> Unit = {},
) {
    val undoHost = rememberUndoSnackbar(undo, onUndo, onUndoGone)
    var picking by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { AreaTopBar("Categorise", Area.CAPTURE, Icons.Outlined.Label, onBack, "categorise_back") }, snackbarHost = { SnackbarHost(undoHost) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("categorise_screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val s = state
            when {
                s == null -> Text("Looking for expenses with no category...", style = MaterialTheme.typography.bodyMedium)
                s.done -> Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("categorise_done")) {
                    Text(if (s.filed == 0 && s.skipped == 0) "Every expense has a category." else "All done.", style = MaterialTheme.typography.titleLarge)
                    Text(
                        (if (s.filed > 0) "Filed ${s.filed} ${if (s.filed == 1) "expense" else "expenses"}." else "") + (if (s.skipped > 0) " Left ${s.skipped} as they were." else ""),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("categorise_done_text"),
                    )
                    Button(onClick = onBack, modifier = Modifier.testTag("categorise_finish")) { Text("Done") }
                }
                else -> {
                    val item = s.current!!
                    Text("${s.left} left", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("categorise_left"))
                    Column(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp).testTag("categorise_item_${item.id}"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("categorise_title"))
                        Text(item.amountText, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.testTag("categorise_amount"))
                        Text(item.dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.suggestions.isNotEmpty()) {
                        Text(if (item.suggestions.first().sure) "Suggested" else "A guess: please check it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("categorise_suggested_label"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.suggestions.forEach { sg ->
                                FilterChip(selected = false, onClick = { onChoose(sg.categoryId) }, label = { Text(sg.name + if (sg === item.suggestions.first()) " (${sg.why})" else "") }, modifier = Modifier.testTag("categorise_suggest_${sg.categoryId}"))
                            }
                        }
                    }
                    val offered = offeredCategories(categories, income = false).filter { c -> item.suggestions.none { it.categoryId == c.id } }
                    Text("Or choose", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        offered.take(CATEGORISE_CHIPS).forEach { c -> AssistChip(onClick = { onChoose(c.id) }, label = { Text(c.name) }, modifier = Modifier.testTag("categorise_chip_${c.id}")) }
                        if (offered.size > CATEGORISE_CHIPS) AssistChip(onClick = { picking = true }, label = { Text("More (${offered.size - CATEGORISE_CHIPS})") }, modifier = Modifier.testTag("categorise_more"))
                    }
                    if (picking) CategoryPickerDialog(offered, "", onPick = { id -> picking = false; onChoose(id) }, onClose = { picking = false })
                    OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().testTag("categorise_skip")) { Text("Skip") }
                    Text("Skipped ones stay as they are. Nothing is filed until you choose.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** How many categories show as chips under a shown expense; the rest are behind "More". */
private const val CATEGORISE_CHIPS = 8
