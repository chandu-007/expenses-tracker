package app.expenses.core.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Screens must use the app's own `Text` (it hides amounts in privacy mode), not Material's. An IDE auto-import of `material3.Text` would
 * silently show real amounts, so this fails the build if any screen file imports it.
 */
class UiConventionsTest {
    private val ui = listOf(File("../../shared/src/commonMain/kotlin/app/expenses/ui"), File("shared/src/commonMain/kotlin/app/expenses/ui")).first { it.isDirectory }

    @Test
    fun noScreenImportsMaterialsTextBypassingTheAmountMask() {
        val offenders = ui.walkTopDown().filter { it.extension == "kt" }.filter { f -> f.readLines().any { it.trim() == "import androidx.compose.material3.Text" } }.map { it.name }.toList()
        assertTrue(offenders.isEmpty(), "these import material3.Text and would show amounts in privacy mode: $offenders")
        assertTrue(ui.walkTopDown().count { it.extension == "kt" } > 10, "the screens were found")
    }

    /**
     * The capture checkup ("Check a message" shows an amount), the guided setup and "Bring in earlier spending" (it lists amounts just added) draw every word with the app's own Text, so privacy mode
     * hides those amounts too: none of them names Material's Text, by import or in full.
     */
    @Test
    fun theCheckupSetupAndEarlierSpendingScreensDrawEveryWordWithTheMaskingText() {
        for (name in listOf("CaptureCheckupScreen.kt", "GuidedSetup.kt", "EarlierSpendingScreen.kt")) {
            val file = File(ui, name)
            assertTrue(file.isFile, "$name is in the screens folder")
            val src = file.readText()
            assertTrue(!Regex("""androidx\.compose\.material3\.Text\b""").containsMatchIn(src), "$name names Material's Text, which would show amounts in privacy mode")
            assertTrue(Regex("""(?<![A-Za-z.])Text\(""").containsMatchIn(src), "$name draws its words with Text")
        }
    }
}
