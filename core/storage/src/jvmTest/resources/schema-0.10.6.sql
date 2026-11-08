-- The CREATE statements of the 0.10.6 database (commit e7dea55, core/storage/.../Schema.sq), nothing else: queries are not part of a database. Used by SchemaFrom0106Test. Statements are separated by marker lines.

-- @@
CREATE TABLE account (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    currency TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    -- What the account held before its first entry here (negative: owed on a card or loan).
    opening_minor INTEGER NOT NULL DEFAULT 0,
    -- Hidden from pickers. Its history stays and still counts.
    archived INTEGER NOT NULL DEFAULT 0
);
-- @@
CREATE TABLE currency (
    code TEXT NOT NULL PRIMARY KEY,
    digits INTEGER NOT NULL
);
-- @@
CREATE TABLE category (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    parent_id TEXT,
    color INTEGER,
    position INTEGER NOT NULL,
    hidden INTEGER NOT NULL DEFAULT 0,
    -- EXPENSE, INCOME or BOTH: which kind of entry the category is offered for. BOTH is only for categories made before kinds existed that income already used.
    kind TEXT NOT NULL DEFAULT 'EXPENSE',
    deleted INTEGER NOT NULL DEFAULT 0,
    -- Set when this category was merged into another. The row stays as a tombstone so an edit that arrives later and still
    -- uses this id (for example from a device that had not yet seen the merge) is redirected to the target.
    merged_into TEXT
);
-- @@
CREATE TABLE template (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    amount_minor INTEGER,
    currency TEXT NOT NULL,
    category_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    merchant TEXT,
    uses INTEGER NOT NULL DEFAULT 0,
    deleted INTEGER NOT NULL DEFAULT 0
);
-- @@
CREATE TABLE budget (
    scope TEXT NOT NULL PRIMARY KEY,
    limit_minor INTEGER NOT NULL CHECK (limit_minor > 0),
    currency TEXT NOT NULL
);
-- @@
CREATE TABLE budget_period (
    id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
    kind TEXT NOT NULL,
    start_value INTEGER NOT NULL
);
-- @@
CREATE TABLE recurring (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    amount_minor INTEGER,
    currency TEXT NOT NULL,
    category_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    merchant TEXT,
    unit TEXT NOT NULL,
    every INTEGER NOT NULL,
    start_day INTEGER NOT NULL,
    end_kind TEXT NOT NULL,
    end_value INTEGER,
    auto INTEGER NOT NULL DEFAULT 0,
    deleted INTEGER NOT NULL DEFAULT 0,
    kind TEXT NOT NULL DEFAULT 'EXPENSE',
    to_account_id TEXT
);
-- @@
CREATE TABLE recurring_done (
    recurring_id TEXT NOT NULL,
    day INTEGER NOT NULL,
    outcome TEXT NOT NULL,
    entry_id TEXT,
    PRIMARY KEY (recurring_id, day)
);
-- @@
CREATE TABLE setting (
    key TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);
-- @@
CREATE TABLE entry (
    seq INTEGER PRIMARY KEY AUTOINCREMENT,
    id TEXT NOT NULL UNIQUE,
    kind TEXT NOT NULL,
    occurred_at INTEGER NOT NULL,
    account_id TEXT,
    counter_account_id TEXT,
    amount_minor INTEGER NOT NULL,
    currency TEXT NOT NULL,
    category_id TEXT,
    -- Columns below are used only by some kinds; see EntryMapping.kt for which.
    person_id TEXT,
    to_amount_minor INTEGER,
    to_currency TEXT,
    direction TEXT,
    refunds_entry TEXT,
    original_amount_minor INTEGER,
    original_currency TEXT,
    merchant TEXT,
    note TEXT,
    source TEXT NOT NULL DEFAULT 'manual',
    source_ref TEXT,
    deleted INTEGER NOT NULL DEFAULT 0
);
-- @@
CREATE INDEX entry_time ON entry(occurred_at DESC, seq DESC) WHERE deleted = 0;
-- @@
CREATE INDEX entry_account_time ON entry(account_id, occurred_at DESC) WHERE deleted = 0;
-- @@
CREATE INDEX entry_account_time_seq ON entry(account_id, occurred_at DESC, seq DESC) WHERE deleted = 0;
-- @@
CREATE INDEX entry_counter_time ON entry(counter_account_id, occurred_at DESC, seq DESC) WHERE counter_account_id IS NOT NULL AND deleted = 0;
-- @@
CREATE INDEX entry_account_sums ON entry(account_id, kind, direction, amount_minor) WHERE deleted = 0;
-- @@
CREATE INDEX entry_transfer_in ON entry(counter_account_id, to_amount_minor) WHERE kind = 'TRANSFER' AND deleted = 0;
-- @@
CREATE INDEX entry_refunds ON entry(refunds_entry) WHERE refunds_entry IS NOT NULL;
-- @@
CREATE INDEX entry_loans ON entry(person_id, currency, direction, amount_minor, occurred_at) WHERE kind = 'SETTLEMENT' AND deleted = 0;
-- @@
CREATE INDEX entry_deleted_ids ON entry(id) WHERE deleted = 1;
-- @@
CREATE UNIQUE INDEX entry_source_ref ON entry(source, source_ref) WHERE source_ref IS NOT NULL;
-- @@
CREATE TABLE entry_split (
    entry_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    category_id TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    PRIMARY KEY (entry_id, position)
);
-- @@
CREATE INDEX entry_split_category ON entry_split(category_id);
-- @@
CREATE INDEX entry_income_category ON entry(category_id) WHERE category_id IS NOT NULL;
-- @@
CREATE TABLE entry_share (
    entry_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    person_id TEXT, -- null is the user
    amount_minor INTEGER NOT NULL,
    PRIMARY KEY (entry_id, position)
);
-- @@
CREATE TABLE entry_origin (
    entry_id TEXT NOT NULL PRIMARY KEY,
    kind TEXT NOT NULL,
    sender TEXT,
    text TEXT NOT NULL,
    received_at INTEGER
);
-- @@
CREATE TABLE entry_expect (
    entry_id TEXT NOT NULL PRIMARY KEY,
    from_text TEXT,
    person_id TEXT,
    expected_minor INTEGER NOT NULL,
    closed INTEGER NOT NULL DEFAULT 0
);
-- @@
CREATE TABLE person (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0
);
-- @@
CREATE TABLE attachment (
    id TEXT NOT NULL PRIMARY KEY,
    entry_id TEXT NOT NULL,
    name TEXT NOT NULL,
    mime TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    sha256 TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
-- @@
CREATE INDEX attachment_entry ON attachment(entry_id);
-- @@
CREATE TABLE inbox (
    id TEXT NOT NULL PRIMARY KEY,
    source TEXT NOT NULL,
    sender TEXT NOT NULL,
    issuer TEXT NOT NULL,
    received_at INTEGER NOT NULL,
    direction TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    currency TEXT NOT NULL,
    txn_date TEXT,
    reference TEXT,
    account_last4 TEXT,
    via_upi INTEGER NOT NULL DEFAULT 0,
    confidence TEXT NOT NULL,
    body TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    entry_id TEXT,
    fingerprint TEXT NOT NULL UNIQUE
);
-- @@
CREATE INDEX inbox_status ON inbox(status, received_at DESC);
-- @@
CREATE TABLE bill (
    id TEXT NOT NULL PRIMARY KEY,
    source TEXT NOT NULL,
    sender TEXT NOT NULL,
    issuer TEXT NOT NULL,
    kind TEXT NOT NULL,
    title TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    min_due_minor INTEGER,
    currency TEXT NOT NULL,
    due_date TEXT,
    account_last4 TEXT,
    reference TEXT,
    auto_debit INTEGER NOT NULL DEFAULT 0,
    received_at INTEGER NOT NULL,
    body TEXT,
    status TEXT NOT NULL DEFAULT 'FOUND',
    entry_id TEXT,
    paid_at INTEGER,
    dedupe_key TEXT NOT NULL UNIQUE
);
-- @@
CREATE INDEX bill_status ON bill(status, due_date);
-- @@
CREATE TABLE capture_rule (
    id TEXT NOT NULL PRIMARY KEY,
    position INTEGER NOT NULL,
    pattern TEXT NOT NULL,
    category_id TEXT NOT NULL
);
-- @@
CREATE TABLE merchant_map (
    key TEXT NOT NULL PRIMARY KEY,
    category_id TEXT NOT NULL,
    uses INTEGER NOT NULL DEFAULT 1
);
-- @@
CREATE TABLE capture_event (
    at INTEGER NOT NULL,
    sender_root TEXT NOT NULL,
    outcome TEXT NOT NULL
);
-- @@
CREATE INDEX capture_event_at ON capture_event(at);
-- @@
CREATE TABLE card_terms (
    account_id TEXT NOT NULL PRIMARY KEY,
    statement_day INTEGER NOT NULL,
    due_day INTEGER NOT NULL,
    min_due_percent INTEGER NOT NULL DEFAULT 5,
    remind_days INTEGER NOT NULL DEFAULT 3
);
-- @@
CREATE TABLE account_look (
    account_id TEXT NOT NULL PRIMARY KEY,
    network TEXT,
    tier TEXT,
    last4 TEXT
);
-- @@
CREATE INDEX account_look_last4 ON account_look(last4) WHERE last4 IS NOT NULL;
-- @@
CREATE TABLE saved_search (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    text TEXT NOT NULL DEFAULT '',
    min_rupees TEXT NOT NULL DEFAULT '',
    max_rupees TEXT NOT NULL DEFAULT '',
    category_id TEXT,
    kind TEXT NOT NULL DEFAULT 'ANY',
    range_kind TEXT NOT NULL DEFAULT 'ANY',
    tag TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    -- chosen dates (days since 1970-01-01, both ends inclusive, either may be open) and one account; added after the table first shipped (see SchemaUpgrade)
    from_day INTEGER,
    to_day INTEGER,
    account_id TEXT
);
-- @@
CREATE TABLE entry_tag (
    entry_id TEXT NOT NULL,
    tag TEXT NOT NULL COLLATE NOCASE,
    PRIMARY KEY (entry_id, tag)
) WITHOUT ROWID;
-- @@
CREATE INDEX entry_tag_tag ON entry_tag(tag, entry_id);
-- @@
CREATE TABLE spend_line (
    entry_id TEXT NOT NULL,
    line INTEGER NOT NULL,
    occurred_at INTEGER NOT NULL,
    currency TEXT NOT NULL,
    category_id TEXT,
    mine_minor INTEGER NOT NULL,
    cash_out_minor INTEGER NOT NULL,
    PRIMARY KEY (entry_id, line)
);
-- @@
CREATE INDEX spend_line_report ON spend_line(currency, occurred_at, category_id, mine_minor, cash_out_minor);
-- @@
CREATE INDEX spend_line_uncategorised ON spend_line(currency, occurred_at DESC) WHERE category_id IS NULL AND mine_minor > 0;
-- @@
CREATE VIRTUAL TABLE entry_fts USING fts5(merchant, note, content='entry', content_rowid='seq', tokenize='trigram');
-- @@
CREATE TABLE fts_suspended (id INTEGER NOT NULL PRIMARY KEY);
-- @@
CREATE TRIGGER entry_fts_insert AFTER INSERT ON entry
WHEN NOT EXISTS (SELECT 1 FROM fts_suspended)
BEGIN
    INSERT INTO entry_fts(rowid, merchant, note) VALUES (new.seq, new.merchant, new.note);
END;
-- @@
CREATE TRIGGER entry_fts_delete AFTER DELETE ON entry
WHEN NOT EXISTS (SELECT 1 FROM fts_suspended)
BEGIN
    INSERT INTO entry_fts(entry_fts, rowid, merchant, note) VALUES ('delete', old.seq, old.merchant, old.note);
END;
-- @@
CREATE TRIGGER entry_fts_update AFTER UPDATE OF merchant, note ON entry
WHEN NOT EXISTS (SELECT 1 FROM fts_suspended)
BEGIN
    INSERT INTO entry_fts(entry_fts, rowid, merchant, note) VALUES ('delete', old.seq, old.merchant, old.note);
    INSERT INTO entry_fts(rowid, merchant, note) VALUES (new.seq, new.merchant, new.note);
END;
-- @@
CREATE TABLE op_log (
    hlc TEXT NOT NULL PRIMARY KEY,
    entity TEXT NOT NULL,
    row_id TEXT NOT NULL,
    field TEXT NOT NULL,
    value TEXT
);
-- @@
CREATE INDEX op_log_row ON op_log(entity, row_id, field, hlc DESC);
