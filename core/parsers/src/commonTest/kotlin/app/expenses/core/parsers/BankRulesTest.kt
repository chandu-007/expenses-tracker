package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The self-check of [BankRules]: the list is data, so a mistake in it (a repeated header, two banks claiming one email name, a header nobody has a sample for) must fail here
 * and not show up as a missed bank on someone's phone. Every sample is invented. **Adding a bank means adding its line to [BankRules.ALL] and its sample here.**
 */
class BankRulesTest {
    private val today = LocalDate(2026, 6, 1)
    private val debit = "Rs.450.00 debited from A/c XX1234 on 05-06-26 to VPA shop@upi. UPI Ref 628374659283."
    private val card = "Rs.1,250.00 spent on Card XX4321 at ACME STORES on 05-06-26."
    private val bill = "Your bill of Rs.706.82 is due on 30-06-2026. Total amount payable Rs.706.82."

    /** Header root to one invented text that bank sends. A bank, card or payment-app text must be read as a transaction; a utility's as a bill (or a transaction). */
    private val samples: Map<String, String> = mapOf(
        "HDFCBK" to "Txn Rs.20.00 On HDFC Bank Card 0862 At ACME STORES by UPI 628374659283", "HDFCBN" to "Txn Rs.20.00 On HDFC Bank Card 0862 At ACME STORES by UPI 628374659283",
        "ICICIB" to "ICICI Bank Acct XX123 debited for Rs 450.00 on 05-Jun-26; ACME STORES credited. UPI:628374659283.", "SBIINB" to "Dear UPI user A/C X1234 debited by 450.00 on date 05Jun26 trf to ACME Refno 628374659283",
        "SBIPSG" to "Dear Customer, A/C X1234 debited by 450.00 on date 05Jun26 trf to ACME Refno 628374659283", "SBICRD" to "Rs.1,250.00 spent on your SBI Card ending 4321 at ACME STORES on 05/06/26",
        "SBIUPI" to "Dear UPI user A/C X1234 debited by 450.00 on date 05Jun26 trf to ACME Refno 628374659283", "AXISBK" to "Spent Card no. XX4321 INR 1250.00 05-06-26 12:30:00 ACME STORES",
        "KOTAKB" to "Sent Rs.450.00 from Kotak Bank AC X1234 to shop@upi on 05-06-26. UPI Ref 628374659283.", "IDFCFB" to debit, "INDUSB" to "Your a/c XX1234 is debited by Rs. 450.00 on 05-06-26 for ACME",
        "YESBNK" to debit, "PNBSMS" to "A/c XX1234 debited INR 450.00 Dt 05-06-26 14:20 Bal INR 5,000.00", "BOBSMS" to "Rs.450.00 Dr. from A/C XXXXXX1234 and Cr. to shop@upi. Ref:628374659283",
        "BOIIND" to "A/c XX1234 debited Rs.450.00 on 05-06-26 UPI Ref 628374659283", "CANBNK" to "Your A/c XX1234 is debited for Rs.450.00 on 05-06-26 and credited to ACME",
        "UNIONB" to "A/c *1234 Debited for Rs:450.00 on 05-06-2026 by UPI Ref 628374659283", "IDBIBK" to debit, "FEDBNK" to debit, "RBLBNK" to card, "AUBANK" to debit, "BANDHN" to debit,
        "SCBANK" to card, "HSBCIN" to card, "AMEXIN" to "Alert: You've spent INR 1,250.00 on your AMEX card ** 41005 at ACME STORES on 5 June 2026 at 12:30 PM IST.",
        "CITIBK" to card, "INDBNK" to "Your a/c XX1234 is debited for Rs.450.00 on 05-06-26 and a/c credited. UPI Ref 628374659283",
        "GPAY" to "Paid Rs.450.00 to ACME STORES using UPI Ref 628374659283", "PHONEPE" to "Paid Rs.450.00 to ACME STORES. UPI Ref 628374659283", "PAYTM" to "Paid Rs.450.00 to ACME STORES via UPI. Ref 628374659283",
        "BHIM" to "Rs.450.00 debited from your account to shop@upi. UPI Ref 628374659283",
        "BESCOM" to "Your electricity bill of Rs.1,340.00 for consumer no 1234567890 is due on 30-06-2026. Total amount payable Rs.1,340.00.", "MSEDCL" to "Electricity bill generated for consumer 123456789012. Amount payable Rs.1,340.00, due date 30-06-2026.",
        "TATAPW" to "Your Tata Power bill of Rs.1,340.00 is due on 30-06-2026. Amount payable Rs.1,340.00.", "JIOPAY" to bill, "AIRTEL" to "Your Airtel postpaid bill of Rs.499.00 is due on 30-06-2026. Total amount payable Rs.499.00.",
        "LICIND" to "Your LIC premium of Rs.5,000.00 is due on 30-06-2026. Amount payable Rs.5,000.00.", "ONECRD" to card, "SLCEIT" to card, "JUPITR" to debit, "PAYTMB" to debit, "AIRBNK" to debit,
    )

    @Test
    fun everyRootIsWellFormedAndUniqueAndEveryRootHasASampleAndEverySampleHasARoot() {
        val roots = BankRules.ALL.map { it.root }
        assertEquals(roots.toSet().size, roots.size, "a header root is listed twice: ${roots.groupBy { it }.filter { it.value.size > 1 }.keys}")
        assertEquals(roots.toSet(), samples.keys, "every entry needs one sample, and a sample needs an entry")
        for (r in BankRules.ALL) {
            assertEquals(r.root, SenderRules.headerRoot("AX-${r.root}-S"), "${r.root} is not a header root the matcher accepts")
            assertEquals(r.root, r.root.uppercase(), "${r.root} must be upper case")
            assertTrue(r.issuer.isNotBlank() && r.issuer == r.issuer.trim(), "${r.root} has no clean issuer name")
        }
    }

    @Test
    fun noEmailPhraseIsClaimedByTwoBanksAndEveryAliasIsLowerCase() {
        val claims = BankRules.ALL.flatMap { r -> (if (r.emailNamed) listOf(r.issuer.lowercase()) else emptyList()) + r.emailAliases }
        val byPhrase = BankRules.ALL.flatMap { r -> ((if (r.emailNamed) listOf(r.issuer.lowercase()) else emptyList()) + r.emailAliases).map { it to r.issuer } }.distinct().groupBy({ it.first }, { it.second })
        assertEquals(emptyMap(), byPhrase.filterValues { it.size > 1 }, "two issuers claim one email phrase")
        assertEquals(claims.map { it.lowercase() }, claims, "email aliases are written in lower case")
        assertTrue(BankRules.ALL.flatMap { it.emailAliases }.none { a -> BankRules.ALL.any { it.issuer.lowercase() == a } }, "an alias repeats an issuer name")
        for (r in BankRules.ALL) for (alias in r.emailAliases) assertEquals(r.root, SenderRules.emailIssuer("$alias alerts"), "alias '$alias' stands for ${r.root}")
        for (r in BankRules.ALL.filter { it.emailNamed }.distinctBy { it.issuer }) assertEquals(BankRules.ALL.first { it.issuer == r.issuer }.root, SenderRules.emailIssuer(r.issuer), "an email from ${r.issuer}")
        for (r in BankRules.ALL.filter { !it.emailNamed }) assertEquals(null, SenderRules.emailIssuer(r.issuer), "${r.issuer} is not named in an email")
    }

    @Test
    fun theBuiltInViewHasOneIssuerPerRootAndTheOnlyHeaderWithTheCardLayoutAreHdfcs() {
        assertEquals(BankRules.ALL.size, SenderRules.BUILT_IN.size)
        for (r in BankRules.ALL) assertEquals(r.issuer, SenderRules.BUILT_IN[r.root])
        assertEquals(listOf("HDFCBK", "HDFCBN"), BankRules.ALL.filter { it.cardTxnLayout }.map { it.root })
        assertEquals(setOf("HDFC Bank"), BankRules.ALL.filter { it.cardTxnLayout }.map { it.issuer }.toSet())
    }

    @Test
    fun theClassifierReadsEveryEntrysSampleUnderItsOwnHeaderAsATransactionOrAnApprovedBill() {
        val bad = mutableListOf<String>()
        for (r in BankRules.ALL) {
            val sender = "VM-${r.root}-S"
            when (val o = CaptureClassifier.classify(sender, samples.getValue(r.root), emptySet(), today)) {
                is CaptureOutcome.Transaction -> if (o.issuer != r.issuer || o.headerRoot != r.root || r.kind == SenderKind.UTILITY) bad += "${r.root}: $o"
                is CaptureOutcome.Bill -> if (r.kind != SenderKind.UTILITY || o.issuer != r.issuer) bad += "${r.root}: $o"
                is CaptureOutcome.Ignored -> bad += "${r.root}: $o"
            }
        }
        assertEquals(emptyList(), bad, "samples the classifier does not read as its entry's issuer")
    }

    @Test
    fun aHeaderNotOnTheListIsStillIgnoredAndAPersonalNumberIsNeverRead() {
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, "NOBANK"), CaptureClassifier.classify("AX-NOBANK-S", debit, emptySet(), today))
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.PERSONAL_SENDER, null), CaptureClassifier.classify("+919876543210", debit, emptySet(), today))
    }
}
