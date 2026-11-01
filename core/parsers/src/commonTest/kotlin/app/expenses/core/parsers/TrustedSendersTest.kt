package app.expenses.core.parsers

import app.expenses.core.parsers.TrustedSenders.AddResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrustedSendersTest {
    @Test fun parseReadsEveryOldSeparatorCaseAndRepeat() {
        assertEquals(listOf("MYCOOP", "OTHERCO", "THIRDCO"), TrustedSenders.parse("mycoop, otherco\nThirdCo"))
        assertEquals(listOf("MYCOOP", "OTHERCO"), TrustedSenders.parse("mycoop\notherco\n"))
        assertEquals(listOf("AAA", "BBB"), TrustedSenders.parse(" aaa ;bbb\r\n\t AAA ,, \n"))
        assertEquals(emptyList(), TrustedSenders.parse(null))
        assertEquals(emptyList(), TrustedSenders.parse(" \n , "))
    }

    @Test fun addNormalisesAFullHeaderToItsRoot() {
        val r = TrustedSenders.add("", " ax-mybank-s ") as AddResult.Added
        assertEquals("MYBANK", r.name)
        assertEquals("MYBANK", r.stored)
        val r2 = TrustedSenders.add("mycoop, otherco", "newco") as AddResult.Added
        assertEquals("MYCOOP\nOTHERCO\nNEWCO", r2.stored)
    }

    @Test fun addResultTypes() {
        assertEquals(AddResult.Empty, TrustedSenders.add("", "   "))
        assertEquals(AddResult.Invalid, TrustedSenders.add("", "ab"))
        assertEquals(AddResult.Invalid, TrustedSenders.add("", "my bank"))
        assertEquals(AddResult.Invalid, TrustedSenders.add("", "+919800000000"))
        assertEquals(AddResult.Invalid, TrustedSenders.add("", "bad!name"))
        assertEquals(AddResult.Invalid, TrustedSenders.add("", "THIRTEENCHARS"))
        assertEquals(AddResult.Duplicate("MYCOOP"), TrustedSenders.add("MyCoop\nother", "VM-mycoop-S"))
        val b = TrustedSenders.add("", "HDFCBK")
        assertTrue(b is AddResult.BuiltIn && b.name == "HDFCBK" && b.issuer == SenderRules.BUILT_IN["HDFCBK"])
    }

    @Test fun removeKeepsTheOthers() {
        assertEquals("OTHERCO", TrustedSenders.remove("mycoop, otherco", "MYCOOP"))
        assertEquals("", TrustedSenders.remove("mycoop", "mycoop"))
        assertEquals("MYCOOP\nOTHERCO", TrustedSenders.remove("mycoop\notherco", "absent"))
    }

    @Test fun roundTrip() {
        var s = ""
        for (n in listOf("alpha1", "BETA2", "gamma3")) s = (TrustedSenders.add(s, n) as AddResult.Added).stored
        assertEquals(listOf("ALPHA1", "BETA2", "GAMMA3"), TrustedSenders.parse(s))
        s = TrustedSenders.remove(s, "BETA2")
        assertEquals(listOf("ALPHA1", "GAMMA3"), TrustedSenders.parse(s))
        assertEquals(s, TrustedSenders.serialize(TrustedSenders.parse(s)))
    }
}
