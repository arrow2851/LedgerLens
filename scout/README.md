# LedgerLens Source Scout v0.2

Separate experimental app that reads stored SMS rows with explicit \`READ_SMS\` permission.

## Export options

- **Safe JSON** — previous version's source counts and generalized structural patterns.
- **Full original SMS JSON** — unredacted content of **every SMS row**, including incoming, sent, draft and other SMS types, preserving exact body text, sender, provider SMS ID, timestamps, thread ID and message type. Save using Android's system file picker.
- **Financial candidates JSON** — same full unredacted fields but restricted to received messages classified as likely financial; this heuristic may miss real transactions.

**No SMS are automatically uploaded or committed to GitHub.** This source repository contains program code only. Neither the app nor its build workflow receives any personal SMS data. No \`INTERNET\` permission. Export happens on the phone; the user explicitly selects a save location. These JSON files are unencrypted and can contain OTPs, private conversations and financial identifiers. Review the file before sharing, and delete unneeded copies. Export does not include RCS/MMS, which are outside the SMS provider.

Building: \`gradle -p scout :app:assembleDebug\`

Android 8.0+, debug build. v0.2 might need uninstalling v0.1 first due to independent debug signing keys. This is separate from the full LedgerLens financial app, which remains untouched.
