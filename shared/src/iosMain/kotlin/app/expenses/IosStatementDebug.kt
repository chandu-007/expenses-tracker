package app.expenses

import app.expenses.core.domain.StatementTextParser
import platform.Foundation.NSData

/**
 * Test entry (the Swift unit tests call it, as the notification tests call `debugSeedAndCheck`): reads a statement PDF or picture the way Import does and
 * answers with what was understood, as one line per transaction ("12/09/2026 | SWIGGY BANGALORE | 450.50 | Dr") after a first line that says how the file
 * was read. It never touches the ledger.
 */
object IosStatementDebug {
    /** "PASSWORD_NEEDED", "WRONG_PASSWORD", "UNREADABLE: reason", or "OK recognised=<bool> payments=<n>" followed by the transaction lines. */
    fun pdf(data: NSData, password: String?): String = when (val r = IosPdf.read(data.toByteArray(), password)) {
        PdfText.PasswordNeeded -> "PASSWORD_NEEDED"
        PdfText.WrongPassword -> "WRONG_PASSWORD"
        is PdfText.Unreadable -> "UNREADABLE: ${r.reason}"
        is PdfText.Text -> summary(r.text, r.recognised)
    }

    fun picture(data: NSData): String = try {
        summary(IosOcr.recognize(data.toByteArray()), recognised = true)
    } catch (e: IllegalArgumentException) {
        "UNREADABLE: ${e.message}"
    }

    private fun summary(text: String, recognised: Boolean): String {
        val p = StatementTextParser.parse(text)
        return "OK recognised=$recognised payments=${p.cardPayments}\n" + p.table.rows.drop(1).joinToString("\n") { it.joinToString(" | ") }
    }
}
