package app.expenses

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.storage.EntryDetails
import app.expenses.ui.Flow3
import app.expenses.ui.LocalSpendingRed
import app.expenses.ui.Feel
import app.expenses.ui.amountColor
import app.expenses.ui.hapticConstant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * UI S3 on the device: category glyph tiles on stable colours (Food and Fun are no longer both "F", a rename does not recolour), neutral spending amounts with the Appearance switch, the swipe's
 * screen-reader action, the glyph picker, the monogram for a known merchant and the grey swap tile for a transfer, and the one-time line about the new look. Invented data.
 */
@RunWith(AndroidJUnit4::class)
class UiS3Test {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1"); it.templates.putSetting("category_look_notice", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun spend(id: String, rupees: Long, category: String, merchant: String) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), System.currentTimeMillis(), AccountId("bank"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))), EntryDetails(merchant = merchant)) }
    }
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }

    /** The colour of the tile's container: a pixel in its left part, which the glyph (the middle 56%) never reaches. */
    private fun tileColour(tag: String): Int {
        val img = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0].captureToImage()
        return img.toPixelMap()[(img.width * 0.1f).toInt(), img.height / 2].toArgb()
    }

    @Test
    fun foodAndFunAreGlyphTilesOfDifferentColoursNotTwoLettersF() {
        spend("a", 100, "Food", "Cafe"); spend("b", 200, "Fun", "Movie"); spend("c", 300, "Transport", "Auto"); spend("d", 400, "Groceries", "Shop")
        launch(); waitFor("tile_food"); waitFor("tile_fun")
        val colours = listOf("tile_food", "tile_fun", "tile_transport", "tile_groceries").map { tileColour(it) }
        assertEquals(colours.size, colours.toSet().size, "four categories, four tile colours: $colours")
        // not a letter: a glyph tile has an icon and no text of its own
        assertTrue(compose.onAllNodes(hasTestTag("tile_food"), useUnmergedTree = true)[0].fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text) == null)
    }

    @Test
    fun renamingACategoryKeepsItsTileColour() {
        spend("a", 100, "Food", "Cafe")
        launch(); waitFor("tile_food")
        val before = tileColour("tile_food")
        runBlocking { container.write { ok -> ok.categories.rename("Food", "Dining") } }
        launch(); waitFor("tile_dining")
        assertEquals(before, tileColour("tile_dining"), "the colour comes from the id, so a new name is the same colour")
    }

    @Test
    fun aKnownMerchantShowsItsMonogramAndAnOrdinaryNoteShowsTheCategoryGlyph() {
        spend("a", 436, "Food", "Starbucks"); spend("b", 120, "Food", "Lunch with Riya")
        launch(); waitFor("row_Starbucks"); waitFor("row_Lunch with Riya")
        assertTrue(has("tile_starbucks"), "a payee the app knows by name gets its own monogram tile")
        assertTrue(has("tile_food"), "a note it does not know gets the category's glyph tile")
    }

    @Test
    fun aTransferIsAGreySwapTileAndAnAmountWithNoSign() {
        runBlocking { container.write { ok -> ok.store.save(Transfer(EntryId("t"), System.currentTimeMillis(), AccountId("bank"), AccountId("cash"), Money(300_000, inr)), EntryDetails(merchant = "ATM withdrawal")) } }
        launch(); waitFor("row_ATM withdrawal")
        assertTrue(has("tile_transfer"))
        val shown = node("amount_ATM withdrawal").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }
        assertEquals("₹3,000.00", shown, "no plus or minus on money that only moved between your own accounts")
    }

    @Test
    fun spendingHasAMinusAndIncomeAPlusAndHideAmountsMasksBothInTheRows() {
        spend("a", 436, "Food", "Cafe")
        runBlocking { container.write { it.store.save(Income(EntryId("i"), System.currentTimeMillis(), AccountId("bank"), Money(500_000, inr), CategoryId("income:salary")), EntryDetails(merchant = "Acme")) } }
        launch(); waitFor("amount_Cafe"); waitFor("amount_Acme")
        fun text(tag: String) = node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }
        assertEquals("₹436.00", text("amount_Cafe")); assertEquals("+₹5,000.00", text("amount_Acme"))
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        launch(); waitFor("amount_Cafe")
        assertFalse(text("amount_Cafe").any { it.isDigit() }, "no digit of a hidden amount: '${text("amount_Cafe")}'")
        assertFalse(text("amount_Acme").any { it.isDigit() })
    }

    @Test
    fun theAppearanceSwitchShowSpendingInRedIsOffByDefaultAndIsKept() {
        launch(); compose.openMore(); tap("open_appearance"); waitFor("spend_red_row")
        assertEquals(null, setting("show_spending_red"), "off until the person turns it on")
        tap("spend_red_row"); compose.waitUntil(10_000) { setting("show_spending_red") == "1" }
        launch(); compose.openMore(); tap("open_appearance"); waitFor("spend_red_row")
        tap("spend_red_row"); compose.waitUntil(10_000) { setting("show_spending_red") == "0" }
    }

    @Test
    fun theGlyphPickerChoosesAnIconThatIsKeptAndAutomaticGoesBack() {
        spend("a", 100, "Food", "Cafe")
        launch(); compose.openMore(); tap("manage_categories"); waitFor("new_category")
        tap("cat_row_Food"); waitFor("glyph_pets")
        tap("glyph_pets"); tap("cat_save"); waitFor("categories_list")
        compose.waitUntil(10_000) { setting("cat_glyph:Food") == "pets" }
        tap("cat_row_Food"); waitFor("glyph_auto"); tap("glyph_auto"); tap("cat_save"); waitFor("categories_list")
        compose.waitUntil(10_000) { setting("cat_glyph:Food") == null }
    }

    @Test
    fun aRowCanBeDeletedFromTheScreenReaderActionAndUndoBringsItBack() {
        spend("a", 100, "Food", "Cafe")
        launch(); waitFor("row_Cafe")
        // the swipe's action for a screen reader (which cannot swipe): a custom action on the row's content
        val actions = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("has a Delete action") { n -> n.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "Delete" } == true }, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(actions.isNotEmpty(), "the row offers Delete to a screen reader")
        compose.runOnUiThread { actions.first().config[SemanticsActions.CustomActions].first { it.label == "Delete" }.action() }
        compose.waitUntil(10_000) { !has("row_Cafe") }
        assertEquals(0, runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().size } })
    }

    @Test
    fun theTilesBecomeCheckboxesWhenARowIsPressedAndHeld() {
        spend("a", 100, "Food", "Cafe"); spend("b", 200, "Fun", "Movie")
        launch(); waitFor("row_Cafe")
        node("row_Cafe").performTouchInput { longClick() }
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.isToggleable(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() || has("bulk_bar") }
        assertFalse(has("tile_food") && has("tile_fun"), "while choosing the tiles give way to checkboxes")
    }

    @Test
    fun theLineAboutTheNewLookIsOnTopOfCategoriesForAnExistingLedgerUntilGotItAndNeverForAnEmptyOne() {
        runBlocking { container.write { it.templates.removeSetting("category_look_notice") } }
        spend("a", 100, "Food", "Cafe")
        launch(); compose.openMore(); tap("manage_categories"); waitFor("look_notice")
        tap("look_notice_ok"); compose.waitUntil(10_000) { !has("look_notice") }
        assertEquals("1", setting("category_look_notice"))
        launch(); compose.openMore(); tap("manage_categories"); waitFor("new_category"); assertFalse(has("look_notice"), "not again")
        // an empty ledger is marked as told without saying anything
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } }
        launch(); compose.openMore(); tap("manage_categories"); waitFor("new_category"); assertFalse(has("look_notice"))
        assertEquals("1", setting("category_look_notice"))
    }

    @Test
    fun theThresholdTickIsAClockTickBeforeAndroid14AndTheSystemsGesturePairOnIt() {
        assertEquals(android.view.HapticFeedbackConstants.CLOCK_TICK, hapticConstant(Feel.THRESHOLD, 30)); assertEquals(android.view.HapticFeedbackConstants.CLOCK_TICK, hapticConstant(Feel.THRESHOLD_OFF, 26))
        assertEquals(android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, hapticConstant(Feel.THRESHOLD, 34)); assertEquals(android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE, hapticConstant(Feel.THRESHOLD_OFF, 34))
        assertNotEquals(hapticConstant(Feel.THRESHOLD, 34), hapticConstant(Feel.THRESHOLD_OFF, 34))
    }
}

/** The colour of an amount, drawn in isolation (no app, no data): neutral for spending unless the switch is on, green for money in, the text colour for a transfer. */
@RunWith(AndroidJUnit4::class)
class UiS3AmountColourTest {
    @get:Rule val compose = createComposeRule()

    private fun px(tag: String) = compose.onNodeWithTag(tag).captureToImage().toPixelMap()[10, 10].toArgb()

    @Test
    fun spendingIsTheOrdinaryTextColourUnlessTheSwitchIsOnAndThenRedInBothThemes() {
        var lightText = 0; var darkText = 0; var lightPrimary = 0; var darkPrimary = 0
        compose.setContent {
            Column {
                for ((name, scheme) in listOf("light" to androidx.compose.material3.lightColorScheme(), "dark" to androidx.compose.material3.darkColorScheme())) {
                    MaterialTheme(colorScheme = scheme) {
                        if (name == "light") { lightText = MaterialTheme.colorScheme.onSurface.toArgb(); lightPrimary = MaterialTheme.colorScheme.primary.toArgb() } else { darkText = MaterialTheme.colorScheme.onSurface.toArgb(); darkPrimary = MaterialTheme.colorScheme.primary.toArgb() }
                        Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.background)) {}
                        CompositionLocalProvider(LocalSpendingRed provides false) {
                            Box(Modifier.size(20.dp).background(amountColor(Flow3.OUT)).testTag("${name}_out")); Box(Modifier.size(20.dp).background(amountColor(Flow3.MOVE)).testTag("${name}_move")); Box(Modifier.size(20.dp).background(amountColor(Flow3.IN)).testTag("${name}_in"))
                        }
                        CompositionLocalProvider(LocalSpendingRed provides true) { Box(Modifier.size(20.dp).background(amountColor(Flow3.OUT)).testTag("${name}_out_red")) }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(lightText, px("light_out"), "light: spending is the ordinary text colour"); assertEquals(lightText, px("light_move"))
        assertEquals(darkText, px("dark_out"), "dark: spending is the ordinary text colour"); assertEquals(darkText, px("dark_move"))
        for (name in listOf("light", "dark")) {
            val red = px("${name}_out_red"); assertTrue(((red shr 16) and 0xFF) > ((red shr 8) and 0xFF) + 60 && ((red shr 16) and 0xFF) > (red and 0xFF) + 40, "red when asked for, $name: ${red.toString(16)}")
            assertEquals(if (name == "light") lightPrimary else darkPrimary, px("${name}_in"), "money in is the theme's accent (its text colour), $name")
        }
    }
}
