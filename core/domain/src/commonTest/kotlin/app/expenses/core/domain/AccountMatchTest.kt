package app.expenses.core.domain

import app.expenses.core.domain.AccountMatch.Basis
import app.expenses.core.domain.AccountMatch.Candidate
import app.expenses.core.domain.AccountMatch.Result
import app.expenses.core.domain.ledger.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Digits of an account as a bank prints them (three or four) and which account a text's digits point at. Every name, digit and amount here is invented. */
class AccountMatchTest {
    private val banks = BankNames(listOf("ICICI Bank", "HDFC Bank", "SBI", "Axis Bank", "Kotak Mahindra Bank", "IDFC FIRST Bank")) // banks only: a payment app such as Google Pay is no bank
    private fun acct(id: String, name: String, digits: String?, type: AccountType = AccountType.BANK) = Candidate(id, name, type, digits)
    private fun found(r: Result) = (r as? Result.Found)?.accountId

    // ---- the digits themselves ----

    @Test
    fun digitsAreThreeOrFourDigitsAndNothingElse() {
        assertNull(AccountDigits.parse("  ").getOrThrow()); assertEquals("4821", AccountDigits.parse(" 4821 ").getOrThrow()); assertEquals("821", AccountDigits.parse("821").getOrThrow())
        for (bad in listOf("82", "8", "48211", "48a1", "XX48", "4 82", "-123")) assertTrue(AccountDigits.parse(bad).isFailure, bad)
        assertEquals("Type 3 or 4 digits, like 821 or 4821, as your bank's texts show them, or leave it empty.", AccountDigits.parse("1").exceptionOrNull()?.message)
        assertTrue(AccountDigits.isValid("123") && AccountDigits.isValid("1234") && !AccountDigits.isValid("12") && !AccountDigits.isValid("12345") && !AccountDigits.isValid(null))
    }

    @Test
    fun aRunOfDigitsKeepsItsThreeOrTakesItsLastFour() {
        assertEquals("123", AccountDigits.fromRun("123")); assertEquals("1234", AccountDigits.fromRun("1234")); assertEquals("2345", AccountDigits.fromRun("12345")); assertEquals("9012", AccountDigits.fromRun("123456789012"))
        assertNull(AccountDigits.fromRun("12")); assertNull(AccountDigits.fromRun("")); assertNull(AccountDigits.fromRun("12a"))
    }

    @Test
    fun twoValuesAreOneAccountWhenEqualOrOneEndsTheOther() {
        assertTrue(AccountDigits.compatible("123", "123")); assertTrue(AccountDigits.compatible("4123", "123")); assertTrue(AccountDigits.compatible("123", "4123")); assertTrue(AccountDigits.compatible("4123", "4123"))
        assertFalse(AccountDigits.compatible("4123", "5123")); assertFalse(AccountDigits.compatible("123", "124")); assertFalse(AccountDigits.compatible("123", "1234"), "1234 does not end in 123")
        assertFalse(AccountDigits.compatible(null, "123")); assertFalse(AccountDigits.compatible("12", "123"))
        assertTrue(AccountDigits.same("9", "9"), "equal as stored, whatever it is"); assertFalse(AccountDigits.same(null, null))
    }

    @Test
    fun aStatementLineNamesAnAccountByAMaskOrAnAccountWordNeverByABareThreeDigitNumber() {
        assertTrue(AccountDigits.mentionedIn("neft to 1234 savings", "1234"), "four digits stand alone, as they always did")
        assertTrue(AccountDigits.mentionedIn("transfer to a/c xx123", "123")); assertTrue(AccountDigits.mentionedIn("transfer to a/c xx4123", "123")); assertTrue(AccountDigits.mentionedIn("sent to acct 123", "123"))
        assertFalse(AccountDigits.mentionedIn("pos 123 mumbai", "123"), "a bare three-digit number is as likely a branch or a reference")
        assertFalse(AccountDigits.mentionedIn("ref 11234 mumbai", "123"), "part of a longer number")
        assertFalse(AccountDigits.mentionedIn("a/c xx5555", "1234"))
    }

    // ---- which account ----

    @Test
    fun aFourDigitTextFindsTheAccountWithTheSameFourDigitsAsBefore() {
        val r = AccountMatch.resolve("4821", "HDFC Bank", listOf(acct("a", "HDFC Savings", "4821"), acct("b", "ICICI Savings", "123")), banks)
        assertEquals(Result.Found("a", Basis.DIGITS), r)
        assertEquals(Result.None, AccountMatch.resolve("9999", "HDFC Bank", listOf(acct("a", "HDFC Savings", "4821")), banks))
    }

    @Test
    fun aThreeDigitTextFindsTheAccountSavedWithThreeDigitsOrWithFourThatEndTheSame() {
        assertEquals(Result.Found("a", Basis.DIGITS), AccountMatch.resolve("123", "ICICI Bank", listOf(acct("a", "ICICI Savings", "123")), banks))
        assertEquals(Result.Found("a", Basis.ENDS_WITH), AccountMatch.resolve("123", "ICICI Bank", listOf(acct("a", "ICICI Savings", "4123")), banks), "saved with four, the text shows three")
        assertEquals(Result.Found("a", Basis.ENDS_WITH), AccountMatch.resolve("4123", "ICICI Bank", listOf(acct("a", "ICICI Savings", "123")), banks), "saved with three, the text shows four")
        assertEquals(Result.None, AccountMatch.resolve("123", "ICICI Bank", listOf(acct("a", "ICICI Savings", "1234")), banks), "1234 does not end in 123")
    }

    @Test
    fun theSameLastDigitsAtAnotherBankAreAnotherAccount() {
        val icici = acct("i", "ICICI Savings", "4123"); val hdfc = acct("h", "HDFC Salary", "5123")
        assertEquals(Result.Found("i", Basis.ENDS_WITH), AccountMatch.resolve("123", "ICICI Bank", listOf(icici, hdfc), banks), "the HDFC account ends the same and is not considered")
        assertEquals(Result.Found("h", Basis.ENDS_WITH), AccountMatch.resolve("123", "HDFC Bank", listOf(icici, hdfc), banks))
        assertEquals(Result.None, AccountMatch.resolve("123", "ICICI Bank", listOf(hdfc), banks), "digits alone never match across banks")
        assertEquals(Result.None, AccountMatch.resolve("4123", "ICICI Bank", listOf(acct("h", "HDFC Salary", "4123")), banks), "four equal digits at another bank are not this bank's account either")
        // a card of the bank's own name is that bank's
        assertEquals(Result.Found("c", Basis.DIGITS), AccountMatch.resolve("3025", "ICICI Bank", listOf(acct("c", "ICICI Amazon Pay card", "3025", AccountType.CREDIT_CARD)), banks))
    }

    @Test
    fun anAccountWhoseNameNamesNoBankIsAskedAboutOnThreeDigitsAndMatchedOnFourEqualDigitsAsBefore() {
        // three digits are weak evidence and the name does not say whose account it is: asked, never taken (the bank is known from the sender)
        assertEquals(Result.Several(listOf("s")), AccountMatch.resolve("123", "ICICI Bank", listOf(acct("s", "Salary", "4123")), banks), "ends the same, name says nothing")
        assertEquals(Result.Several(listOf("s")), AccountMatch.resolve("123", "ICICI Bank", listOf(acct("s", "Salary", "123")), banks), "three equal digits, name says nothing")
        assertEquals(Result.Several(listOf("s")), AccountMatch.resolve("4123", "ICICI Bank", listOf(acct("s", "Salary", "123")), banks), "the text has four, the account three")
        // what the person said settles it, now and for the next texts
        assertEquals(Result.Found("s", Basis.REMEMBERED), AccountMatch.resolve("123", "ICICI Bank", listOf(acct("s", "Salary", "4123")), banks, remembered = "s"))
        // an account whose name does say the bank is taken at once, and one of another bank is never offered
        assertEquals(Result.Found("i", Basis.ENDS_WITH), AccountMatch.resolve("123", "ICICI Bank", listOf(acct("s", "Salary", "9123"), acct("i", "ICICI Savings", "4123")).filter { it.id == "i" }, banks))
        assertEquals(Result.Several(listOf("s", "i")), AccountMatch.resolve("123", "ICICI Bank", listOf(acct("s", "Salary", "9123"), acct("i", "ICICI Savings", "4123")), banks), "both fit: asked, never guessed between")
        // four equal digits keep the rule they always had: they match on their own, whatever the name says
        assertEquals(Result.Found("s", Basis.DIGITS), AccountMatch.resolve("1234", "HDFC Bank", listOf(acct("s", "Salary", "1234")), banks))
        // a sender that is no bank (a payment app) has nothing to compare: nothing is ruled out, nothing needs asking
        assertEquals(Result.Found("s", Basis.DIGITS), AccountMatch.resolve("1234", "Google Pay", listOf(acct("s", "HDFC Salary", "1234")), banks), "a payment app is no bank: nothing is ruled out by its name")
        assertEquals(Result.Found("s", Basis.ENDS_WITH), AccountMatch.resolve("123", "Google Pay", listOf(acct("s", "Salary", "4123")), banks))
    }

    @Test
    fun severalAccountsThatFitAreNeverGuessedBetween() {
        val a = acct("a", "ICICI Savings", "4123"); val b = acct("b", "ICICI Joint", "7123")
        val r = AccountMatch.resolve("123", "ICICI Bank", listOf(a, b), banks)
        assertEquals(Result.Several(listOf("a", "b")), r)
        assertNull(found(r))
        // two accounts with the very same digits
        assertEquals(Result.Several(listOf("a", "c")), AccountMatch.resolve("123", "ICICI Bank", listOf(a.copy(digits = "123"), acct("c", "ICICI Home", "123")), banks))
        // "123" and "4123" both end in 123: both fit, so nothing is guessed (exactly equal digits only win when nothing else fits)
        assertEquals(Result.Several(listOf("a", "e")), AccountMatch.resolve("123", "ICICI Bank", listOf(a, acct("e", "ICICI Own", "123")), banks))
        assertEquals(Result.Found("e", Basis.DIGITS), AccountMatch.resolve("123", "ICICI Bank", listOf(a.copy(digits = "5124"), acct("e", "ICICI Own", "123")), banks))
    }

    @Test
    fun whatThePersonChoseSettlesDigitsTwoAccountsShareAndBeatsDigitsThatOnlyEndTheSame() {
        val a = acct("a", "ICICI Savings", "4123"); val b = acct("b", "ICICI Joint", "7123")
        assertEquals(Result.Found("b", Basis.REMEMBERED), AccountMatch.resolve("123", "ICICI Bank", listOf(a, b), banks, remembered = "b"))
        assertEquals(Result.Found("x", Basis.REMEMBERED), AccountMatch.resolve("123", "ICICI Bank", listOf(a), banks, remembered = "x"), "an earlier choice outranks digits that merely end the same")
        assertEquals(Result.Found("a", Basis.DIGITS), AccountMatch.resolve("4123", "ICICI Bank", listOf(a), banks, remembered = "x"), "the same digits are the strongest evidence, as they always were")
        assertEquals(Result.Found("m", Basis.REMEMBERED), AccountMatch.resolve(null, "ICICI Bank", emptyList(), banks, remembered = "m"), "a bank whose texts print no digits: what was chosen for the bank")
        assertEquals(Result.None, AccountMatch.resolve(null, "ICICI Bank", listOf(a), banks))
        assertEquals(Result.None, AccountMatch.resolve("12", "ICICI Bank", listOf(a), banks), "two digits are no digits")
    }

    @Test
    fun aCardIsPreferredForACardTextOnlyWhenExactlyOneCarriesTheDigits() {
        val bank = acct("b", "HDFC Savings", "4821"); val c1 = acct("c1", "HDFC Regalia", "4821", AccountType.CREDIT_CARD); val c2 = acct("c2", "HDFC Millennia", "4821", AccountType.CREDIT_CARD)
        assertEquals(Result.Found("c1", Basis.DIGITS), AccountMatch.resolve("4821", "HDFC Bank", listOf(bank, c1), banks, preferCard = true))
        assertEquals(Result.Several(listOf("b", "c1")), AccountMatch.resolve("4821", "HDFC Bank", listOf(bank, c1), banks, preferCard = false))
        assertEquals(Result.Several(listOf("c1", "c2")), AccountMatch.resolve("4821", "HDFC Bank", listOf(c1, c2), banks, preferCard = true))
    }

    @Test
    fun bankWordsComeFromTheBanksNamesAndOrdinaryWordsSayNothing() {
        assertEquals(true, banks.sameBank("ICICI Bank", "My ICICI account")); assertEquals(false, banks.sameBank("ICICI Bank", "HDFC Salary")); assertNull(banks.sameBank("ICICI Bank", "Salary account"))
        assertEquals(true, banks.sameBank("Kotak Mahindra Bank", "Kotak 811")); assertEquals(false, banks.sameBank("SBI", "Axis Bank joint")); assertNull(banks.sameBank("IDFC FIRST Bank", "First account"), "first is an ordinary word")
        assertNull(banks.sameBank("Google Pay", "HDFC Salary"), "a payment app: no bank to compare"); assertNull(banks.sameBank(null, "HDFC Salary"))
    }

    // ---- precision ----

    /** A tiny deterministic generator: the same numbers on every run and every platform. */
    private class Lcg(var s: Long) { fun next(n: Int): Int { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 33) % n).toInt() } }

    private val bankNames = listOf("ICICI", "HDFC", "SBI", "Axis", "Kotak")
    private val issuers = mapOf("ICICI" to "ICICI Bank", "HDFC" to "HDFC Bank", "SBI" to "SBI", "Axis" to "Axis Bank", "Kotak" to "Kotak Mahindra Bank")

    @Test
    fun threeAndFourDigitTextsLandOnTheRightAccountOrAskAndNeverOnAnotherBanksAccountInTwoThousandInventedLedgers() {
        val rnd = Lcg(20261102)
        var texts = 0; var right = 0; var asked = 0; var wrong = 0; var missed = 0; var otherBank = 0; var strangers = 0; var strangerFound = 0
        repeat(2000) {
            // each person has two to four accounts across the invented banks; the digits come from a small range so that clashes (the same last three digits at different banks, even at one bank) are common
            val count = 2 + rnd.next(3)
            val mine = (0 until count).map { i ->
                val bank = bankNames[rnd.next(bankNames.size)]
                val tail = (rnd.next(4) * 100 + rnd.next(3) * 10 + rnd.next(2)).toString().padStart(3, '0')
                Triple("id$i", bank, if (rnd.next(2) == 0) tail else (1 + rnd.next(2)).toString() + tail)
            }
            val cands = mine.map { (id, bank, d) -> acct(id, "$bank account ${id.last()}", d) }
            for ((id, bank, d) in mine) {
                // the bank prints the account's three digits (or its four when it has four and the bank prints four): its own account
                val printed = if (d.length == 4 && rnd.next(2) == 0) d.takeLast(3) else d
                val r = AccountMatch.resolve(printed, issuers.getValue(bank), cands, banks)
                texts++
                // the accounts of THIS bank that could be meant: the only ones a text of this bank may land on, so the right answer is known without running the matcher
                val fits = mine.filter { it.second == bank && AccountDigits.compatible(it.third, printed) }
                when (r) {
                    is Result.Found -> { if (mine.first { it.first == r.accountId }.second != bank) otherBank++; if (fits.size == 1 && r.accountId == id) right++ else wrong++ }
                    is Result.Several -> if (fits.size > 1 && r.accountIds.toSet() == fits.map { it.first }.toSet()) asked++ else wrong++
                    Result.None -> missed++
                }
            }
            // a text whose digits no account of its own bank carries: nothing, never an account and never a question
            for (bank in bankNames) {
                val own = mine.filter { it.second == bank }
                val stranger = (100 + rnd.next(800)).toString()
                if (own.none { AccountDigits.compatible(it.third, stranger) }) { strangers++; if (AccountMatch.resolve(stranger, issuers.getValue(bank), cands, banks) !is Result.None) strangerFound++ }
            }
        }
        println("ACCOUNT MATCH PRECISION: $texts texts about the person's own accounts in 2,000 invented ledgers (clashing digits on purpose): the right account $right, asked because several fit $asked, wrong account $wrong, an account of ANOTHER bank $otherBank, missed $missed; $strangers texts whose digits no account of their bank carries: matched or asked $strangerFound")
        assertEquals(0, wrong, "a text landed on the wrong account, or asked about the wrong ones")
        assertEquals(0, otherBank, "a text landed on another bank's account")
        assertEquals(0, missed, "an account that carries the digits was not found")
        assertEquals(0, strangerFound, "digits no account carries were matched to an account")
        assertTrue(right > 1000 && asked > 100, "the invented ledgers should hold both clear and clashing cases: right $right, asked $asked")
    }

    /** Both halves of an own-account transfer as TransferScan reads them: each text's own account by [AccountMatch] with its bank known, the other end's by digits alone, then [TransferPairs.pair]. */
    @Test
    fun ownAccountTransfersPairOnThreeDigitsOnlyWhenTheAccountsAreUnambiguousAndNeverOnTheWrongOnes() {
        val rnd = Lcg(77)
        var transfers = 0; var paired = 0; var leftToAsk = 0; var wrongPairs = 0; var unpairedThatShould = 0; var withClash = 0
        repeat(1500) { n ->
            val bBank = bankNames[1 + rnd.next(4)]
            val aDigits = (100 + rnd.next(900)).toString()                       // ICICI prints three digits
            val bDigits = (1000 + rnd.next(9000)).toString()                     // the other bank four
            val clash = rnd.next(3) == 0                                          // a third account, at ANOTHER bank, ends in the same three digits as the ICICI account
            val twin = rnd.next(5) == 0                                           // or a second ICICI account ends the same: the ICICI text then fits two accounts
            val cands = listOfNotNull(
                acct("a", "ICICI Savings", aDigits), acct("b", "$bBank Current", bDigits),
                if (clash) acct("c", "Salary at SBI", (1 + rnd.next(9)).toString() + aDigits) else null,
                if (twin) acct("t", "ICICI Joint", (1 + rnd.next(9)).toString() + aDigits) else null,
            )
            val amount = 100_00L * (1 + rnd.next(500)); val at = 1_000_000L * n
            fun own(issuer: String, digits: String): String? = (AccountMatch.resolve(digits, issuers.getValue(issuer), cands, banks) as? Result.Found)?.accountId
            fun end(digits: String): String? = (AccountMatch.resolve(digits, null, cands) as? Result.Found)?.accountId // the other end's bank is not said
            // ICICI "Acct XX<a> debited ... & Acct XX<b> credited" and the other bank's "A/c XX<b> credited": the account money left and the one it reached are named by digits
            val out = TransferSide("out$n", true, amount, at, own("ICICI", aDigits), end(bDigits), true)
            val into = TransferSide("in$n", false, amount, at + 40_000, own(bBank, bDigits), end(aDigits), true)
            transfers++
            if (clash && !twin) withClash++
            val pairs = TransferPairs.pair(listOf(out, into))
            if (pairs.isEmpty()) { if (twin) leftToAsk++ else unpairedThatShould++ }
            else for (p in pairs) { if (p.out.accountId == "a" && p.into.accountId == "b") paired++ else wrongPairs++ }
        }
        println("OWN-TRANSFER PAIRING: $transfers invented transfers ($withClash of them with the same last three digits at another bank): paired on the right two accounts $paired, left for the person because the ICICI account was ambiguous $leftToAsk, false or wrong pairs $wrongPairs, clear ones left unpaired $unpairedThatShould")
        assertEquals(0, wrongPairs, "a transfer was paired on the wrong accounts")
        assertEquals(0, unpairedThatShould, "a clear transfer was not paired")
        assertTrue(paired > 800 && leftToAsk > 100 && withClash > 100, "both cases should occur: paired $paired, left $leftToAsk, clashing $withClash")
    }
}
