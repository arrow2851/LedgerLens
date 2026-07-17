# LedgerLens preview signing

This directory contains a **test-only** signing key encoded as Base64.

It exists so GitHub-built `LedgerLens Preview` APKs can update one another without uninstalling the app or erasing the preview database. The debug application ID is `com.example.ledgerlens.preview`, so it can coexist with the original prototype.

Important boundaries:

- This key is intentionally public and provides no production trust.
- It must never sign a Play Store, beta, production, or non-preview package.
- A future release application ID and release signing key must be created separately and stored outside the repository.
- The decoded `.keystore` file is generated during Gradle configuration and is ignored by Git.

Preview credentials:

- alias: `ledgerlens-preview`
- store/key password: `ledgerlens-preview-2026`
