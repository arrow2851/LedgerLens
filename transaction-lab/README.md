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

## v0.2 functionality

Source picker is mandatory before transaction scanning: discover on device, view sender labels with inferred institution and potential transaction counts, choose Suggested/All/custom senders, save selection, then scan only chosen senders. Change Sources and rescan later.

Transaction detail fields are separate: institution, payment method, sender/recipient/card issuer, cardholder if explicitly named, card last four when available, account last four when evidenced. Merchants apply to purchases; Zelle and card repayments are described by their specific roles. Authorization holds remain provisional, repayments are not additional purchases, scheduled payments and OTPs are ignored when identifiable. User corrections can be exported and imported as JSON through the Android system picker.

No user financial data is in GitHub. The app has no INTERNET permission. The local correction store contains merchant/party names and values (not SMS bodies) in private app preferences. Review sensitive correction exports before sharing. A debug APK built on a new CI runner may have a different signing key and require uninstalling the older Transaction Lab first; export its corrections before uninstalling.


## v0.3 — user experience rebuild

- An independent, polished design system with warm-paper canvas, forest-green actions, intentional typography, low-contrast separators and minimum 46–54dp major touch targets.
- Source-first onboarding with on-device sender discovery, ranked and searchable source cards, explicit selection, suggested/all filtering, and a persistent primary scan action.
- Transaction workbench: focused summary, review/pending/confirmed states, search across source and transaction details, review filters, and secondary actions in the Manage menu.
- Transaction evidence editor: clear amount and transaction roles, separate cardholder/counterparty fields, structured type/direction/status controls, expandable highlighted original SMS, sticky save and review actions.
- No internet permission or server analytics; sensitive source data stays on device.
- Corrections import/export and selected-sender persistence remain compatible with v0.2.
- This is a functional mini app: confidence scores, source authentication, accessibility instrumentation, settlement reconciliation and finance analytics are future work.
