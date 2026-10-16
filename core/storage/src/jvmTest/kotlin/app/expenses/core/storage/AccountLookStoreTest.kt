package app.expenses.core.storage

import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.CardNetwork
import app.expenses.core.domain.CardTier
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountLookStoreTest {
    private val d = Device("a")
    private val looks = AccountLookStore(d.db, d.clock) { 9L }

    private fun account(id: String, type: AccountType, archived: Boolean = false) = d.ledger.saveAccount(Account(AccountId(id), id, type, Currency.INR, 0, archived))
    private fun ops(id: String) = d.db.schemaQueries.opsOfRow("account_look", id).executeAsList().map { it.field_ to it.value_ }

    @Test
    fun aLookIsSavedAndReadBackAndAnEmptyOneRemovesTheRow() {
        account("card", AccountType.CREDIT_CARD)
        looks.save(AccountLook("card", CardNetwork.RUPAY, CardTier.PLATINUM, "4821"))
        assertEquals(AccountLook("card", CardNetwork.RUPAY, CardTier.PLATINUM, "4821"), looks.get("card"))
        looks.save(AccountLook("card", CardNetwork.RUPAY, null, null)); assertEquals(AccountLook("card", CardNetwork.RUPAY), looks.get("card"))
        looks.save(AccountLook("card")); assertNull(looks.get("card"), "nothing set: no row")
    }

    @Test
    fun digitsThatAreNotFourAndAnAccountThatIsGoneAreRefusedAndNothingIsSaved() {
        account("bank", AccountType.BANK)
        assertFailsWith<IllegalArgumentException> { looks.save(AccountLook("bank", last4 = "123")) }
        assertFailsWith<IllegalArgumentException> { looks.save(AccountLook("bank", last4 = "12a4")) }
        assertFailsWith<IllegalArgumentException> { looks.save(AccountLook("missing", last4 = "1234")) }
        assertNull(looks.get("bank"))
    }

    @Test
    fun onlyWhatChangedIsLoggedAndRemovingItIsLogged() {
        account("card", AccountType.CREDIT_CARD)
        looks.save(AccountLook("card", CardNetwork.VISA, null, "1111"))
        val first = ops("card").size
        looks.save(AccountLook("card", CardNetwork.VISA, null, "1111")); assertEquals(first, ops("card").size, "saving the same again logs nothing")
        looks.save(AccountLook("card", CardNetwork.VISA, null, "2222")); assertEquals(first + 1, ops("card").size); assertEquals("last4" to "2222", ops("card").last())
        looks.save(AccountLook("card")); assertEquals("deleted" to "1", ops("card").last())
    }

    @Test
    fun digitsFindTheAccountButNotAnArchivedOneAndSharedDigitsNeedACardToBePreferred() {
        account("bank", AccountType.BANK); account("card", AccountType.CREDIT_CARD); account("old", AccountType.BANK)
        looks.save(AccountLook("bank", last4 = "4821")); looks.save(AccountLook("old", last4 = "9999"))
        assertEquals("bank", looks.accountFor("4821", preferCard = false))
        account("old", AccountType.BANK, archived = true); assertNull(looks.accountFor("9999", preferCard = false), "an archived account is not offered")
        looks.save(AccountLook("card", CardNetwork.MASTERCARD, last4 = "4821"))
        assertNull(looks.accountFor("4821", preferCard = false), "a bank account and a card share the digits: ask")
        assertEquals("card", looks.accountFor("4821", preferCard = true), "a card statement is for the card")
        assertNull(looks.accountFor(null, preferCard = true)); assertNull(looks.accountFor("0000", preferCard = true))
    }

    @Test
    fun looksSurviveABackupOnlyForAccountsThatExistAndUnknownNamesAndBadDigitsAreDropped() {
        d.populate(); account("card", AccountType.CREDIT_CARD); looks.save(AccountLook("card", CardNetwork.AMEX, CardTier.PREMIUM, "0007"))
        val (_, data) = BackupFile.decode(BackupFile.encode(d.backup.export(), 1L))
        val other = Device("b"); val restored = AccountLookStore(other.db, other.clock) { 9L }
        other.backup.restore(data); other.backup.restore(data)
        assertEquals(AccountLook("card", CardNetwork.AMEX, CardTier.PREMIUM, "0007"), restored.get("card"))
        // a file from a newer version: a network this one does not know and digits that are not four
        val odd = data.copy(accountLooks = listOf(BackupAccountLook("card2", "DINERS", "GOLD9", "12")))
        val third = Device("c"); third.ledger.saveAccount(Account(AccountId("card2"), "c2", AccountType.CREDIT_CARD, Currency.INR))
        third.backup.restore(odd); assertNull(AccountLookStore(third.db, third.clock) { 9L }.get("card2"), "nothing usable in it: no row")
    }
}
