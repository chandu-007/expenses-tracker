# Backup file format (version 1)

The backup is the complete, lossless export of everything in the app. (The CSV export is for reading in a spreadsheet and leaves things out; it cannot be imported back.) Anyone may read or write this format; there is no secret in it. The file is plain unless the user chose a passphrase (see Encryption).

## File layout

Two parts: one line of JSON describing the file, a newline (`\n`), then the payload (JSON, UTF-8).

```
{"format":"expenses-backup","version":1,"createdAt":1760000000000,"payloadBytes":12345,"sha256":"<hex>"}
{"currencies":[...],"accounts":[...],"categories":[...],"entries":[...], ...}
```

| Field | Meaning |
|---|---|
| `format` | Always `expenses-backup`. Anything else is refused. |
| `version` | `1`. A file with a higher version is refused with "update the app". |
| `createdAt` | When the backup was made, epoch milliseconds (UTC). |
| `payloadBytes` | Exact byte length of the payload; a cut-off file is detected by it. |
| `sha256` | SHA-256 of the payload bytes exactly as stored; a damaged file is detected before anything is read. |

Unknown fields are ignored on read, so a later version can add fields without breaking older readers of the same major version.

## Units

All amounts are integers in **minor units** (paise, cents) with the currency code beside them. Entry times are epoch milliseconds (UTC). Repeating-expense days are days since 1970-01-01. Identifiers are opaque strings.

## Payload

`currencies`, `accounts`, `categories`, `entries`, `templates`, `budgets`, `budgetPeriod`, `recurring`, `recurringDone`, `settings`. Deleted entries are not included; a deleted category that was merged into another is (`mergedInto`), so a late edit still finds its target.

### `currencies`: `code`, `digits`

### `accounts`
`id`, `name`, `type` (`BANK`, `CASH`, `WALLET`, `CREDIT_CARD`, `LOAN`), `currency`, `openingMinor` (balance before the first entry; negative means owed on a card or loan), `archived` (hidden from pickers; history stays).

### `categories`
`id`, `name`, `parentId`, `color`, `position`, `hidden`, `deleted`, `mergedInto`.

### `entries`
`id`, `kind` (`EXPENSE`, `INCOME`, `TRANSFER`, `REFUND`, `FRIEND_PAID`, `SETTLEMENT`), `occurredAt`, `accountId`, `counterAccountId` (the account a transfer goes to), `amountMinor`, `currency`, `categoryId` (income), `personId`, `toAmountMinor` and `toCurrency` (a transfer between currencies), `direction` (settlements), `refundsEntry` (the expense a refund is for), `originalAmountMinor` and `originalCurrency` (a foreign-currency amount kept for display), `merchant`, `note`, `source` (`manual`, `sms`, `csv`, ...), `sourceRef` (where it came from, used to avoid duplicates), `splits`, `shares`, `tags`.

- `splits`: `categoryId`, `amountMinor`. They add up to the expense amount.
- `shares`: `personId` (null is you), `amountMinor`.
- `tags`: text labels, many per entry, compared ignoring case (a restore keeps the first spelling it meets). No commas, at most 30 characters, at most 10 per entry.

### `templates`: `id`, `name`, `amountMinor`, `currency`, `categoryId`, `accountId`, `merchant`
### `budgets`: `scope`, `limitMinor`, `currency`
### `budgetPeriod`: `kind`, `startValue`
### `recurring`: `id`, `name`, `amountMinor`, `currency`, `categoryId`, `accountId`, `merchant`, `unit`, `every`, `startDay`, `endKind`, `endValue`, `auto`
### `recurringDone`: `recurringId`, `day`, `outcome`, `entryId`
### `settings`: a map of text to text (reminder preferences and similar).

## Restoring

A restore merges by id: anything already on the phone is kept as it is (including edits and deletions made since the backup), what is missing is added, and restoring the same file twice adds nothing. A restore that would break a ledger rule changes nothing.

## Encryption

With a passphrase the whole file is sealed with AES-GCM; the key is derived from the passphrase. A wrong passphrase or a damaged file is refused. Without the passphrase the data cannot be recovered; there is no reset.
