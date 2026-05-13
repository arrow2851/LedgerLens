# LedgerLens Agent Notes

## Read First

- Read `CODEX_HANDOFF.md` before making product, UX, or architecture changes.
- Keep this file short and durable. Put evolving product detail in `CODEX_HANDOFF.md`.
- Current Android package/namespace is `com.example.ledgerlens`; do not rename it unless explicitly asked.
- LedgerLens is a personal/local Android app. It is not optimized for Play Store release yet, but it should still be polished and maintainable.

## Project Shape

- Android app module: `app`
- Main package: `app/src/main/java/com/example/ledgerlens`
- Current stack: Kotlin, Jetpack Compose, Room, KSP
- `MainActivity` should stay thin: Android permission, SMS import entry points, export/share entry points, and `setContent`.
- Compose UI should live under `ui`, organized by workflow or reusable component.
- Data lives under `data`; parser/source/rule/export/business logic lives under `domain`.

## Build And Test

- Build debug APK:
  - `.\gradlew.bat :app:assembleDebug`
- Run unit tests:
  - `.\gradlew.bat :app:testDebugUnitTest`
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

- Spending views include actual spending only:
  - `transactionType == EXPENSE`
  - `excludedFromSpending == false`
- Credit card payments default to transfer/payment treatment and are excluded from spending totals.
- Transfers, balance alerts, payment confirmations, income, refunds, and manually excluded transactions must not inflate spending totals.
- Zelle/Venmo-like transactions should be conservative and usually require review.

## UX Direction

- The mockup direction is the source of truth for product shape:
  - `Spending`
  - `Review`
  - `Activity`
  - `More`
- Main flows should feel like a focused personal finance app, not a debug console.
- Setup, source management, parser diagnostics, imports, exports, and repair tools belong under `More` or deeper maintenance screens.
- Prefer merchant-level categorization over transaction-by-transaction fixes.
- Transaction Detail should expose editable user-facing fields first; parser/debug details should be collapsed or moved deeper.
- Do not keep replaced legacy screens hidden under alternate routes. Either migrate them into the new IA, rename them as tools, or remove them.

## Development Style

- Preserve durable data behavior and parser safety rules.
- Refactor when it improves clarity, separation of concerns, or alignment with the mockups.
- Keep changes coherent and reviewable, but do not avoid larger architecture work when the task explicitly calls for it.
- Prefer existing project patterns where they still fit; improve patterns that are causing clutter or duplication.
- Do not use destructive Room migrations unless explicitly requested.
- After code changes, summarize files changed, behavior changed, and how to test in Android Studio.
