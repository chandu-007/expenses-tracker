// core/domain/src/commonTest/kotlin/app/expenses/core/domain/AliasChainDepthTest.kt   (fails today; passes with ReviewLearning.diff, PayeeAliases.problemFor)
package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AliasChainDepthTest {
    private fun a(from: String, to: String) = PayeeAlias(from, to, from, to)

    @Test
    fun aChainLongerThanTheHopLimitIsRefusedSoEveryNameOfAShopReachesTheShop() {
        var table = PayeeAliases.NONE
        // n0 -> n1 -> ... -> n8: eight steps, the longest chain resolve() follows to the end
        for (i in 0 until PayeeAliases.MAX_HOPS) {
            assertNull(table.problemFor("n$i", "n${i + 1}"), "step $i")
            table = table.with(a("n$i", "n${i + 1}"))
        }
        assertEquals("n${PayeeAliases.MAX_HOPS}", table.resolve("n0"))
        // one more step at the end, or at the start, would leave n0 (or the new name) stopping short of its shop
        assertNotNull(table.problemFor("n${PayeeAliases.MAX_HOPS}", "xx"), "a ninth step at the end")
        assertNotNull(table.problemFor("ww", "n0"), "a ninth step at the start")
        // joining straight to the shop is still fine
        assertNull(table.problemFor("ww", "n${PayeeAliases.MAX_HOPS}"))
    }
}
