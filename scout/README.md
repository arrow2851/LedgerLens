# LedgerLens Source Scout

Experimental Android source scanner. Reads all stored SMS rows after READ_SMS permission, groups received SMS by sender, creates *heuristic* financial-message structure signatures, and exports allowlisted JSON. No INTERNET permission; no raw SMS is saved or uploaded. Local source detail can display a few original sample messages.

Export remains potentially identifying in rare cases: review before sharing.

Build: gradle -p scout :app:assembleDebug

Android 8.0+ (API 26+). This standalone mini app is separate from the existing LedgerLens app; it does not change its database or source logic. The debug APK is development-only, signed using GitHub Actions' ephemeral debug signing key.
