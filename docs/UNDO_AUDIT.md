# Undo audit

Every action that removes or rewrites what the person entered, what has a way back, and what does not. Read from the code (the view model and the screens)
and, for the rows marked "test", exercised by a device or host test. "Undo" is a bar with an Undo button (Home's list or the screen's own bar); "confirm"
is a dialog asked before the action; "reversible" is a button on the item itself that reverses it.

| Action | Where | Way back | Test |
|---|---|---|---|
| Delete an entry (swipe) | Home list | Undo bar (soft delete, `EntryActions.restore`) | EditDeleteTest |
| Bulk delete / category / account / tag add / tag remove | Home and Search selection | Undo bar on Home, Undo button in Search's notice | BulkEditTest, SearchBulk* |
| Several entries added from a bill's items | Add form | Undo bar takes them all back | BillEntriesSaveTest |
| "Same as..." move of similar entries | Similar dialog | Undo bar | |
| Edit an entry | Editor | History: "Undo this edit" (a new revision, nothing lost) | HistoryUiTest |
| Change an entry's type (expense, income, transfer), one or several | Editor chips, Search/Home selection "Make transfer" | Undo bar ("Changed to a transfer"): the new entry is deleted and the original restored exactly (same id, fields, reference, text, attachments) | KindChangesTest, ChangeTypeUiTest |
| Make transfers from Transfer candidates (cash withdrawals, moves between own accounts, transfers to yourself) | More, Transfer candidates | Undo bar on that screen ("Made N transfers"): every replaced entry is restored exactly and every transfer removed; for a move between two accounts the income that was deleted as the other half is brought back. The bar lives in memory like every other screen Undo bar; "Not a transfer" changes no entry and is undone with "Show the N you marked" | KindChangesTest, TransferCandidatesUiTest |
| Skip a due repeating item | Home, Bills | Undo bar (`ok.recurring.undo`) | RecurringUiTest |
| Delete a repeating item | Repeating screen | Confirm ("It stops repeating. Expenses already posted stay.") | RecurringUiTest |
| Remove a quick entry (template) | Home | Confirm | |
| Remove a goal | Goals | Confirm | GoalsUiTest |
| Remove a saved search | Search | Confirm | SavedSearchUiTest |
| Remove a file attached to an entry | Editor | Confirm (the file is deleted from the phone) | AttachmentsTest |
| Mark a bill paid | Bills | Reversible: "Not paid after all" on the card (the entry recorded stays; deleting it is the person's call) | BillsUiTest |
| **Skip an item in To review** (swipe left, Skip, Same one: skip) | To review | **Undo bar (new)**: the item returns with its text; a skip forgets the text in the database, the screen keeps it in memory only while the bar is shown | SkipUndoUiTest, InboxStoreTest |
| **Skip a found bill / remove a tracked bill** | Bills | **Undo bar (new)**: back to found or tracked with its message | SkipUndoUiTest, BillStoreTest |
| Restore from a backup | Your data | Confirm ("Restore this backup?"); a restore only merges what the backup holds, it deletes nothing | DataTest |
| Start fresh after the key is lost | Key-lost screen | Two-step confirm; the data was unreadable already | |
| Statement import (CSV, Excel, PDF, photo) | Import | Not undoable (see below). The preview with the counts is shown first, and the import adds entries only | |
| Merge a category into another | Categories | Confirm-style choice: the dialog says "This can't be undone" and nothing happens until a target is tapped | |
| Delete a category | Categories | Only an unused one can be deleted (the store refuses otherwise): nothing is lost but its name and colour | |
| Delete an account | Accounts | Only one with no entries and no saved items can be deleted: nothing is lost but its name and settings | |
| Remove a person | Lent and borrowed | Only one with no entries | |
| Remove a capture rule | Category rules | None. Left: a rule is a phrase and a category, two taps to make again | |
| Clear a budget limit | Budget | None. Left: one number, shown in the dialog that clears it | |
| Remove billing dates from a card | Accounts | None. Left: three numbers, the dialog that removes them shows them | |
| Forget one / all recent searches | Search | None. Left: they are only a shortcut list | |

## Why a statement import has no Undo

An import adds entries with ids made from a fingerprint of account, day, direction, amount and description, and "an id that already exists, whether live or
deleted, is skipped" (`StatementImporter`): that is what makes importing the same file twice harmless. An Undo of an import would have to delete the entries it
added, and the person would then find that importing the file again, with a corrected column choice, silently skips every row ("already in the app from an
earlier import"). A wrong import is better handled today by what the person already has: the preview before importing (counts, totals, the first rows, the
columns chosen), Search's selection with Delete and its Undo for the entries of the import, and the entries carrying their statement line as origin.
