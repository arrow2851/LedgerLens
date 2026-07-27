# Transaction automation lab

This sandbox validates how LedgerLens can reduce repeated correction work without guessing through conflicting financial intent.

## Privacy boundary

The complete exported transaction CSV and parser-diagnostics JSONL were evaluated locally. They are not committed because this repository is public. The repository contains only:

- Aggregate before/after measurements.
- Synthetic focused fixtures.
- Android unit tests.
- The implementation under evaluation.

## User-work contract

The user should provide the first decision when personal context is required, such as assigning a new merchant category or distinguishing income, reimbursement, a card payment, and an internal transfer.

After that decision, LedgerLens should:

1. Save category, treatment, spending effect, and resolved review state as one complete decision.
2. Apply the decision to matching history and future transactions.
3. Derive inclusion or exclusion from the treatment unless the user uses an advanced override.
4. Normalize conservative merchant variants and payment-processor prefixes.
5. Reject a cardholder or card-product label as a merchant.
6. Ignore alert-setting threshold notifications before creating a transaction.
7. Block automatic learning when prior user decisions conflict.
8. Flag possible duplicates instead of deleting them automatically.

## Full-export dry-run result

| Metric | Before | After |
|---|---:|---:|
| Transactions needing review | 38 | 18 |
| Uncategorized spending rows | 80 | 54 |
| Uncategorized spending merchant groups | 54 | 46 |
| Non-transaction alerts stored as transactions | 3 | 0 |

The dry run removed 20 repeated review items and filled 26 category rows automatically. One conflicting decision group was deliberately blocked from auto-learning.

## Test commands

```bash
./gradlew testDebugUnitTest
```

Synthetic cases are stored in `app/src/test/resources/parser/focused_fixtures.synthetic.json`. Aggregate measurements are stored in `docs/testing/transaction-automation-dry-run.json`.
