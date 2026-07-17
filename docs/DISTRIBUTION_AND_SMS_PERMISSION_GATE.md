# LedgerLens Distribution and SMS Permission Gate

_Status: Open product gate_

_Last updated: 2026-07-17_

## Why this gate exists

LedgerLens depends on reading financial SMS messages. `READ_SMS` is a highly restricted Android permission, and distribution requirements differ materially between public Google Play release, managed/private distribution, and sideloading.

This gate must be resolved before the real-device ingestion slice is considered approved. The app can continue development with redacted fixtures while this question is resolved.

## Distribution options to decide

### Option A — Public Google Play distribution

Requires early validation that LedgerLens fits an allowed core-use-case category and can satisfy all declaration, disclosure, consent, privacy, and data-use requirements.

Risks:

- permission declaration rejection;
- product scope constrained by allowed use case;
- recurring policy-review burden;
- marketing/store copy must match actual core functionality precisely.

### Option B — Managed/private enterprise distribution

May avoid public-store declaration flow depending on deployment method, but introduces device-management, organizational ownership, and support constraints.

### Option C — Direct sideload/internal use

Simplifies store policy but materially limits reach, updates, trust, and installation usability. It is acceptable for development and personal dogfooding, not automatically a viable consumer release strategy.

### Option D — Alternative input strategy

Potential alternatives should be evaluated only if they preserve the product’s value:

- user-imported statement/CSV data;
- notification-listener ingestion where appropriate and permitted;
- email alert import;
- bank/account aggregation provider;
- hybrid model.

Alternatives have their own privacy, reliability, and distribution tradeoffs. Do not assume notification access is automatically safer or more acceptable.

## Required decision record

Before Android SMS ingestion is approved, record:

1. intended initial distribution channel;
2. target users and countries;
3. why SMS access is essential to the app’s core function;
4. exact data collected and retained;
5. whether processing is entirely on-device;
6. raw-message retention policy;
7. backup/encryption/logging controls;
8. onboarding disclosure and consent requirements;
9. permission denial and revocation behavior;
10. store/deployment declaration owner;
11. fallback plan if SMS access cannot be approved.

## Development policy until resolution

- continue domain, schema, parser, UI-shell, and fixture-based vertical-slice work;
- do not design the whole onboarding experience around guaranteed store approval;
- isolate Android SMS access behind `SmsDataSource`;
- do not add cloud transmission of SMS content;
- keep this gate visible in the control document and PR.

## Exit criteria

This gate closes only when the intended distribution route and SMS-permission compliance path are documented well enough to guide implementation and release scope.
