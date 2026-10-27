# Backup file format (versions 1 and 2)

The backup is the complete, lossless export of everything in the app, including the photos and files attached to entries (receipts, bills) unless the person switched that off. (The CSV export is for reading in a spreadsheet and leaves things out; it cannot be imported back.) Anyone may read or write this format; there is no secret in it. The file is plain unless the user chose a passphrase (see Encryption).

**Which version is written.** A backup *without* photos and files is written exactly as version 1 always was, so every release of the app can read it. A backup that carries photos and files is **version 2**: the same first line and payload, with the files' bytes after the payload (see Files). Version 2 is read by this release and newer ones. An older release that is given a version 2 file says "This backup was made by a newer version of the app. Update the app to restore it." and changes nothing (a sealed one, see Encryption, says "This is not a backup file from this app", because that release does not know the sealed layout); it does not restore the entries without the files.

## File layout

Two parts: one line of JSON describing the file, a newline (`\n`), then the payload (JSON, UTF-8).

```
{"format":"expenses-backup","version":1,"createdAt":1760000000000,"payloadBytes":12345,"sha256":"<hex>"}
{"currencies":[...],"accounts":[...],"categories":[...],"entries":[...], ...}
```

| Field | Meaning |
|---|---|
| `format` | Always `expenses-backup`. Anything else is refused. |
| `version` | `1` for a file without photos and files, `2` for one with them. A file with a higher version is refused with "update the app". |
| `createdAt` | When the backup was made, epoch milliseconds (UTC). |
| `payloadBytes` | Exact byte length of the payload; a cut-off file is detected by it. In version 1 nothing but blank space may follow the payload; in version 2 the files follow it. |
| `sha256` | SHA-256 of the payload bytes exactly as stored; a damaged file is detected before anything is read. |

Unknown fields are ignored on read, so a later version can add fields without breaking older readers of the same major version.

## Units

All amounts are integers in **minor units** (paise, cents) with the currency code beside them. Entry times are epoch milliseconds (UTC). Repeating-expense days are days since 1970-01-01. Identifiers are opaque strings.

## Payload

`currencies`, `accounts`, `categories`, `entries`, `templates`, `budgets`, `budgetPeriod`, `recurring`, `recurringDone`, `savedSearches`, `cardTerms`, `captureRules`, `merchantMap`, `people`, `bills`, `origins`, `accountLooks`, `attachments`, `settings`. Deleted entries are not included; a deleted category that was merged into another is (`mergedInto`), so a late edit still finds its target.

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
### `recurring`: `id`, `name`, `amountMinor`, `currency`, `categoryId`, `accountId`, `merchant`, `unit`, `every`, `startDay`, `endKind`, `endValue`, `auto`, `kind` (`EXPENSE`, `INCOME`, `TRANSFER`), `toAccountId` (the account a repeating transfer goes to)
### `recurringDone`: `recurringId`, `day`, `outcome`, `entryId`
### `cardTerms`: `accountId`, `statementDay` (1 to 28), `dueDay` (1 to 28), `minDuePercent` (0 to 100, an estimate of the minimum due), `remindDaysBefore` (0 to 10). Billing dates of a credit card. Restored only for a credit card that exists here and has none yet.

### `accountLooks`: `accountId`, `network` (`VISA`, `MASTERCARD`, `RUPAY`, `AMEX` or `OTHER`), `tier` (`CLASSIC`, `PLATINUM` or `PREMIUM`), `last4` (exactly four digits). How an account's tile looks and the digits texts use to find it. Any field may be absent. Restored only for an account that exists here and has none yet; an unknown name or digits that are not four are dropped. Absent in a backup made before they existed.

### `captureRules`: `id`, `position` (the order they are tried in), `pattern` (words that must all appear in the payee or text), `categoryId`. Restored only where this phone has no rule with the same id and the category exists here.

### `merchantMap`: `key` (the payee reduced to lower-case letters and digits), `categoryId`, `uses`. What the person chose for each payee. Restored only where this phone has no entry for the key.

### `people`: `id`, `name`, `deleted`. The people lent to and borrowed from. A lent-or-borrowed entry (`SETTLEMENT`) refers to one by `id` in its `personId`; the name lives only here. Absent in a backup made before they existed. Restored only where this phone has no person with the same id.

### `bills`: `id`, `issuer`, `kind` (`CREDIT_CARD`, `ELECTRICITY`, `WATER`, `GAS`, `MOBILE`, `BROADBAND`, `DTH`, `INSURANCE`, `LOAN_EMI`, `OTHER`), `title`, `amountMinor`, `minDueMinor`, `currency`, `dueDate` (`yyyy-MM-dd`), `accountLast4`, `reference` (a consumer or customer number), `autoDebit`, `receivedAt`, `status` (`TRACKED`, `PAID`, `DISMISSED`; a bill still `FOUND` is not written), `paidAt`, `dedupeKey` (what makes the same bill reminded again one bill). The text of the message and the entry a payment made are not in the file. Absent in a backup made before bills existed. Restored only where this phone has no bill with the same id or `dedupeKey`.

### `origins`: `entryId`, `kind` (`SMS`, `NOTIFICATION`, `EMAIL` or `STATEMENT`), `sender` (the name the text or notification came from; absent for a statement line), `text` (the original words the entry was read from), `receivedAt` (epoch milliseconds, when it arrived; absent when not known). Written only for entries in the file. Absent in a backup made before they existed. Restored only for an entry that exists here and has no origin yet.

### `attachments`: `id`, `entryId`, `name`, `mime`, `sizeBytes`, `sha256`, `createdAt`
The photos and files attached to the entries in the file, only in a version 2 file (the key is absent otherwise). `id` is the attachment's identifier (a UUID; the app stores the file under it. A restore takes only ids of letters, digits, `-` and `_`, at most 64, and **lower-cases** them, so the record and the file name agree on file systems that do not tell upper from lower case), `entryId` the entry it belongs to, `name` the file name shown (no folders), `mime` its type (a restore keeps it only if it is `type/subtype` of plain characters, else `application/octet-stream`), `sizeBytes` its exact length (1 to 15,728,640), `sha256` the SHA-256 of its bytes as lower-case hex, `createdAt` when it was attached (epoch milliseconds, may be 0). **The bytes are not in the JSON**: they follow the payload (see Files). Attachments of deleted entries are not included. A file that is missing on the phone when the backup is made, or whose size no longer matches its record, or that cannot be opened and read, is left out of the list, not written empty and never failing the backup: the entries are always backed up. (A file that fails in the middle of being copied, after the list was written, keeps its place as zeros, so the files after it stay where the list says; a restore finds it damaged and skips it.) A file whose bytes on the phone no longer match its own `sha256` is written as it is and skipped on restore.

### `savedSearches`: `id`, `name`, `text`, `minRupees`, `maxRupees`, `categoryId`, `kind` (`ANY`, `EXPENSE`, `INCOME`), `range` (`ANY`, `LAST_30_DAYS`, `THIS_PERIOD`, `THIS_YEAR`), `tag`, `fromDay`, `toDay` (chosen dates as days since 1970-01-01, both included, either may be absent), `accountId` (limits the search to one account). The last three are optional: a backup made before they existed restores as a search without them.
### `settings`: a map of text to text (reminder preferences and similar).

## Files (version 2)

After the payload, the bytes of each file in `attachments` follow one after the other, in the order of that list, exactly `sizeBytes` each, with no separator and no padding:

```
<first line>\n<payload: payloadBytes bytes><file 1: sizeBytes><file 2: sizeBytes>...
```

The payload is read into memory, so a header whose `payloadBytes` is over the limit is refused, and so is a backup that would need more. The limit is 128 MB (about 548 bytes per entry, so about 245,000 entries) and a phone with little memory lowers it (the Android app uses an eighth of its heap, between 16 and 128 MB). Memory arithmetic: opening needs about 6 times the payload in heap, writing about 8 times. A phone that runs out of memory anyway says "too large to open on this phone" (or "not enough memory to make a backup") and changes nothing; the photos and files are never held, they are copied in pieces.

Because the list sits in the payload (covered by the payload `sha256`), the sizes can be trusted and a reader never has to guess where a file ends. A reader checks each file against its own `sha256` as it reads it. A file that does not match is **skipped and counted, and the restore carries on** with the next one: damage to one photo never costs the entries or the other photos. If the file ends early (a cut-off download), the files that did arrive whole are kept and the rest are counted as missing. A file can be written and read in pieces, so a backup of hundreds of megabytes is never held in memory.

Hand-made files: a reader ignores a version 1 file's `attachments` (there are no bytes), and takes none of an entry's files beyond 10, none larger than 15 MB, none with an unsafe `id` or a `sha256` that is not 64 lower-case hex characters, none already attached (same id, compared lower-cased), none for an entry that is deleted on the phone, and, when an id is listed twice, only the first valid record (the file and the record that is written for it are always the same one, and a later record with that id can never remove or replace what the first stored). The restore message says how many were left alone and why. A restore that is cancelled or killed after storing files but before recording them leaves files with no record; the app removes such files once, at every start (`sweep` over the recorded ids), so nothing needs to be done by hand.

## Restoring

A restore merges by id: anything already on the phone is kept as it is (including edits and deletions made since the backup), what is missing is added, and restoring the same file twice adds nothing. A restore that would break a ledger rule changes nothing.

For files, the same rule: an attachment is put back under its own `id` only if the phone has no attachment with that `id`, its entry is on the phone (or in the same file) and not deleted, the entry has fewer than 10 attachments, and the person did not remove that attachment on this phone since. A file already here is never overwritten. The bytes are stored first (each checked against `sha256`); the attachment records are then written in the same transaction as the entries; if that transaction fails the stored files are removed, so the records and the files never disagree. An old backup (version 1, or version 2 with files switched off) restores as before, its entries without files; restoring a later backup that has the files adds them to those entries.

## Encryption

With a passphrase the whole file (first line, payload and files) is sealed with AES-256-GCM; the key is derived from the passphrase with PBKDF2-HMAC-SHA256 (600,000 rounds, a random 16-byte salt). A wrong passphrase or a damaged file is refused. Without the passphrase the data cannot be recovered; there is no reset. There are two layouts, and a reader tells them apart by the first 8 bytes:

**`EXPENC1` (a backup without files; written exactly as before).** `EXPENC1\n`, rounds (4 bytes, big-endian), salt (16), nonce (12), then the AES-GCM ciphertext of the whole plain file followed by its 16-byte tag. Everything before the nonce is authenticated data. The plain file inside is a version 1 file.

**`EXPENC2` (a backup with files).** Written and read in chunks so nothing large is held in memory:

```
"EXPENC2\n"  rounds (4 bytes, big-endian)  salt (16)  chunkBytes (4 bytes, big-endian)     the 32-byte header
chunk 0, chunk 1, ... each one AES-256-GCM output: ciphertext followed by its 16-byte tag
```

The plain file inside (a version 2 file) is cut into pieces of exactly `chunkBytes` (the app writes 65,536; a reader accepts 4,096 to 1,048,576); the last piece holds 1 to `chunkBytes` bytes (0 only if the plain file is empty). Chunk number *i* (counting from 0) is sealed with the nonce of four zero bytes followed by *i* as 8 bytes big-endian, and with authenticated data equal to the 32 header bytes followed by one byte: `01` for the last chunk, `00` for every other. A fresh random salt makes the key different for every file, so the counter nonces never repeat under one key. Because of the chunk number and the last-chunk byte, chunks cannot be reordered, taken from another file, or cut off at the end without a tag failing, and a changed round count, salt or chunk size fails at once. Rounds below 100,000 or above 5,000,000 are refused outright.

A chunk that fails its tag, after the first one, damages only the plain bytes it holds: a reader that is restoring files treats those bytes as unreadable, so the files that lie across them fail their own `sha256` and are skipped and counted like any damaged file, and the rest are restored. A chunk that fails in the part that holds the first line and payload (or the first chunk of any file) refuses the whole backup: "The passphrase is wrong, or the file is damaged."

## Checking a file without this app

`tools/backup_interop/Interop.java` is an independent reader written against this document with only the JDK: `java tools/backup_interop/Interop.java verify2 <passphrase or -> <file>` opens a version 1 or 2 file (plain or `EXPENC1` / `EXPENC2`), checks the payload length and `sha256`, and checks the `sha256` of every file; the iPhone's tests use it to verify the files the iPhone writes.
