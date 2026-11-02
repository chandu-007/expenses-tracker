package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The one-letter category suffix of a registered sender header (T transactional, S service, P promotional, G government). Every header is invented or a public bank header. */
class SenderSuffixTest {
    @Test
    fun theSuffixIsTheLetterAfterTheRootInAnyCaseAndNothingElse() {
        assertEquals('S', SenderRules.headerSuffix("AX-HDFCBK-S")); assertEquals('T', SenderRules.headerSuffix("BP-ICICIT-T")); assertEquals('P', SenderRules.headerSuffix("VM-MYSHOP-P"))
        assertEquals('G', SenderRules.headerSuffix("DL-GOVTIN-G")); assertEquals('S', SenderRules.headerSuffix(" jd-icicib-s ")); assertEquals('S', SenderRules.headerSuffix("VM-SBIINB-S"))
        assertEquals("ICICIT", SenderRules.headerRoot("BP-ICICIT-T"), "the root is unchanged by this")
    }

    @Test
    fun aHeaderWithNoSuffixAPhoneNumberAnEmailAndNonsenseHaveNone() {
        assertNull(SenderRules.headerSuffix("VM-SBIINB")); assertNull(SenderRules.headerSuffix("HDFCBK")); assertNull(SenderRules.headerSuffix("+919876543210")); assertNull(SenderRules.headerSuffix("09876543210"))
        assertNull(SenderRules.headerSuffix("alerts@bank.example")); assertNull(SenderRules.headerSuffix("")); assertNull(SenderRules.headerSuffix("AX-AB-S"), "a root of two letters is no header")
    }
}
