#!/usr/bin/env python3
"""Rebuilds core/parsers/src/jvmTest/resources/corpus/expected.tsv from the pinned upstream fixtures.

Needs Python 3.14 (the upstream library uses deferred annotations):
    python3.14 -m venv /tmp/venv
    /tmp/venv/bin/pip install "git+https://github.com/akhilnarang/bank-sms-parser@add4c0278fc219f60390204b02abf20f5c5a2a00"
    /tmp/venv/bin/python tools/sms-corpus/build_answer_key.py [output-path]

With no argument it rewrites the committed expected.tsv. To check the committed file, write elsewhere and diff:
    /tmp/venv/bin/python tools/sms-corpus/build_answer_key.py /tmp/check.tsv
    diff /tmp/check.tsv core/parsers/src/jvmTest/resources/corpus/expected.tsv

The key is that library's own output for each fixture, not ground truth.
"""
import glob
import os
import sys

from bank_sms_parser import parse_sms

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "core", "parsers", "src", "jvmTest", "resources", "corpus")
ROOT = os.path.normpath(ROOT)

rows = []
for path in sorted(glob.glob(os.path.join(ROOT, "**", "*.txt"), recursive=True)):
    rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
    bank = rel.split("/")[0]
    with open(path) as f:
        body = f.read()
    try:
        tx = parse_sms(bank=bank, body=body).model_dump().get("transaction")
    except Exception:  # the library rejects non-transaction messages
        tx = None
    if not tx:
        rows.append([rel, "0", "", "", "", "", "", ""])
        continue
    mask = tx.get("account_mask") or tx.get("card_mask") or ""
    last4 = "".join(c for c in mask if c.isdigit())[-4:]
    minor = str(int(round(float(tx["amount"]["amount"]) * 100)))
    date = str(tx["transaction_date"]) if tx.get("transaction_date") else ""
    rows.append([rel, "1", tx["direction"], minor, date, tx.get("reference_number") or "", last4, tx.get("channel") or ""])

out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "expected.tsv")
with open(out, "w") as f:
    for r in rows:
        f.write("\t".join(r) + "\n")
print(f"wrote {len(rows)} rows to {out}")
