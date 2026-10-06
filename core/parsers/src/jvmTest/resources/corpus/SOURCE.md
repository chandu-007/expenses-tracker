# SMS sample corpus

Public sample messages used to measure the generic alert parser. **They are sanitised samples written for another
project's tests, not live bank traffic, and they may not match what banks send today.** Any coverage figure derived from
them is "synthetic, unvalidated" until it is checked against real messages from consenting testers.

- Source: https://github.com/akhilnarang/bank-sms-parser, directory `tests/fixtures/sms/`
- Pinned commit: `add4c0278fc219f60390204b02abf20f5c5a2a00` (committed 2026-09-30)
- Licence: MIT, Copyright (c) 2026 Akhil Narang. See `LICENSE-upstream`.
- `expected.tsv` is **an answer key produced by that project's own parser**, not ground truth. Columns: file,
  is-transaction, direction, amount in paise, date, reference, last 4 of account or card, channel.
  Rebuild with `tools/sms-corpus/build_answer_key.py` (needs Python 3.14).
- Files under a `negative/` directory are messages that are not transactions.
