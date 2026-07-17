# LedgerLens Reset — Continuously Runnable Implementation Slices

_Last updated: 2026-07-17_

This plan modifies the phase roadmap so the reset does not become a long blank-slate rewrite. Each slice must leave the branch buildable and produce a demonstrable product behavior.

## Slice 0 — Build and fixture harness

**Goal:** obtain a trusted build/test loop before replacing behavior.

Deliverables:

- GitHub Actions executes unit tests and lint;
- local/reproducible Gradle commands documented;
- redacted fixture-message source available in debug/test code;
- template tests removed;
- first financial-semantics tests added without changing production UI.

Demo:

- CI passes;
- fixture ledger calculates documented spending contribution.

Removal allowed:

- template tests only.

## Slice 1 — Typed financial semantics

**Goal:** establish domain truth independent of Room and UI.

Deliverables:

- `Money`, `TransactionTreatment`, `MoneyDirection`, `SpendingEffect`;
- signed contribution calculation;
- typed review reasons;
- Clock/time-period abstractions;
- reconciliation tests for purchase, fee, refund, transfer, card payment, income, reversal, and unresolved cash withdrawal.

Demo:

- deterministic fixture ledger total and explanation output.

Removal allowed:

- no production behavior removed yet.

## Slice 2 — Clean Room V1 foundation

**Goal:** create the new database alongside the prototype until a usable end-to-end path exists.

Deliverables:

- source/account/message/transaction/override/category/merchant/rule/import-run entities;
- foreign keys and indices;
- schema export;
- system-category seed;
- DAO and repository tests;
- deterministic message fingerprint.

Demo:

- fixture messages persist idempotently;
- parser facts can be updated while user override survives.

Removal allowed:

- none of the old UI yet; old database may remain temporarily under a clearly named prototype boundary.

## Slice 3 — One supported parser vertical path

**Goal:** prove message → source → transaction candidate → review semantics.

Deliverables:

- debug fixture ingestion through the new repository;
- sender normalization and candidate source;
- one explicitly supported message family/parser strategy;
- source confirmation use case;
- parsed transaction with evidence/review reasons;
- no `READ_SMS` dependency required for the demo.

Demo:

- confirm fixture source and see one new-domain transaction candidate.

Removal allowed:

- duplicated parser helper code only after fixture parity is demonstrated.

## Slice 4 — Minimal app shell and Activity

**Goal:** replace the 4,319-line Activity host without rebuilding all screens.

Deliverables:

- minimal `MainActivity`;
- `LedgerLensApp`;
- proper navigation;
- LedgerLens theme;
- provisional Overview, Activity, Review destinations using new repositories;
- Settings entry;
- explicit loading/empty/error states.

Demo:

- navigate predictably among destinations with fixture data and correct system back behavior.

Removal allowed:

- old conditional navigation and old Home/Tools shell after parity for essential access is available.

## Slice 5 — Activity and transaction correction

**Goal:** make the first complete user-value loop usable.

Deliverables:

- compact date-grouped Activity list;
- user-first transaction detail;
- merchant/category/treatment/spending-effect edits;
- transaction-specific overrides;
- collapsed parser evidence/original-message preview;
- undo for recent correction.

Demo:

- correct a fixture transaction and prove correction survives parser reprocessing.

Removal allowed:

- old transaction list/detail/correction functions.

## Slice 6 — Focused Review queue

**Goal:** resolve ambiguity efficiently.

Deliverables:

- prioritized review item;
- one-item-at-a-time decision flow;
- Save & next, Skip, Undo;
- queue progress;
- rule/default suggestion separated from transaction-only correction.

Demo:

- resolve a mixed fixture queue without accidental bulk changes.

Removal allowed:

- old review queue and helper predicates.

## Slice 7 — Rule preview and merchant defaults

**Goal:** reduce repeated corrections safely.

Deliverables:

- merchant profiles/default categories;
- declarative rules;
- impact preview;
- deterministic precedence/conflict handling;
- disable/undo behavior;
- provenance in effective transaction.

Demo:

- create a rule, preview affected transactions and spending delta, apply it, then disable it and restore prior effective values.

Removal allowed:

- old raw-SMS `LIKE` bulk mutation code and rule DAO cross-table updates.

## Slice 8 — Overview and reconciliation

**Goal:** deliver trusted at-a-glance value.

Deliverables:

- current-period signed spending total;
- attention count;
- category preview;
- recent activity;
- explanation of excluded/unresolved events;
- reconciliation tests against Activity.

Demo:

- Overview total equals independently calculated fixture ledger.

Removal allowed:

- old spending-summary calculations in Composables.

## Slice 9 — Android SMS ingestion and onboarding

**Goal:** connect the proven product loop to real device data only after distribution/privacy decisions are explicit.

Prerequisites:

- distribution channel decision;
- SMS permission policy checklist;
- raw-body retention decision;
- backup/encryption/logging policy;
- permission-denial/recovery UX.

Deliverables:

- isolated Android SMS data source;
- informed onboarding;
- permission flow;
- automatic idempotent import coordinator;
- progress, partial failure, retry;
- source confirmation from real data.

Demo:

- grant, deny, revoke, retry, re-run, and interrupt ingestion without duplicates or data corruption.

Removal allowed:

- old Activity SMS query/import functions and release Tools workflow.

## Slice 10 — Insights, settings, privacy controls, and hardening

**Goal:** complete the product after the core loop is trustworthy.

Deliverables:

- category/merchant/month trends;
- source/account/rule management;
- import status and rescan;
- full reset and raw-data deletion;
- accessibility, performance, privacy, policy, and release reviews.

Demo:

- full release-candidate scenario with reconciliation and destructive-action verification.

## Slice completion rule

A slice is complete only when:

1. build and applicable tests pass;
2. demo behavior is reproducible;
3. control and critique logs are updated;
4. no new blocker/high finding is silently deferred;
5. old code is removed only after the replacement behavior is exercised;
6. the relevant adversarial checkpoint has been created or completed.
