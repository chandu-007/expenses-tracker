package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttachmentRulesTest {
    @Test
    fun namesLoseFoldersControlCharactersAndLeadingDotsAndAreNeverEmptyOrTooLong() {
        assertEquals("receipt.jpg", AttachmentRules.safeName("../../etc/receipt.jpg")); assertEquals("a.pdf", AttachmentRules.safeName("C:\\Users\\me\\a.pdf"))
        assertEquals("bad name.png", AttachmentRules.safeName("bad \u0000na\u0007me.png"))
        assertEquals("hidden", AttachmentRules.safeName("..hidden")); assertEquals("attachment", AttachmentRules.safeName("")); assertEquals("attachment", AttachmentRules.safeName("  ./  ")); assertEquals("attachment", AttachmentRules.safeName(null))
        val long = AttachmentRules.safeName("x".repeat(200) + ".jpeg")
        assertEquals(80, long.length); assertEquals(true, long.endsWith(".jpeg"))
        assertEquals(80, AttachmentRules.safeName("y".repeat(200)).length)
        assertEquals("Café ₹100 bill.jpg", AttachmentRules.safeName("Café ₹100 bill.jpg"))
    }

    @Test
    fun limitsAreEnforcedAtTheirBoundaries() {
        assertNull(AttachmentRules.checkAdd(9, 15L * 1024 * 1024)); assertNull(AttachmentRules.checkAdd(0, null))
        assertEquals("An entry can have up to 10 attachments.", AttachmentRules.checkAdd(10, 1))
        assertEquals("That file is over 15 MB, which is more than a receipt needs.", AttachmentRules.checkAdd(0, 15L * 1024 * 1024 + 1))
        assertEquals("That file is empty.", AttachmentRules.checkAdd(0, 0))
    }

    @Test
    fun sizesReadNaturally() {
        assertEquals("0 B", AttachmentRules.sizeText(0)); assertEquals("1023 B", AttachmentRules.sizeText(1023)); assertEquals("1 KB", AttachmentRules.sizeText(1024)); assertEquals("512 KB", AttachmentRules.sizeText(512 * 1024))
        assertEquals("1.0 MB", AttachmentRules.sizeText(1024 * 1024)); assertEquals("2.5 MB", AttachmentRules.sizeText(2_621_440)); assertEquals("15.0 MB", AttachmentRules.sizeText(15L * 1024 * 1024))
    }
}
