# LedgerLens transaction automation coverage

## Three different proofs

| Area | Full-export dry run | Merged Android code | GitHub Pages verifier |
|---|---|---|---|
| Original 456 transaction rows | Evaluated directly | Not committed or bundled | Loaded locally by the user |
| 495 parser diagnostics | Evaluated directly | Not bundled | Loaded locally by the user |
| Review reduction 38 to 18 | Demonstrated | Requires the matching learned rules to exist and be reapplied | Verifies a candidate export against the result |
| Uncategorized spending 80 to 54 | Demonstrated | Depends on matching merchant/category rules | Verifies the candidate result |
| Alert-threshold records | Three detected and suppressed | Filter implemented before persistence | Confirms three ignored/removed records |
| Refund/reimbursement offsets | Demonstrated | Implemented in `TransactionTreatments` | Compares spending impact fields |
| Treatment-derived exclusion | Demonstrated | Implemented for user edits and alias rules | Compares treatment and exclusion fields |
| Authorized-user/card label | One bad merchant detected | Invalid merchant is cleared and routed to review | Confirms the merchant repair |
| Separate authorized-user database field | Modeled in sandbox | Not implemented | Display/verification only |
| Complete decision rules | Six safe rules inferred from historical edits | Generic complete-rule application implemented | Verifies their output |
| Automatic extraction of those six rules from old edit history | Performed by the sandbox analysis | Not implemented as a migration/backfill job | Not performed by the verifier |
| Grouped 13-decision review UI | Simulated | Not implemented as a dedicated Android screen | Not persisted; comparison only |
| Duplicate candidates | Fifteen flagged | Detection/storage/UI not implemented | Verifies flags in the dry-run candidate |
| Conflicting Capital One history | Automatic rule blocked | Generic rule conflict must still be prevented by the caller/workflow | Shown as unresolved in dry-run output |
| Android unit tests | Not applicable | 90/90 passed | Reports the test result |
| Live SMS capture, permissions, Room migration | Not tested | Existing app paths, not end-to-end validated by this change | Not tested |

## How to confirm the original dry run

1. Open the private verifier at `/LedgerLens/verify.html`.
2. Load the original transaction CSV as **Baseline**.
3. Load the redacted parser diagnostics JSONL as **Diagnostics**.
4. Load `dry_run_transactions.csv` as **Candidate**.
5. Confirm the original-file fingerprints, 456 joined transactions, and the target metrics.
6. Inspect and export the row-level differences.

## How to confirm the Android implementation

1. Use a copy of the same LedgerLens database or the same captured SMS set.
2. Run the updated build from `feat/improvements`.
3. Ensure the required merchant/treatment rules exist, then reapply rules or reparse the approved alerts.
4. Export transactions from the app.
5. Load that export as **Candidate** in the verifier.
6. Treat changes to amount, date, currency, transaction identity, or protected user-edited fields as blockers.
7. Test notification capture, runtime permissions, database migration, and export separately on an Android device.

The verifier processes selected files in browser memory. It does not upload or persist them.