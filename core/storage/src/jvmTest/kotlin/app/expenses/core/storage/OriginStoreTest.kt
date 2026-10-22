package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Flow
import app.expenses.core.domain.Money
import app.expenses.core.domain.StatementTxn
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OriginStoreTest {
    private val text = "Rs.1,250.00 debited from A/c XX1234 to VPA shop@okhdfc (UPI Ref 628374659283)."

    @Test
    fun anOriginRoundTripsAndABlankOneIsNotKept() {
        val d = Device("a"); d.populate(); val store = OriginStore(d.db)
        store.put("coffee", OriginKind.SMS, "AX-HDFCBK-S", text, 1_234L)
        assertEquals(Origin("coffee", OriginKind.SMS, "AX-HDFCBK-S", text, 1_234L), store.get("coffee"))
        store.put("coffee", OriginKind.EMAIL, null, "second", null)
        assertEquals(Origin("coffee", OriginKind.EMAIL, null, "second", null), store.get("coffee"), "put replaces")
        store.put("tea", OriginKind.SMS, "X", "  ", 1L)
        assertNull(store.get("tea"), "blank words are no origin")
        store.remove("coffee"); assertNull(store.get("coffee"))
    }

    @Test
    fun theKindComesFromTheSourceAndTheSender() {
        assertEquals(OriginKind.SMS, OriginStore.kindOf("SMS", "AX-HDFCBK-S"))
        assertEquals(OriginKind.NOTIFICATION, OriginStore.kindOf("NOTIFICATION", "PHONEPE"))
        for (s in listOf("GMAIL", "OUTLOOK", "YAHOO", "gmail")) assertEquals(OriginKind.EMAIL, OriginStore.kindOf("NOTIFICATION", s), s)
    }

    @Test
    fun originsSurviveABackupAndAnOldBackupStillRestores() {
        val d = Device("a"); d.populate(); val store = OriginStore(d.db)
        store.put("coffee", OriginKind.NOTIFICATION, "PHONEPE", text, 99L)
        val (_, data) = BackupFile.decode(BackupFile.encode(d.backup.export(), 1L))
        assertEquals(listOf(BackupOrigin("coffee", "NOTIFICATION", "PHONEPE", text, 99L)), data.origins)

        val other = Device("b"); other.backup.restore(data); other.backup.restore(data)
        assertEquals(Origin("coffee", OriginKind.NOTIFICATION, "PHONEPE", text, 99L), OriginStore(other.db).get("coffee"))

        // a backup made before origins existed has no such field at all: build one by taking the field out of a current file and sealing it again
        val current = BackupFile.encode(d.backup.export().copy(origins = emptyList()), 1L).decodeToString()
        val payload = current.substringAfter('\n')
        assertTrue("\"origins\":[]," in payload)
        val oldPayload = payload.replace("\"origins\":[],", "").encodeToByteArray()
        val header = """{"format":"expenses-backup","version":1,"createdAt":1,"payloadBytes":${oldPayload.size},"sha256":"${Sha256.hex(oldPayload)}"}"""
        val (_, oldData) = BackupFile.decode(header.encodeToByteArray() + byteArrayOf('\n'.code.toByte()) + oldPayload)
        assertTrue(oldData.origins.isEmpty())
        val third = Device("c"); third.backup.restore(oldData); assertNull(OriginStore(third.db).get("coffee"))
    }

    @Test
    fun aDeletedEntryIsNotInABackupWithItsOrigin() {
        val d = Device("a"); d.populate(); val store = OriginStore(d.db)
        store.put("atm", OriginKind.SMS, "X", text, 1L)
        d.ledger.delete(EntryId("atm"))
        assertTrue(d.backup.export().origins.none { it.entryId == "atm" })
        assertNotNull(store.get("atm"), "kept, so an undo brings the words back with the entry")
    }

    @Test
    fun aStatementLineIsKeptAsTheOriginOfItsEntryAndARowWithoutOneHasNone() {
        val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
        val clock = app.expenses.core.domain.sync.HlcClock("n")
        val ledger = LedgerStore(db, clock) { 1L }
        ledger.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("bank"), "Bank", app.expenses.core.domain.ledger.AccountType.BANK, Currency.INR))
        val line = "05/10/2026 UPI-SWIGGY-123 250.50 Dr"
        StatementImporter(db, ledger).import(
            listOf(StatementTxn(1, LocalDate(2026, 10, 5), Flow.SPENDING, 25_050, "UPI-SWIGGY-123", line), StatementTxn(2, LocalDate(2026, 10, 6), Flow.SPENDING, 1_000, "NOTE")),
            AccountId("bank"), Currency.INR, TimeZone.UTC,
        )
        val rows = db.schemaQueries.liveEntriesInInsertionOrder().executeAsList()
        val withLine = rows.first { it.merchant == "UPI-SWIGGY-123" }; val without = rows.first { it.merchant == "NOTE" }
        val store = OriginStore(db)
        assertEquals(Origin(withLine.id, OriginKind.STATEMENT, null, line, null), store.get(withLine.id))
        assertNull(store.get(without.id))
    }

    @Test
    fun anAcceptedInboxItemKeepsItsMessageAfterTheInboxForgetsIt() {
        val d = Device("a"); d.populate(); val inbox = InboxStore(d.db); val origins = OriginStore(d.db)
        val item = InboxItem("i1", "NOTIFICATION", "GMAIL", "HDFC Bank", 7_000L, "DEBIT", 10_000, "INR", "2026-10-05", "R1", "1234", false, "GENERIC", text, InboxStatus.PENDING, null)
        assertTrue(inbox.add(item, "fp"))
        // what the app does on accept: the same transaction saves the entry, keeps the words and confirms the item
        d.ledger.atomically {
            d.ledger.save(Expense(EntryId("e1"), 1_000, AccountId("cash"), Money(10_000, Currency.INR)), EntryDetails(merchant = "HDFC", source = "sms", sourceRef = "R1"))
            val i = inbox.get("i1")!!
            origins.put("e1", OriginStore.kindOf(i.source, i.sender), i.sender, i.body!!, i.receivedAt)
            inbox.confirm("i1", "e1")
        }
        assertNull(inbox.get("i1")!!.body, "the inbox forgot the words")
        assertEquals(Origin("e1", OriginKind.EMAIL, "GMAIL", text, 7_000L), origins.get("e1"))
    }
}
