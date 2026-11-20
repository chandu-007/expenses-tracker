package app.expenses

import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Appearances
import app.expenses.core.storage.BackupStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** UI S9 (D18, U-25, U-18) on the device: the true-black theme, the wallpaper accent, the Appearance preview, the page summaries. Invented data only. */
@RunWith(AndroidJUnit4::class)
class UiS9ThemeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun appearance() = runBlocking { container.write { it.templates.setting("appearance") } }
    private fun appearancePage() { compose.openMore(); tap("open_appearance"); waitFor("appearance_preview") }

    /** The colour of the screen's own background at the right edge, halfway down (the page's padding is there, so it is the background and nothing drawn on it). */
    private fun edgeRgb(): Int {
        val img = compose.onRoot(useUnmergedTree = true).captureToImage().toPixelMap()
        val c = img[img.width - 2, img.height / 2]
        return ((c.red * 255 + 0.5f).toInt() shl 16) or ((c.green * 255 + 0.5f).toInt() shl 8) or (c.blue * 255 + 0.5f).toInt()
    }

    @Test
    fun theBlackDarkThemeIsOffByDefaultAndOnItMakesTheDarkBackgroundTrueBlack() {
        runBlocking { container.write { it.templates.putSetting("appearance", "DARK;violet;COMFORTABLE") } }
        launch(); appearancePage()
        node("black_row").assertIsOff()
        assertEquals(Appearances.DARK_SURFACE, edgeRgb(), "the ordinary dark background first")
        tap("black_row"); compose.waitUntil(10_000) { appearance() == "DARK;violet;COMFORTABLE;BLACK" }
        node("black_row").assertIsOn()
        compose.waitUntil(10_000) { edgeRgb() == 0x000000 }
        // the light theme does not change with it
        tap("theme_LIGHT"); compose.waitUntil(10_000) { appearance() == "LIGHT;violet;COMFORTABLE;BLACK" }
        compose.waitUntil(10_000) { edgeRgb() != 0x000000 }
        assertTrue("appearance" in BackupStore.SETTINGS, "the one appearance setting, with the black theme and the accent, is carried by a backup")
    }

    @Test
    fun theWallpaperAccentIsOfferedFromAndroid12AndBecomesTheChoice() {
        launch(); appearancePage()
        if (Build.VERSION.SDK_INT < 31) { assertFalse(has("accent_wallpaper"), "no wallpaper colour before Android 12"); return }
        tap("accent_wallpaper"); compose.waitUntil(10_000) { appearance()?.split(';')?.getOrNull(1) == "wallpaper" }
        waitFor("accent_wallpaper_line")
        assertEquals("Wallpaper, chosen", node("accent_wallpaper").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString())
        tap("accent_teal"); compose.waitUntil(10_000) { appearance()?.split(';')?.getOrNull(1) == "teal" }
        assertFalse(has("accent_wallpaper_line"))
    }

    @Test
    fun thePreviewIsAnExampleAScreenReaderHearsAsOneAndNeverItsFigures() {
        launch(); appearancePage()
        val said = node("appearance_preview").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
        assertEquals("Example of the look you chose, not your data", said)
        assertTrue(compose.onAllNodes(hasTestTag("appearance_preview"), useUnmergedTree = true).fetchSemanticsNodes().single().children.isEmpty(), "its rows and figures are not read one by one")
        // the swatches are at least 48 dp now
        val swatch = node("accent_violet").fetchSemanticsNode().size
        val density = context.resources.displayMetrics.density
        assertTrue(swatch.width >= 48 * density - 1 && swatch.height >= 48 * density - 1, "accent swatch $swatch")
    }

    @Test
    fun theLongPagesLeadWithOneSentenceAndKeepTheirExplanationsBehindDetails() {
        launch()
        compose.openMore(); tap("open_data"); waitFor("data_summary")
        assertFalse(compose.onAllNodes(androidx.compose.ui.test.hasText("importing the same file twice adds nothing new", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "behind Details until asked for")
        tap("data_import_details")
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("importing the same file twice adds nothing new", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Expanded", node("data_import_details").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        // the sentence nobody may miss stays on the page
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Uninstalling the app deletes the data on this phone", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        scenario?.close(); launch()
        compose.openMore(); tap("open_notifications"); waitFor("notif_summary"); waitFor("notif_details")
    }
}
