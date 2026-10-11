# Transaction Lab v0.1 — Functional slice

Purpose: Validate extracting and correcting transactions on a real Android device, before building final LedgerLens.

On phone:
1. Install APK and grant READ_SMS (no INTERNET permission).
2. Scan history; the app retains recognized and reviewable alerts in RAM.
3. Filter Pending to inspect credit-card holds (not proven posted spending).
4. Tap any entry to see original SMS with amount/merchant evidence highlighted.
5. Edit amount, merchant, type, direction, lifecycle, optional category.
6. Save & mark reviewed; reopen later after restarting to check persistence.
7. Ignore false-positive alerts.
8. Export corrections JSON to a device location; no original SMS body in this export.

Not included: automatic merchant categorization, duplicate/reversal reconciliation, expense totals/dashboard, background SMS receiver, RCS/MMS, physical-device instrumentation tests.

Data safety: GitHub contains app source and synthetic tests only, no original user SMS or personal transaction data.
