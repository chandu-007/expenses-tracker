package app.expenses

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.expenses.ui.AddExpenseScreen
import app.expenses.ui.EntryListScreen
import app.expenses.ui.KeyLostScreen
import app.expenses.ui.OpeningScreen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val viewModel: LedgerViewModel by viewModels()

    /** Counts requests to open the add screen from the launcher shortcut or the Quick Settings tile. */
    private val addRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("onCreate: begin")
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.action == ACTION_ADD_EXPENSE) addRequests.value = 1
        // Measurement builds only: `am start -n app.expenses/.MainActivity --ei seed_rows 100000`
        intent.getIntExtra("seed_rows", 0).takeIf { it > 0 && BuildConfig.SEEDING_ENABLED && savedInstanceState == null }?.let(viewModel::seed)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { StartupTrace.mark("first composition") }
                    var adding by rememberSaveable { mutableStateOf(false) }
                    var prefillCategory by rememberSaveable { mutableStateOf<String?>(null) }
                    var prefillAccount by rememberSaveable { mutableStateOf<String?>(null) }
                    var prefillNote by rememberSaveable { mutableStateOf("") }
                    // Lets `am start` and tracing measure cold start up to the first screen with data, not just the spinner.
                    LaunchedEffect(state is UiState.Ready) { if (state is UiState.Ready) { StartupTrace.mark("ready composed"); reportFullyDrawn() } }

                    val requests by addRequests.collectAsState()
                    var handledRequests by rememberSaveable { mutableIntStateOf(0) }
                    LaunchedEffect(requests) {
                        if (requests > handledRequests) { handledRequests = requests; prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true }
                    }

                    when (val s = state) {
                        UiState.Opening -> OpeningScreen()
                        is UiState.KeyLost -> KeyLostScreen(s.message, viewModel::startFresh)
                        is UiState.Ready ->
                            if (adding) {
                                BackHandler { adding = false }
                                AddExpenseScreen(
                                    accounts = s.accounts,
                                    initialCategory = prefillCategory ?: s.lastCategory,
                                    initialAccountId = prefillAccount ?: s.lastAccountId,
                                    initialNote = prefillNote,
                                    onSave = { amount, category, note, account, asTemplate ->
                                        viewModel.addExpense(amount, category, note, account, asTemplate)
                                        adding = false
                                    },
                                    onBack = { adding = false },
                                )
                            } else {
                                EntryListScreen(
                                    state = s,
                                    onAdd = { prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true },
                                    onLoadMore = viewModel::loadMore,
                                    onMessageShown = viewModel::dismissMessage,
                                    onTemplate = { t ->
                                        if (t.amountText != null) viewModel.quickAdd(t.id)
                                        else { prefillCategory = t.category; prefillAccount = t.accountId; prefillNote = t.note.ifEmpty { t.name }; adding = true }
                                    },
                                    onDeleteTemplate = viewModel::deleteTemplate,
                                    onUndo = viewModel::undo,
                                    onUndoDismissed = viewModel::dismissUndo,
                                )
                            }
                    }
                }
            }
        }
    }

    /** The activity is `singleTop`, so a shortcut or tile tap while the app is open arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_ADD_EXPENSE) addRequests.value += 1
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "app.expenses.action.ADD_EXPENSE"
    }
}
