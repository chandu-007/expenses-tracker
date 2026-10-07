package app.expenses.core.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Screens must use the app's own `Text` (it hides amounts in privacy mode), not Material's. An IDE auto-import of `material3.Text` would
 * silently show real amounts, so this fails the build if any screen file imports it.
 */
class UiConventionsTest {
    private val ui = listOf(File("../../androidApp/src/main/kotlin/app/expenses/ui"), File("androidApp/src/main/kotlin/app/expenses/ui")).first { it.isDirectory }

    @Test
    fun noScreenImportsMaterialsTextBypassingTheAmountMask() {
        val offenders = ui.walkTopDown().filter { it.extension == "kt" }.filter { f -> f.readLines().any { it.trim() == "import androidx.compose.material3.Text" } }.map { it.name }.toList()
        assertTrue(offenders.isEmpty(), "these import material3.Text and would show amounts in privacy mode: $offenders")
        assertTrue(ui.walkTopDown().count { it.extension == "kt" } > 10, "the screens were found")
    }
}
