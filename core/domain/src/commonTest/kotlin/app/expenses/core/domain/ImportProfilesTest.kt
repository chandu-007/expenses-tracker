package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportProfilesTest {
    private val full = StatementMapping(3, 0, DateOrder.MDY, 2, AmountLayout.SEPARATE_DEBIT_CREDIT, 4, 5, null, null, SignConvention.POSITIVE_IS_SPENDING)

    @Test
    fun theSignatureIgnoresCaseAndSpacingButNotOrderOrNames() {
        assertEquals(ImportProfiles.signature(listOf("Date ", " Narration", "Withdrawal  Amt.")), ImportProfiles.signature(listOf("date", "narration", "withdrawal amt.")))
        assertEquals(false, ImportProfiles.signature(listOf("Date", "Narration")) == ImportProfiles.signature(listOf("Narration", "Date")))
        assertNull(ImportProfiles.signature(listOf("", "Date")), "a row with fewer than two titles is not a header")
    }

    @Test
    fun aMappingRoundTripsAndAnythingElseDecodesToNothing() {
        assertEquals(full, ImportProfiles.decode(ImportProfiles.encode(full), 6))
        val amountType = StatementMapping(1, 2, DateOrder.DMY, null, AmountLayout.AMOUNT_WITH_TYPE, null, null, 3, 4)
        assertEquals(amountType, ImportProfiles.decode(ImportProfiles.encode(amountType), 5))
        assertNull(ImportProfiles.decode(null, 6)); assertNull(ImportProfiles.decode("garbage", 6)); assertNull(ImportProfiles.decode("", 6))
        assertNull(ImportProfiles.decode(ImportProfiles.encode(full), 4), "a column beyond the file's width is not trusted")
        assertNull(ImportProfiles.decode(ImportProfiles.encode(full).replace("MDY", "XYZ"), 6), "an unknown date order")
        assertNull(ImportProfiles.decode(ImportProfiles.encode(full.copy(debitColumn = null)), 6), "an incomplete mapping is not applied")
    }

    @Test
    fun excelDatesConvertAcrossTheLeapYearBugAndCenturies() {
        assertEquals("1900-01-01", ExcelDates.serialToIso(1.0)); assertEquals("1900-02-28", ExcelDates.serialToIso(59.0))
        assertEquals("1900-03-01", ExcelDates.serialToIso(61.0)); assertEquals("1970-01-01", ExcelDates.serialToIso(25_569.0))
        assertEquals("2026-10-07", ExcelDates.serialToIso(46_302.0)); assertEquals("2026-10-07", ExcelDates.serialToIso(46_302.75), "the time of day is dropped")
        assertEquals("2000-02-29", ExcelDates.serialToIso(36_585.0)); assertEquals("9999-12-31", ExcelDates.serialToIso(2_958_465.0))
        assertNull(ExcelDates.serialToIso(0.0)); assertNull(ExcelDates.serialToIso(-5.0)); assertNull(ExcelDates.serialToIso(3_000_000.0)); assertNull(ExcelDates.serialToIso(Double.NaN))
    }

    @Test
    fun aNumberFormatMakesADateOnlyWhenItShowsDayMonthOrYear() {
        assertEquals(true, ExcelDates.isDateFormat(14, null)); assertEquals(true, ExcelDates.isDateFormat(22, null)); assertEquals(false, ExcelDates.isDateFormat(0, null)); assertEquals(false, ExcelDates.isDateFormat(2, null))
        assertEquals(true, ExcelDates.isDateFormat(171, "yyyy")); assertEquals(true, ExcelDates.isDateFormat(172, "dddd")); assertEquals(true, ExcelDates.isDateFormat(164, "dd-mmm-yyyy")); assertEquals(true, ExcelDates.isDateFormat(165, "d/m/yy")); assertEquals(true, ExcelDates.isDateFormat(166, "[\$-409]mmmm d, yyyy;@"))
        assertEquals(false, ExcelDates.isDateFormat(167, "#,##0.00")); assertEquals(false, ExcelDates.isDateFormat(168, "\"Day\" 0")); assertEquals(false, ExcelDates.isDateFormat(169, "0.00%")); assertEquals(false, ExcelDates.isDateFormat(170, "[Red]#,##0.00"))
    }
}
