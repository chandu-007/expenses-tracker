package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Narrations are SYNTHETIC, shaped like the examples reported from an HDFC-style statement; every personal name is invented. */
class StatementDescriptionsTest {
    @Test fun counterpartyNames() {
        val cases = listOf(
            "NEFT CR-SBIN0021998-ASHA RAO SAMSTHA-MEERA NAIR-SBIN526274612968" to "ASHA RAO SAMSTHA",
            "UPI-KOTAK MAHINDRA BANK -KOTAK811PAYMENTS@KOTAK-KKBK0000958-980566514098-PAYMENT" to "KOTAK MAHINDRA BANK",
            "ACH D- ZERODHA BROKING LTD-6J5AQXZLH82AK" to "ZERODHA BROKING LTD",
            "UPI-NSE ZERODHA COINS-ZERODHA.NSEINVEST.BRK@VALIDHDFC-HDFC0MERUPI-104184779254-D1PT6GL2IH1LOOJX2E" to "NSE ZERODHA COINS",
            "UPI/DR/123456789/AMAZON PAY INDIA MUMBAI" to "AMAZON PAY INDIA MUMBAI",
            "IMPS-412345678901-ROHAN VERMA-HDFC-0001234-RENT" to "ROHAN VERMA",
            "POS 4147XXXXXX1234 SWIGGY BANGALORE" to "SWIGGY BANGALORE",
        )
        for ((raw, want) in cases) assertEquals(want, StatementDescriptions.tidy(raw), raw)
    }

    @Test fun leftAloneWhenNoNameIsFound() {
        for (raw in listOf("SWIGGY BANGALORE", "NET BANKING SI -PPF SAVINGS FOR RETIREMENT", "UPI-SBIN0021998-123456789012", "NEFT TO RAVI KUMAR REF 998877 HDFC0001234", "ATM WDL", "UPI-SWIGGY".replace("SWIGGY", "S"))) {
            assertEquals(raw, StatementDescriptions.tidy(raw), raw)
        }
        assertEquals("SWIGGY", StatementDescriptions.tidy("UPI-SWIGGY-123"))
        assertEquals("", StatementDescriptions.tidy("   "))
    }

    @Test fun neverEmptyAndIdempotent() {
        val raws = listOf("UPI-", "NEFT CR-", "ACH D- -", "UPI-12345678-HDFC0001234", "UPI-NSE ZERODHA COINS-X@Y-HDFC0MERUPI-104184779254", "SWIGGY BANGALORE")
        for (raw in raws) {
            val once = StatementDescriptions.tidy(raw)
            assertNotEquals("", once, raw)
            assertEquals(once, StatementDescriptions.tidy(once), raw)
        }
    }

    @Test fun wrappedLinesJoinWithoutSpaceOnlyWhenTheColumnWasFilled() {
        // two lines reached the width of 24: cut mid-word, so no space
        assertEquals("NET BANKING SI -PPF SAVINGS", StatementDescriptions.join(listOf("NET BANKING SI -PPF SAV", "INGS"), 24, 2))
        assertEquals("UPI-X-KOTAK811PAYMENTS@KOTAK", StatementDescriptions.join(listOf("UPI-X-KOTAK811PAYMENT", "S@KOTAK"), 22, 2))
        // the first line is shorter than the column: a real word break
        assertEquals("SWIGGY BANGALORE IN", StatementDescriptions.join(listOf("SWIGGY BANGALORE", "IN"), 24, 2))
        // only one filled line in the statement: unsure, keep the space
        assertEquals("NET BANKING SI -PPF SAV INGS", StatementDescriptions.join(listOf("NET BANKING SI -PPF SAV", "INGS"), 24, 1))
        assertEquals("A B", StatementDescriptions.join(listOf("A", "B"), 0, 0))
        // a short first line, but the whole word is printed elsewhere in the statement
        assertEquals("NET BANKING SI -PPF SAVINGS", StatementDescriptions.join(listOf("NET BANKING SI -PPF SAV", "INGS"), 40, 1, setOf("SAVINGS")))
        assertEquals("NET BANKING SI -PPF SAV INGS", StatementDescriptions.join(listOf("NET BANKING SI -PPF SAV", "INGS"), 40, 1, setOf("BANKING")))
    }

    @Test fun parserTidiesWrappedNarrationsButKeepsTheRawLines() {
        val text = """
            Savings Account   Withdrawal Amt. Deposit Amt. Closing Balance
            01/10/26 NET BANKING SI -PPF SAV 15,000.00 85,000.00
            INGS FOR RETIREMENT
            02/10/26 NEFT CR-SBIN0021998-ASHA RAO SA 50,000.00 135,000.00
            MSTHA-MEERA NAIR-SBIN526274612968
            03/10/26 SWIGGY BANGALORE 450.00 134,550.00
        """.trimIndent()
        val r = StatementTextParser.parse(text)
        assertEquals(listOf("NET BANKING SI -PPF SAVINGS FOR RETIREMENT", "ASHA RAO SA MSTHA", "SWIGGY BANGALORE"), r.table.rows.drop(1).map { it[1] })
        assertEquals("02/10/26 NEFT CR-SBIN0021998-ASHA RAO SA 50,000.00 135,000.00\nMSTHA-MEERA NAIR-SBIN526274612968", r.rawLines[2])
    }
}
