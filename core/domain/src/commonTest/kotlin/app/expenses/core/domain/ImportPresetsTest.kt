package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sample files are invented; they follow the column layouts described in [ImportPresets]. */
class ImportPresetsTest {
    private fun read(text: String): ImportPresets.Read? {
        val t = Csv.parse(text.trimIndent())
        return ImportPresets.read(t.rows, t.lines, "INR", 2)
    }

    private fun parsed(r: ImportPresets.Read) = StatementParser.parse(r.table.rows, r.mapping, 2, { r.table.lines[it] }, { r.rawLines.getOrNull(it) })

    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    @Test
    fun aMoneyManagerExportIsReadWithItsMonthFirstDatesCategoriesAndAccounts() {
        val r = assertNotNull(read("""
            Date,Account,Category,Subcategory,Note,Amount,Income/Expense,Description
            10/05/2026,HDFC Savings,Food,Eating out,Lunch with team,450.00,Expense,office
            10/06/2026,HDFC Savings,Salary,,October pay,85000.00,Income,
            10/07/2026,HDFC Savings,Cash,,ATM,2000.00,Transfer-Out,
            10/08/2026,Wallet,Transport,,Metro,60.50,Expense,
        """))
        assertEquals(ImportPresets.App.MONEY_MANAGER, r.app)
        assertEquals(DateOrder.MDY, r.mapping.dateOrder, "the template's date is month first")
        assertEquals(1, r.transfers)
        val p = parsed(r)
        assertEquals(emptyList(), p.problems)
        assertEquals(listOf(d(2026, 10, 5), d(2026, 10, 6), d(2026, 10, 8)), p.txns.map { it.date })
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME, Flow.SPENDING), p.txns.map { it.flow })
        assertEquals(listOf(45_000L, 8_500_000L, 6_050L), p.txns.map { it.minor })
        assertEquals(listOf("Lunch with team", "October pay", "Metro"), p.txns.map { it.description })
        assertEquals(listOf("Food", "Salary", "Transport"), p.txns.map { it.category })
        assertEquals(listOf("Eating out", null, null), p.txns.map { it.subcategory })
        assertEquals(listOf("HDFC Savings", "HDFC Savings", "Wallet"), p.txns.map { it.accountName })
        assertEquals(listOf("office", null, null), p.txns.map { it.note })
        assertEquals("10/05/2026, HDFC Savings, Food, Eating out, Lunch with team, 450.00, Expense, office", p.txns[0].sourceText, "the line as the file had it")
        assertEquals(listOf(2, 3, 5), p.txns.map { it.row }, "rows keep the file's own line numbers, though the transfer on line 4 was left out")
    }

    @Test
    fun theRowsKeepTheLineNumbersOfTheFileSoAProblemPointsAtTheRightLine() {
        val r = assertNotNull(read("""
            Date,Account,Category,Subcategory,Note,Amount,Income/Expense,Description
            10/05/2026,HDFC Savings,Food,,Lunch,450.00,Expense,
            10/07/2026,HDFC Savings,Cash,,ATM,2000.00,Transfer-Out,
            10/08/2026,HDFC Savings,Food,,Tea,abc,Expense,
        """))
        assertEquals(listOf(4), parsed(r).problems.map { it.row }, "line 4 of the file, though one row before it was left out")
        assertEquals(listOf(2), parsed(r).txns.map { it.row })
    }

    @Test
    fun aYnabRegisterReadsOutflowAndInflowAndLeavesTransfersOut() {
        val r = assertNotNull(read("""
            "Account","Flag","Date","Payee","Category Group/Category","Category Group","Category","Memo","Outflow","Inflow","Cleared"
            "Checking","","10/25/2026","Corner Grocer","Everyday: Groceries","Everyday","Groceries","weekly shop","₹1,200.50","₹0.00","Cleared"
            "Checking","","10/26/2026","Acme Payroll","Inflow: Ready to Assign","Inflow","Ready to Assign","","₹0.00","₹50,000.00","Cleared"
            "Checking","","10/27/2026","Transfer : Savings","","","","","₹5,000.00","₹0.00","Cleared"
            "Savings","","10/27/2026","Transfer : Checking","","","","","₹0.00","₹5,000.00","Cleared"
        """))
        assertEquals(ImportPresets.App.YNAB, r.app)
        assertEquals(2, r.transfers)
        assertEquals(DateOrder.MDY, r.mapping.dateOrder, "25 can only be a day, so the order is month first")
        val p = parsed(r)
        assertEquals(emptyList(), p.problems)
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME), p.txns.map { it.flow })
        assertEquals(listOf(120_050L, 5_000_000L), p.txns.map { it.minor })
        assertEquals(listOf("Corner Grocer", "Acme Payroll"), p.txns.map { it.description })
        assertEquals(listOf("Groceries", "Ready to Assign"), p.txns.map { it.category })
        assertEquals(listOf("weekly shop", null), p.txns.map { it.note })
        assertEquals(listOf("Checking", "Checking"), p.txns.map { it.accountName })
    }

    @Test
    fun aSplitwiseExportTakesTheWholeCostAndSetsOtherCurrenciesAside() {
        val r = assertNotNull(read("""
            Date,Description,Category,Cost,Currency,Asha,Ravi
            2026-10-05,Dinner,Dining out,1200.00,INR,800.00,-800.00
            2026-10-06,Hotel,Travel,95.00,USD,50.00,-50.00
            2026-10-07,Taxi,Transportation,350,INR,-175.00,175.00

            ,Total balance,,,INR,625.00,-625.00
        """))
        assertEquals(ImportPresets.App.SPLITWISE, r.app)
        assertEquals(1, r.otherCurrency)
        assertTrue(r.note!!.contains("whole cost"))
        val p = parsed(r)
        assertEquals(emptyList(), p.problems)
        assertEquals(listOf(120_000L, 35_000L), p.txns.map { it.minor })
        assertEquals(listOf(Flow.SPENDING, Flow.SPENDING), p.txns.map { it.flow })
        assertEquals(listOf("Dining out", "Transportation"), p.txns.map { it.category })
        assertEquals(DateOrder.YMD, r.mapping.dateOrder)
        assertEquals(1, p.ignoredRows, "the balance row has no cost")
    }

    @Test
    fun aCashewExportUsesItsIncomeColumnAndLeavesBalanceCorrectionsAndUpcomingRowsOut() {
        val r = assertNotNull(read("""
            account,amount,currency,title,note,date,income,type,category name,subcategory name
            Cash,-250.50,INR,Coffee beans,from the market,2026-10-05 09:30:00.000,false,0,Food,Coffee
            Bank,50000,INR,October salary,,2026-10-06 10:00:00.000,true,0,Salary,
            Bank,-3000,INR,To wallet,,2026-10-06 11:00:00.000,false,0,Balance Correction,
            Bank,,INR,Netflix,,2026-10-30 00:00:00.000,false,upcoming,Entertainment,
            Bank,-12,USD,Domain,,2026-10-07 00:00:00.000,false,0,Tech,
        """))
        assertEquals(ImportPresets.App.CASHEW, r.app)
        assertEquals(1, r.transfers); assertEquals(1, r.otherCurrency)
        val p = parsed(r)
        assertEquals(emptyList(), p.problems)
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME), p.txns.map { it.flow }, "the sign of the amount is not what decides; the income column is")
        assertEquals(listOf(25_050L, 5_000_000L), p.txns.map { it.minor })
        assertEquals(listOf("Food", "Salary"), p.txns.map { it.category }); assertEquals(listOf("Coffee", null), p.txns.map { it.subcategory })
        assertEquals(1, p.ignoredRows, "the upcoming row has no amount yet")
        assertEquals(listOf("Cash", "Bank"), p.txns.map { it.accountName })
        assertEquals(d(2026, 10, 5), p.txns[0].date)
    }

    @Test
    fun aSpendeeExportIsReadByItsTypeLabelsAndLongDecimals() {
        val r = assertNotNull(read("""
            Date,Wallet,Type,Category name,Amount,Currency,Note,Labels,Author
            2026-10-05T15:06:00+00:00,Cash Wallet,Expense,Groceries,154.35000000,INR,Walmart,"weekly, home",Me
            2026-10-06T09:00:00+00:00,Cash Wallet,Income,Salary,10000.00000000,INR,October,,Me
            2026-10-07T09:00:00+00:00,Cash Wallet,Outgoing Transfer,Transfer,500.00000000,INR,,,Me
            2026-10-07T09:00:00+00:00,Bank,Incoming Transfer,Transfer,500.00000000,INR,,,Me
        """))
        assertEquals(ImportPresets.App.SPENDEE, r.app)
        assertEquals(2, r.transfers)
        val p = parsed(r)
        assertEquals(emptyList(), p.problems)
        assertEquals(listOf(15_435L, 1_000_000L), p.txns.map { it.minor })
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME), p.txns.map { it.flow })
        assertEquals(listOf("Walmart", "October"), p.txns.map { it.description })
        assertEquals(listOf("weekly", "home"), p.txns[0].tags); assertEquals(emptyList(), p.txns[1].tags)
        assertEquals(listOf(d(2026, 10, 5), d(2026, 10, 6)), p.txns.map { it.date })
    }

    @Test
    fun aSpendeeExportWithoutATypeColumnReadsTheSignOfTheAmount() {
        val r = assertNotNull(read("""
            Date,Wallet,Category name,Amount,Currency,Note
            2026-10-05T15:06:00+00:00,Cash,Groceries,-154.35,INR,Walmart
            2026-10-06T09:00:00+00:00,Cash,Salary,1000,INR,Pay
        """))
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME), parsed(r).txns.map { it.flow })
    }

    @Test
    fun aBankStatementOrAnyOtherFileIsNotTakenForAnotherAppsExport() {
        assertNull(read("Date,Narration,Withdrawal Amt.,Deposit Amt.\n01/10/26,UPI-SWIGGY,250.00,\n"))
        assertNull(read("Date,Description,Amount\n01/10/26,SHOP,-5.00\n"))
        assertNull(read("Date,Account,Category,Amount\n01/10/26,Bank,Food,5.00\n"), "some of the titles are not enough")
        assertNull(read("Date,Payee,Memo,Amount\n01/10/26,Shop,x,5.00\n"), "YNAB needs Outflow and Inflow")
    }

    @Test
    fun theHeaderMayComeAfterTitleRowsAndOnlyTheRowsBelowItAreRead() {
        val r = assertNotNull(read("""
            Exported by the app,,,,,,,
            ,,,,,,,
            Date,Account,Category,Subcategory,Note,Amount,Income/Expense,Description
            10/05/2026,Cash,Food,,Tea,20.00,Expense,
        """))
        assertEquals(1, parsed(r).txns.size)
        assertEquals(listOf(4), parsed(r).txns.map { it.row }, "line 4 of the file")
    }

    @Test
    fun anIncomeOrExpenseValueInAnotherLanguageIsNeverGuessedToBeSpending() {
        val r = assertNotNull(read("""
            Date,Account,Category,Subcategory,Note,Amount,Income/Expense,Description
            10/05/2026,Bank,Food,,Lunch,450.00,Ausgabe,
            10/06/2026,Bank,Salary,,Pay,85000.00,Einnahmen,
            10/07/2026,Bank,Food,,Tea,20.00,Exp.,
            10/08/2026,Bank,Food,,Dinner,300.00,Expense,
            10/09/2026,Bank,Salary,,Bonus,100.00,Income,
            10/10/2026,Bank,Cash,,ATM,100.00,Transfer-Out,
            10/11/2026,Bank,Food,,Blank,100.00,,
        """))
        val p = parsed(r)
        assertEquals(listOf("Dinner", "Bonus"), p.txns.map { it.description }); assertEquals(listOf(Flow.SPENDING, Flow.INCOME), p.txns.map { it.flow })
        assertEquals(4, r.unrecognised); assertEquals(1, r.transfers)
        assertEquals("Left out: 1 transfer between your own accounts, 4 rows whose Income/Expense/Transfer value was not recognised.", r.leftOut)
    }

    @Test
    fun aMoneyManagerRowInAnotherCurrencyIsLeftOutNotReadAsRupees() {
        val r = assertNotNull(read("""
            Date,Account,Category,Subcategory,Note,Amount,Income/Expense,Description,Amount Sub,Currency
            10/05/2026,Bank,Food,,Hotel,830.00,Expense,,9.13,EUR
            10/06/2026,Bank,Food,,Tea,20.00,Expense,,,INR
            10/07/2026,Bank,Food,,Bun,15.00,Expense,,,
        """))
        assertEquals(1, r.otherCurrency)
        assertEquals(listOf("Tea", "Bun"), parsed(r).txns.map { it.description })
        assertEquals("Left out: 1 row in another currency.", r.leftOut)
    }

    @Test
    fun aSpendeeTypeInAnotherLanguageIsLeftOutAndAnEmptyOneReadsTheSign() {
        val r = assertNotNull(read("""
            Date,Wallet,Type,Category name,Amount,Currency,Note,Labels,Author
            2026-10-05T15:06:00+00:00,Cash,Gasto,Groceries,154.35,INR,Walmart,,Me
            2026-10-06T09:00:00+00:00,Cash,,Salary,-10.00,INR,Fee,,Me
            2026-10-07T09:00:00+00:00,Cash,Income,Salary,1000.00,INR,Pay,,Me
        """))
        assertEquals(1, r.unrecognised)
        assertEquals(listOf("Fee", "Pay"), parsed(r).txns.map { it.description }); assertEquals(listOf(Flow.SPENDING, Flow.INCOME), parsed(r).txns.map { it.flow })
    }

    @Test
    fun splitwiseSettleUpsAreLeftOutAndTheWholeCostIsStillWarnedAbout() {
        val r = assertNotNull(read("""
            Date,Description,Category,Cost,Currency,Asha,Ravi
            2026-10-05,Dinner,Dining out,1200.00,INR,800.00,-800.00
            2026-10-06,Payment,Payment,500.00,INR,-500.00,500.00
            2026-10-07,Ravi paid Asha,payment,250.00,INR,-250.00,250.00
            2026-10-08,Taxi,Transportation,350.00,INR,-175.00,175.00
        """))
        assertEquals(2, r.settleUps)
        assertEquals(listOf("Dinner", "Taxi"), parsed(r).txns.map { it.description })
        assertEquals("Left out: 2 settle-up payments between friends.", r.leftOut)
        assertTrue(r.note!!.contains("whole cost") && r.note!!.contains("not your share"))
    }

    @Test
    fun nothingLeftOutSaysNothing() {
        assertNull(assertNotNull(read("Date,Description,Category,Cost,Currency,Asha\n2026-10-05,Dinner,Dining out,1200.00,INR,800.00\n")).leftOut)
    }
}
