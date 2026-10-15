package app.expenses.core.storage

/** Where the account a person picked for a bank's card or account is remembered, so a later text for the same issuer and last four digits starts with it. */
object CaptureAccounts {
    fun key(issuer: String, last4: String?) = "capture_acct:$issuer:${last4.orEmpty()}"
}
