# LedgerLens2
Fresh-start Android app. Native Jetpack Compose / Material 3. Package com.ledgerlens.two; schema version 1; no migration from LedgerLens 1.0.
Source code and CI contain NO user SMS or financial exports.
The original LedgerLens package com.example.ledgerlens is untouched.

Build: ./gradlew :ledgerlens2:testDebugUnitTest :ledgerlens2:assembleDebug
An APK is uploaded by GitHub Actions on pushes to feat/LedgerLens2.
READ_SMS is requested only for on-device scan. No INTERNET permission.
Transactions require bank/source approval before entering spending; ambiguous P2P and transfers are excluded.
The app uses native document picker for CSV export and complete portable JSON backup/restore.

## Charge-or-hold accounting policy (November 2026)
Issuer SMS alerts worded "charge or hold" are **included in spending** under the unified expense view once their SMS source is approved. They appear beside posted expenses in Activity and expense category totals, not in a separate pending bucket. The internal `HOLD` treatment preserves the original alert meaning for inspection and future reconciliation; it does not mean confirmed bank settlement. Scheduled future payments, card-bill payments, transfers, and income remain outside spending. Because SMS alerts alone do not establish settlement, any same-amount candidate duplicates are review flags, not automatically deleted records.
