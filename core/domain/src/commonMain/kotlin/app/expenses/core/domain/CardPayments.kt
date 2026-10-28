package app.expenses.core.domain

/** An account a card payment can move money to or from, as the import knows it: its id, its name, and the last four digits it carries (if the person entered them). */
data class CardRef(val id: String, val name: String, val last4: String? = null)

/**
 * What to do with the lines of a statement that look like paying a credit card's bill. [TRANSFER] records money moving between the person's own accounts (neither
 * spending nor income), [AS_IS] records the line the way the statement shows it (spending on a bank statement, income on a card statement), [SKIP] leaves it out.
 */
enum class CardPaymentChoice { TRANSFER, AS_IS, SKIP }

/**
 * Paying a credit card's bill is money moving from the bank to the card, not spending: the purchases were the spending. A bank statement shows it as a debit
 * ("CREDIT CARD PAYMENT", "BILLDESK*HDFC CREDIT CARD"), the card's own statement as a credit ("PAYMENT RECEIVED - THANK YOU"). Imported as they stand, the first is counted
 * as spending again and the second as income. This finds those lines by their wording, and the card they are for by the last four digits or the bank's name in
 * the line, so the preview can offer to record them as a transfer.
 *
 * The wording is an assumption from how Indian banks word these lines. A line that merely says "card payment" is not enough (a shop called "Card World", a coffee
 * chain's or a fuel card, "Debit card annual fee", a gift card): it has to say "credit card" or "CC" with a payment word, or name a card company (HDFC, SBI, Amex...)
 * with "card", or be CRED's. No real bank statement was available; the lines in the tests are invented.
 */
object CardPayments {
    /** Which side of the payment the statement being imported is: a bank (or wallet, cash, loan) account paying the bill, or the card receiving it. */
    enum class Side { BANK, CARD }

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Lower case, every run of anything but letters and digits as one space, with a space at both ends so a whole word is " word ". */
    private fun norm(text: String) = " " + text.lowercase().replace(NON_ALNUM, " ").trim() + " "

    private val PAYMENT_WORDS = setOf("payment", "payments", "pymt", "pmt", "bill", "bills", "autopay", "repayment", "dues", "due")
    private val FEE_WORDS = setOf("fee", "fees", "charge", "charges", "annual", "interest", "markup", "gst", "refund", "reversal", "waiver")
    private val GLUED_CC = setOf("ccpayment", "ccpymt", "ccbill", "ccautopay", "ccpmt")
    private val OTHER_CARDS = listOf(" debit card ", " gift card ", " prepaid card ", " forex card ", " travel card ", " metro card ", " smart card ", " fuel card ", " atm ", " pos ")
    private val PAYING_A_CARD = listOf(" payment to card ", " payment towards card ", " payment for card ", " pay card ", " paying card ")
    private val NOT_A_PAYMENT_TO_A_CARD = Regex("\\b(refund|reversal|reversed|cashback|cash back|waiver|chargeback|failed|dispute)\\b")

    /**
     * The banks and card companies whose credit cards people hold. A name here next to "card" (or a card payment word) is what makes a line "someone's credit card":
     * a line that merely says "card payment" or "card dues" is as likely a shop's own card (a coffee chain's, a fuel card, a canteen's) as a bank's.
     */
    private val ISSUERS = setOf(
        "hdfc", "icici", "sbi", "axis", "kotak", "amex", "citi", "citibank", "idfc", "indusind", "rbl", "pnb", "federal", "hsbc", "canara", "bandhan", "scapia", "onecard",
        "bobcard", "yes", "au", "bob", "standard",
    )
    /** Names that are common words or part of other names unless said with their second word. */
    private val NEEDS_BANK_WORD = mapOf("yes" to listOf(" yes bank "), "au" to listOf(" au bank ", " au small finance "), "bob" to listOf(" bob card ", " bank of baroda "), "standard" to listOf(" standard chartered "))
    private val BILLERS = setOf("billdesk", "bbps", "billpay", "autopay")
    /** Words that say the biller is being paid for something else than a card (an insurer or a loan of the same bank). */
    private val NOT_A_CARD_BILL = setOf("life", "insurance", "ins", "loan", "loans", "mutual", "fund", "amc", "securities", "home", "finance", "general", "ergo", "pension", "lombard", "health", "mf", "sip", "demat")

    private fun canonical(issuer: String) = when (issuer) { "citibank" -> "citi"; "bobcard" -> "bob"; else -> issuer }

    /** The card companies a line names, by one name each ("sbi" for "SBICARD", "amex" for "American Express"). */
    fun issuersIn(description: String): Set<String> = issuersOf(norm(description))

    private fun issuersOf(n: String): Set<String> {
        val found = LinkedHashSet<String>()
        for (t in n.trim().split(' ')) {
            val name = if (t in ISSUERS) t else if (t.length > 4 && t.endsWith("card") && t.removeSuffix("card") in ISSUERS) t.removeSuffix("card") else continue
            val needs = NEEDS_BANK_WORD[name]
            if (needs == null || needs.any { it in n } || t != name) found += canonical(name)
        }
        if (" american express " in n) found += "amex"
        return found
    }

    /** Aggregator apps that pay card bills for people (CRED): they say which card only when the line does. */
    private fun aggregatorIn(n: String) = n.trim().split(' ').any { it == "cred" || it == "credclub" }

    /** Whether the line is one of an aggregator's (CRED): a card payment whose card cannot be assumed. */
    fun isAggregator(description: String) = aggregatorIn(norm(description))

    private val GATE = listOf("card", "cc", "cred", "amex", "american express", "billdesk", "bbps", "billpay", "autopay")

    /** Whether the line of a **bank** statement (money out) reads as paying a credit card's bill. */
    fun looksLikePaymentToCard(description: String): Boolean {
        val n = norm(description)
        // every import asks this of every money-out line: most have none of these
        if (GATE.none { it in n }) return false
        if (NOT_A_PAYMENT_TO_A_CARD.containsMatchIn(n.trim())) return false
        val tokens = n.trim().split(' ')
        // "CC" is a word only on its own ("CC PAYMENT", "AUTOPAY CC"), never inside another ("CCAVENUE")
        if (tokens.any { it in GLUED_CC } || (" cc " in n && " cc avenue " !in n && tokens.any { it in PAYMENT_WORDS })) return true
        if (aggregatorIn(n)) return true
        if (FEE_WORDS.any { it in tokens } || OTHER_CARDS.any { it in n }) return false
        // "credit card" itself says it
        if (" credit card " in n || " creditcard " in n || " cr card " in n) return true
        if (PAYING_A_CARD.any { it in n }) return true
        val issuers = issuersOf(n)
        if (issuers.isEmpty()) return false
        // a bank's name with "card" ("ICICI CARD PAYMENT", "SBICARD", "UPI-AMAZON PAY ICICI CARD"); American Express's name alone is a card
        if (" card " in n || tokens.any { it.length > 4 && it.endsWith("card") && it.removeSuffix("card") in ISSUERS } || "amex" in issuers) return true
        // a bill-payment service with a card company's name, unless it is paying that company for something else
        return tokens.any { it in BILLERS } && tokens.none { it in NOT_A_CARD_BILL }
    }

    private val RECEIVED = Regex("\\bpayment\\b|\\bthank you\\b|\\bbbps\\b|\\bbppy\\b|\\bautopay\\b|\\bauto pay\\b")

    /** Whether the line of a **credit card** statement (money in) reads as the bill being paid ("PAYMENT RECEIVED", "THANK YOU"), not a refund or a cashback. */
    fun looksLikePaymentReceived(description: String): Boolean {
        val n = norm(description).trim()
        return RECEIVED.containsMatchIn(n) && !NOT_A_PAYMENT_TO_A_CARD.containsMatchIn(n)
    }

    private val LAST4_PATTERNS = listOf(
        Regex("(?<![a-z])[x*]{2,}[\\s-]*(\\d{4})(?!\\d)"),
        Regex("(?<![a-z0-9])x(\\d{4})(?!\\d)"),
        Regex("\\b(?:ending|ends)\\s*(?:in|with)?\\s*[:\\-]?\\s*(\\d{4})(?!\\d)"),
        Regex("\\bcard\\s*(?:no\\.?|number|num|#)\\s*[:\\-]?\\s*(?:[x*]+[\\s-]*)?(\\d{4})(?!\\d)"),
    )

    /** The last four digits a line names ("XX1234", "card ending 1234", "4111XXXXXXXX1234"), or null. */
    fun last4(description: String): String? {
        val t = description.lowercase()
        return LAST4_PATTERNS.firstNotNullOfOrNull { it.find(t)?.groupValues?.get(1) }
    }

    /** Words that name no bank: they are in every card payment line and in most account names. */
    private val NOT_A_NAME = setOf(
        "credit", "card", "cards", "bank", "payment", "payments", "pymt", "pmt", "bill", "bills", "autopay", "auto", "pay", "billdesk", "bbps", "billpay", "neft",
        "imps", "upi", "rtgs", "inft", "ach", "nach", "ecs", "visa", "master", "mastercard", "rupay", "cred", "club", "credclub", "platinum", "classic", "premium", "signature",
        "titanium", "rewards", "cashback", "limited", "ltd", "india", "the", "for", "from", "towards", "ref", "txn", "www", "com", "net", "online", "banking",
        "thank", "you", "received", "dues", "due", "repayment", "settlement", "mobile", "app", "my", "new", "old", "account", "main", "personal", "joint",
    )

    /** The words of [text] that could name a bank or a card, with its card companies under one name each ("SBICARD" is "sbi", "American Express" is "amex"). */
    private fun names(text: String): Set<String> {
        val n = norm(text)
        return n.trim().split(' ').filter { w -> w.length >= 3 && w.all { it in 'a'..'z' } && w !in NOT_A_NAME }.toSet() + issuersOf(n)
    }

    /**
     * The one of [accounts] this line is for, or null when none or several could be. The last four digits decide when the line has them and an account carries
     * them; otherwise a name in the line (a bank's, "HDFC", "ICICI") that only one account's name also has.
     */
    fun accountFor(description: String, accounts: List<CardRef>): String? {
        val digits = last4(description)
        if (digits != null) {
            val hit = accounts.filter { it.last4 == digits }
            if (hit.size == 1) return hit.single().id
            if (hit.size > 1) return null
        }
        val words = names(description)
        if (words.isEmpty()) return null
        return accounts.filter { (digits == null || it.last4 == null) && names(it.name).any { w -> w in words } }.singleOrNull()?.id
    }

    /**
     * The lines of a statement that look like card payments: their positions in the list of transactions, the account each one is for where that could be told
     * ([identified]), and the ones that name no card, digits or bank at all and are not an aggregator's ([generic]: "CREDIT CARD PAYMENT"; they can be taken for the
     * one card there is, where a line that names other digits or another bank, or is CRED's, cannot).
     */
    data class Found(val side: Side, val indexes: List<Int>, val identified: Map<Int, String>, val generic: Set<Int> = emptySet())

    /**
     * The card payments among [txns]. On a bank statement ([side] BANK) they are the money-out lines and [others] are the person's credit cards; on a card
     * statement they are the money-in lines and [others] are the accounts the money can have come from. Null when there are none.
     */
    fun find(txns: List<StatementTxn>, side: Side, others: List<CardRef>): Found? {
        val indexes = txns.indices.filter { i ->
            val t = txns[i]
            t.counterAccount == null && when (side) {
                Side.BANK -> t.flow == Flow.SPENDING && looksLikePaymentToCard(t.description)
                Side.CARD -> t.flow == Flow.INCOME && looksLikePaymentReceived(t.description)
            }
        }
        if (indexes.isEmpty()) return null
        val identified = indexes.mapNotNull { i -> accountFor(txns[i].description, others)?.let { i to it } }.toMap()
        val generic = indexes.filter { i -> i !in identified && last4(txns[i].description) == null && issuersIn(txns[i].description).isEmpty() && !isAggregator(txns[i].description) }.toSet()
        return Found(side, indexes, identified, generic)
    }

    /**
     * What the person has chosen: [choice] (null while they have not), and for [CardPaymentChoice.TRANSFER] the [account] every line goes to or comes from
     * (null: each line's own account, where the line named one).
     */
    data class Pick(val choice: CardPaymentChoice?, val account: String? = null)

    /**
     * The choice made for the person until they make their own. A bank statement: a transfer when every line can be tied to a card, or the person has one card
     * and no line says anything against it (a line that names other digits or another bank, or CRED's, is not taken for it); as spending when the person has no
     * card account to move it to; no choice yet otherwise, since guessing the card would put the payment on the wrong one. A card statement: skipped, as a
     * card's text statement does, since the bank statement shows the same payment as money out.
     */
    fun defaultPick(found: Found, others: List<CardRef>): Pick = when {
        found.side == Side.CARD -> Pick(CardPaymentChoice.SKIP)
        others.isEmpty() -> Pick(CardPaymentChoice.AS_IS)
        found.indexes.all { it in found.identified } -> Pick(CardPaymentChoice.TRANSFER)
        others.size == 1 && found.indexes.all { it in found.identified || it in found.generic } -> Pick(CardPaymentChoice.TRANSFER)
        else -> Pick(null)
    }

    /** The account line [index] moves money to (bank statement) or from (card statement) under [pick]: the one chosen for all, else the line's own, else the only one there is. */
    fun target(found: Found, index: Int, pick: Pick, others: List<CardRef>): String? =
        pick.account ?: found.identified[index] ?: others.singleOrNull()?.id

    /** [txns] ready for the importer. [skipped] lines are left out; [transfers] are tied to their account. */
    data class Applied(val txns: List<StatementTxn>, val skipped: Int, val transfers: Int)

    /** The transactions to import under [pick], or null while it cannot be applied: nothing chosen, or a transfer with a line that has no account to go to. */
    fun apply(txns: List<StatementTxn>, found: Found?, pick: Pick, others: List<CardRef>): Applied? {
        if (found == null) return Applied(txns, 0, 0)
        val choice = pick.choice ?: return null
        val inGroup = found.indexes.toSet()
        val out = ArrayList<StatementTxn>(txns.size)
        var skipped = 0; var transfers = 0
        for ((i, t) in txns.withIndex()) {
            if (i !in inGroup) { out += t; continue }
            when (choice) {
                CardPaymentChoice.SKIP -> skipped++
                CardPaymentChoice.AS_IS -> out += t
                CardPaymentChoice.TRANSFER -> { out += t.copy(counterAccount = target(found, i, pick, others) ?: return null); transfers++ }
            }
        }
        return Applied(out, skipped, transfers)
    }
}
