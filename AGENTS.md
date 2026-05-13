# LedgerLens Agent Notes

## Read First

- Read `CODEX_HANDOFF.md` before making product or architecture changes.
- Keep this file short and durable. Put evolving product detail in `CODEX_HANDOFF.md`.
- Current Android package/namespace is `com.example.ledgerlens`; do not rename it unless explicitly asked.
- LedgerLens is a personal/local Android app, not currently optimized for Play Store release.

## Project Shape

- Android app module: `app`
- Main package: `app/src/main/java/com/example/ledgerlens`
- Current stack: Kotlin, Jetpack Compose, Room, KSP
- Current UI is mostly in `MainActivity.kt`; data lives under `data`, parser/source logic under `domain`.

## Build And Test

- Sync/build from Android Studio, or run:
  - `./gradlew :app:assembleDebug`
  - On Windows PowerShell: `.\gradlew.bat :app:assembleDebug`
- Run unit tests:
  - `./gradlew :app:testDebugUnitTest`
  - On Windows PowerShell: `.\gradlew.bat :app:testDebugUnitTest`
- If Room schema changes, verify migrations and run a debug build.

## Product Constraints

- SMS is the primary ingestion path.
- SMS imports must use stable keys: `notificationKey = sms:<sms_id>`.
- Sender/shortcode/phone number is the top-level financial source identity.
- Source keys should stay sender-level: `sender:<sms_sender>`.
- Do not make institution, account hint, or card hint the top-level source.
- New detected sources should start as uncategorized possible sources.
- Only parse transactions from identified sources.
- Do not parse uncategorized possible sources or non-sources.

## Spending Rules

- Spending Summary should include actual spending only:
  - `transactionType == EXPENSE`
  - `excludedFromSpending == false`
- Credit card payments should default to transfer/payment treatment and be excluded from spending totals.
- Transfers, balance alerts, payment confirmations, income, refunds, and manually excluded transactions should not inflate spending totals.
- Zelle/Venmo-like transactions should be conservative and usually require review.

## Data And Room

- Important entities: `RawAlertEntity`, `FinancialSourceEntity`, `TransactionEntity`, `TransactionRuleEntity`.
- Important DAOs: `RawAlertDao`, `FinancialSourceDao`, `TransactionDao`, `TransactionRuleDao`.
- When changing Room schema:
  - Update the entity.
  - Bump the database version.
  - Add a proper migration.
  - Update DAOs/call sites as needed.
- Do not use destructive migrations unless explicitly requested.

## UX Direction

- Keep setup/debug actions in Tools.
- Main workflows should stay clear:
  - Home
  - Spending Summary
  - Review Queue
  - Merchant Review
  - Transactions
  - Sources
  - Tools
- Prefer merchant-level categorization over transaction-by-transaction fixes.
- Transaction Detail can keep correction/debug tools for parser validation and exceptions.
- Avoid adding more clutter to already dense screens unless the task explicitly asks for it.

## Development Style

- Make small, targeted changes.
- Preserve working behavior.
- Do not remove WIP correction tools unless asked.
- Prefer existing project patterns over new abstractions.
- Before large refactors, inspect current files because recent changes may have duplicate helpers, stale UI, or mismatched signatures.
- After code changes, summarize files changed, behavior changed, and how to test in Android Studio.
