package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackMailTest {
    @Test fun subjectNamesVersionAndPlatform() {
        assertEquals("Expenses feedback (version 0.6.0, Android)", FeedbackMail.subject("0.6.0", "Android 15"))
        assertEquals("Expenses feedback (version 0.6.0, iPhone)", FeedbackMail.subject("0.6.0", "iOS 19.1"))
    }

    @Test fun urlIsMailtoWithEncodedSubjectAndBody() {
        val url = FeedbackMail.build("0.6.0", "Android 15", "Google Pixel 8")
        assertTrue(url.startsWith("mailto:nelluri.c@gmail.com?subject=Expenses%20feedback%20%28version%200.6.0%2C%20Android%29&body="), url)
        assertFalse(url.contains(' ') || url.contains('\n') || url.contains(','), url)
        assertTrue(url.contains("body=Describe%20what%20happened%3A%0A"), url)
        assertTrue(url.contains("App%20version%3A%200.6.0%0A"), url)
        assertTrue(url.contains("System%3A%20Android%2015%0A"), url)
        assertTrue(url.endsWith("Device%3A%20Google%20Pixel%208%0A"), url)
    }

    @Test fun aMessageReportIsMailtoWithTheRedactedTextEncodedAndNothingElse() {
        val url = FeedbackMail.buildReport("0.7.2", "iOS 19.1", "Sender: AX-NEWBNK-S\nRs.NNN.NN & more")
        assertTrue(url.startsWith("mailto:nelluri.c@gmail.com?subject=Expenses%3A%20a%20bank%20message%20to%20fix%20%28version%200.7.2%2C%20iPhone%29&body=Sender%3A%20AX-NEWBNK-S%0ARs.NNN.NN%20%26%20more"), url)
        assertEquals(1, url.count { it == '?' }); assertEquals(2, url.count { it == '=' }); assertEquals(1, url.count { it == '&' })
        assertFalse(url.contains(' ') || url.contains('\n'), url)
    }

    @Test fun nothingButVersionSystemAndModelIsInTheBody() {
        val body = FeedbackMail.body("0.6.0", "iOS 19.1", "iPhone")
        assertEquals("Describe what happened:\n\n\n\n---\nApp version: 0.6.0\nSystem: iOS 19.1\nDevice: iPhone\n", body)
    }

    @Test fun encodingKeepsUnreservedAndEncodesTheRestAsUtf8() {
        assertEquals("AZaz09-._~", FeedbackMail.encode("AZaz09-._~"))
        assertEquals("%20%26%3D%3F%23%25%2B%2F", FeedbackMail.encode(" &=?#%+/"))
        assertEquals("%E2%82%B9%C3%A9", FeedbackMail.encode("₹é"))
        assertEquals("%F0%9F%99%82", FeedbackMail.encode("🙂"))
    }

    @Test fun modelWithSpecialCharactersCannotBreakTheUrl() {
        val url = FeedbackMail.build("1", "Android 14", "A&B=C?D#E")
        assertEquals(1, url.count { it == '?' })
        assertEquals(2, url.count { it == '=' } )
        assertEquals(1, url.count { it == '&' })
        assertFalse(url.contains('#'))
    }
}
