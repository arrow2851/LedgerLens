# LedgerLens Transaction Lab — v0.1

Second functionality-first mini app (separate package from Source Scout).

- Reads on-device Android SMS provider after granting READ_SMS; raw messages are never uploaded.
- Uses a pure Kotlin/JVM deterministic parser ported from the chat-only Python reference.
- Highlights evidence spans for amount and merchant, lists rule ID, transaction kind, direction, lifecycle and review status.
- User may correct amount, merchant, transaction type, direction, lifecycle and category; save a local SMS-specific override; mark reviewed or ignore.
- Corrections persist in private SharedPreferences (unencrypted; includes merchant names and amounts, **not SMS bodies**). Original SMS are read from the device's SMS provider on each scan.
- User may explicitly export JSON containing correction records. No network permission and no analytics.
- Filter all recognized messages, needs review, pending card charges, confirmed alerts and already-reviewed messages; search merchants/senders/rules.
- No spending totals, post-settlement verification or cross-source deduplication yet. Confidence not calibrated. Prototype parsing matches are NOT confirmed bank transactions.
- Android 8.0+ and READ_SMS permission; build with gradle -p transaction-lab :app:testDebugUnitTest :app:assembleDebug.
- This is separate from your existing LedgerLens app and does not write into that app's database.
- App source is in GitHub. Absolutely no exported SMS data or derived customer financial records committed.
