# LedgerLens2
Fresh-start Android app. Native Jetpack Compose / Material 3. Package com.ledgerlens.two; schema version 1; no migration from LedgerLens 1.0.
Source code and CI contain NO user SMS or financial exports.
The original LedgerLens package com.example.ledgerlens is untouched.

Build: ./gradlew :ledgerlens2:testDebugUnitTest :ledgerlens2:assembleDebug
An APK is uploaded by GitHub Actions on pushes to feat/LedgerLens2.
READ_SMS is requested only for on-device scan. No INTERNET permission.
Transactions require bank/source approval before entering spending; ambiguous P2P and transfers are excluded.
The app uses native document picker for CSV export and complete portable JSON backup/restore.
