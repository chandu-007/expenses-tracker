// core/storage/src/jvmTest/kotlin/app/expenses/core/storage/AliasRestoreChainTest.kt   (fails today; passes with ReviewLearning.diff)
package app.expenses.core.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A hand-made backup with a long chain of aliases must not leave names that stop short of their shop (resolve() follows at most MAX_HOPS steps). */
class AliasRestoreChainTest {
    @Test
    fun aLongChainInAFileNeverLeavesANameThatStopsShortOfItsShop() {
        val chain = (0 until 20).associate { i -> "payee_alias:n${i.toString().padStart(2, '0')}" to "n${(i + 1).toString().padStart(2, '0')}\tName $i\tName ${i + 1}" }
        val d = Device("d"); d.backup.restore(BackupData(settings = chain))
        val have = CaptureRuleStore(d.db).aliases()
        val froms = have.all.map { it.from }.toSet()
        assertTrue(have.all.isNotEmpty())
        // every kept alias ends on a name that is not itself counted as another: no name is left half way along a chain
        for (a in have.all) assertTrue(have.resolve(a.from) !in froms, "${a.from} resolves to ${have.resolve(a.from)}, which counts as another name")
        // and two names that are one shop agree on it
        for (a in have.all) assertEquals(have.resolve(a.from), have.resolve(a.to))
    }
}
