# LedgerLens Product Reset — Control Document

_Last updated: 2026-07-17_

This is the living source of truth for the LedgerLens product reset. It records current status, product decisions, architecture and UX direction, critiques, risks, completed work, next work, and independent adversarial-review checkpoints.

## 1. Program status

- **Repository:** `arrow2851/LedgerLens`
- **Working branch:** `refactor/product-reset`
- **Base branch:** `master`
- **Current phase:** Phase 0 — baseline audit and reset preparation
- **Release status:** Unreleased prototype
- **Compatibility policy:** Prototype-era database and UI compatibility are not product requirements. Prefer the cleanest correct design until an explicit beta data-retention commitment is made.
- **Overall health:** Core concept is promising; architecture, privacy posture, data model, workflow orchestration, tests, and UI/UX require substantial restructuring before feature expansion.

## 2. Product mission

LedgerLens should be a private, local-first financial activity assistant that turns supported financial SMS alerts into an understandable transaction history, review workflow, and spending picture.

The product should feel:

- trustworthy and privacy-forward;
- calm rather than alarmist;
- automatic rather than procedural;
- fast to review and correct;
- clear about what counts as spending;
- transparent when the system is uncertain.

The user should not need to understand the internal pipeline of importing, source detection, parsing, and rule application.

## 3. Binding product rules

These rules should remain stable unless explicitly changed in this document.

1. **SMS-first source identity**
   - The top-level source identity is the SMS sender, shortcode, or phone number.
   - Institution, account, and card hints are attributes beneath that sender, not separate top-level sources.

2. **Identified sources only**
   - Only confirmed/identified financial sources may create transactions.
   - Unknown or dismissed senders must not silently enter financial totals.

3. **Spending means actual spending**
   - Spending summaries include transactions whose effective treatment contributes a positive spending impact.
   - Transfers, credit-card payments, informational alerts, and excluded transactions do not count as spending by default.
   - Refunds and reimbursements should reduce spending where appropriate rather than appear as ordinary expenses.

4. **Minimize transaction-by-transaction correction**
   - Prefer merchant defaults and reusable rules.
   - Transaction-level overrides remain available for exceptions.
   - Rule precedence target:
     1. transaction-specific override;
     2. user exception rule;
     3. merchant default;
     4. source-specific suggestion;
     5. generic fallback;
     6. needs review.

5. **No premature backward compatibility**
   - The application has not shipped.
   - Old prototype schemas, navigation decisions, and copy may be removed rather than preserved.
   - Database migrations begin only when a beta or production data-retention contract is declared.

6. **Privacy by default**
   - Processing should remain on-device unless a later feature explicitly requires otherwise.
   - Raw SMS and account hints should be masked or collapsed in ordinary UI.
   - Technical data and parser details must not dominate normal user screens.

## 4. Current-state critique

### Architecture

- `MainActivity.kt` currently owns permission handling, SMS querying, importing, parsing, source detection, rule application, navigation, state, database access, and most screens.
- Compose UI calls DAOs and coordinates dispatchers directly.
- Navigation is a conditional state machine rather than a proper navigation graph.
- Mutable selected objects and repeated `!!` use create lifecycle and race-condition risk.
- Domain concepts are stored and compared as unvalidated strings.
- Prototype database migrations create historical complexity without any released user data to preserve.

### Data and processing

- SMS import performs per-message duplicate queries rather than using a durable unique constraint and batched insertion.
- Raw SMS bodies, account hints, and financial records need stronger backup and storage controls.
- Parsing is useful as a prototype but relies on broad keyword and first-amount heuristics.
- Parsing, source detection, confidence scoring, and rules require table-driven tests.
- The current model risks conflating accounting treatment, spending impact, and user-facing category.

### UI/UX

- The product exposes internal operations such as Backfill, Detect Sources, Parse Identified Sources, and Clear Test Data.
- Home behaves like a menu of developer feature cards rather than a financial dashboard.
- Most screens are long vertical stacks of generic cards and full-width buttons.
- Transaction detail presents internal fields and parser metadata before user-relevant actions.
- Correction flows require excessive scrolling and cognitive effort.
- Review is not optimized for rapid sequential decisions.
- Visual styling is largely default Material styling and does not yet communicate a distinct trustworthy financial identity.
- Empty, loading, error, permission-denied, undo, and recovery states are incomplete.

## 5. Target product experience

### Provisional primary information architecture

The current recommended product structure is:

1. **Overview** — monthly spending, items needing attention, category preview, and recent activity.
2. **Activity** — searchable and filterable transaction history.
3. **Review** — a focused queue for uncertain or incomplete transactions.
4. **Insights** — category, merchant, and monthly trend analysis.

**Settings** is accessed from the top app bar and contains Sources, SMS/privacy controls, import status, learned rules, data management, and app preferences.

This structure is provisional until the dedicated navigation adversarial review. The test is whether a user can understand where to go without knowing LedgerLens internals.

### Main workflow

1. Explain what LedgerLens does and why SMS access is requested.
2. Receive informed permission.
3. Automatically scan supported messages.
4. Detect likely financial senders.
5. Ask the user to confirm only ambiguous sources.
6. Parse transactions automatically from identified sources.
7. Show immediate value on Overview.
8. Surface uncertain items in Review.
9. Learn merchant defaults and rules from corrections.
10. Keep technical diagnostics out of the normal workflow.

## 6. Target technical architecture

```text
app/
├── MainActivity.kt
├── LedgerLensApp.kt
├── navigation/
│   ├── AppDestination.kt
│   └── LedgerLensNavHost.kt
├── data/
│   ├── local/
│   │   ├── LedgerLensDatabase.kt
│   │   ├── dao/
│   │   └── entity/
│   ├── repository/
│   └── mapper/
├── domain/
│   ├── model/
│   ├── parser/
│   ├── detector/
│   ├── rules/
│   └── usecase/
└── ui/
    ├── overview/
    ├── activity/
    ├── review/
    ├── insights/
    ├── onboarding/
    ├── settings/
    └── design/
```

### Architectural rules

- Composables render immutable UI state and emit events.
- ViewModels coordinate user-facing state and use cases.
- Repositories own data access.
- Use cases own business workflows.
- DAOs do not appear in UI code.
- SMS provider access is isolated behind a data source/repository boundary.
- Domain models use typed enums/value objects.
- Room entities are not the primary UI models.
- Coroutine work is lifecycle-aware.
- Debug tooling is excluded from release UI.

## 7. Execution plan

### Phase 0 — Baseline audit and reset preparation

**Objective:** freeze the prototype conceptually, document what is reusable, and prepare a safe clean rebuild path.

- [x] Establish `refactor/product-reset` branch.
- [x] Create this control document.
- [ ] Produce complete code inventory and dependency map.
- [ ] Identify reusable domain behavior versus code to replace.
- [ ] Record current build/test status.
- [ ] Decide minimum supported Android version.
- [ ] Define beta compatibility threshold.
- [ ] Remove or quarantine committed IDE-only files.

**Exit criteria:** agreed reset scope, build baseline known, critical product rules documented, no uncertainty about what is being preserved.

**Adversarial review checkpoint AR-0:** independent audit of reset assumptions. Reviewer must challenge whether important behavior is being discarded, whether hidden compatibility requirements exist, and whether the proposed rebuild scope is too broad or too narrow.

### Phase 1 — Clean domain and database foundation

**Objective:** establish the product model without prototype migration baggage.

- [ ] Define typed transaction treatment, review status, source status, spending impact, category, and rule models.
- [ ] Separate accounting treatment from spending impact and category.
- [ ] Redesign Room entities and constraints.
- [ ] Reset database to a clean version 1 schema.
- [ ] Add unique SMS identity protection and indices.
- [ ] Add repository interfaces and implementations.
- [ ] Add Room migration/schema tests appropriate to the new baseline.

**Exit criteria:** clean schema, typed domain model, repository boundary, tests for constraints and core calculations.

**Adversarial review checkpoint AR-1:** data-model and financial-semantics review. Reviewer should search for double counting, incorrect refund/payment behavior, source collisions, duplicate ingestion, and ambiguous treatment/category coupling.

### Phase 2 — Secure automatic ingestion pipeline

**Objective:** replace manual pipeline buttons with one reliable, observable workflow.

- [ ] Isolate SMS access.
- [ ] Implement informed permission and denial recovery.
- [ ] Batch import with deterministic deduplication.
- [ ] Detect and confirm financial sources.
- [ ] Parse identified-source messages.
- [ ] Apply rules and defaults.
- [ ] Make the workflow idempotent and resumable.
- [ ] Define import progress, partial failure, and retry states.
- [ ] Disable or explicitly constrain sensitive backups.

**Exit criteria:** one automatic workflow produces stable results when run repeatedly and handles denial, interruption, malformed messages, and duplicate messages safely.

**Adversarial review checkpoint AR-2:** privacy, abuse, data-loss, and failure-mode review. Reviewer should assume hostile or malformed SMS input, interrupted jobs, permission changes, device restores, duplicate records, and sensitive-data exposure.

### Phase 3 — Parser and rule-engine hardening

**Objective:** make classification measurable, explainable, and correctable.

- [ ] Introduce sender/institution-specific parser strategies.
- [ ] Separate amount candidates such as transaction amount, balance, limit, and payment amount.
- [ ] Capture confidence reasons, not only a score.
- [ ] Implement explicit rule precedence.
- [ ] Add merchant normalization and user correction learning.
- [ ] Add broad table-driven parser tests using redacted fixtures.
- [ ] Define unsupported-message behavior.

**Exit criteria:** supported formats have test coverage; ambiguity routes to Review; rules apply predictably.

**Adversarial review checkpoint AR-3:** parser red-team review. Reviewer should construct misleading messages with multiple amounts, negations, declines, reversals, refunds, balances, transfers, dates, and merchant-like phrases.

### Phase 4 — App shell and design system

**Objective:** establish a polished, consistent product foundation before rebuilding screens.

- [ ] Apply a LedgerLens theme consistently.
- [ ] Define color, typography, spacing, elevation, iconography, shapes, and component tokens.
- [ ] Add proper Navigation Compose structure.
- [ ] Implement primary navigation and Settings entry.
- [ ] Build shared transaction rows, amount formatting, chips, banners, empty states, loading states, and error states.
- [ ] Validate dark mode, large text, TalkBack labels, contrast, and touch targets.

**Exit criteria:** app shell and common components are production-quality and reusable.

**Adversarial review checkpoint AR-4:** navigation, accessibility, and design-system consistency review. Reviewer should test screen-reader semantics, large fonts, one-handed use, state restoration, back behavior, and destination discoverability.

### Phase 5 — Onboarding and Overview

**Objective:** deliver immediate value and establish trust.

- [ ] Privacy-first onboarding.
- [ ] Permission explanation and recovery.
- [ ] Automatic setup progress.
- [ ] Overview with current-period spending, attention count, category preview, recent activity, and refresh state.
- [ ] Useful first-run, no-data, and partial-data experiences.

**Exit criteria:** a new user reaches meaningful value without understanding the internal processing pipeline.

**Adversarial review checkpoint AR-5:** first-run usability review. Reviewer should start with no context, deny permission once, grant later, have no supported SMS, and have ambiguous sources.

### Phase 6 — Activity and transaction detail

**Objective:** make browsing and correcting financial activity fast and understandable.

- [ ] Date-grouped compact transaction list.
- [ ] Search and meaningful filters.
- [ ] Human-readable transaction treatment.
- [ ] Transaction detail led by amount, merchant, date, account, category, and spending effect.
- [ ] Collapsed technical/parser details.
- [ ] Merchant/category/treatment edits with undo.
- [ ] Rule creation expressed in user language.

**Exit criteria:** ordinary transaction correction does not require navigating a long technical form.

**Adversarial review checkpoint AR-6:** transaction comprehension review. Reviewer should test confusing payments, tuition, reimbursements, P2P transfers, cash withdrawals, missing merchants, and incorrect categories.

### Phase 7 — Review workflow

**Objective:** reduce uncertainty through a focused queue.

- [ ] Prioritized review reasons.
- [ ] One-item-at-a-time correction.
- [ ] Save and next.
- [ ] Skip, undo, and queue progress.
- [ ] Suggested merchant, category, treatment, and source decisions.
- [ ] Bulk/rule options only when consequences are clear.

**Exit criteria:** users can resolve a review queue quickly without accidental bulk changes.

**Adversarial review checkpoint AR-7:** decision-quality and accidental-action review. Reviewer should look for misleading defaults, irreversible actions, bulk-rule overreach, and unclear spending consequences.

### Phase 8 — Insights

**Objective:** turn clean transaction data into understandable financial insight.

- [ ] Monthly spending totals.
- [ ] Category breakdown and drilldown.
- [ ] Merchant analysis.
- [ ] Month-over-month trends.
- [ ] Clear explanation of included and excluded activity.
- [ ] Graceful low-data states.

**Exit criteria:** totals reconcile with Activity and remain understandable without accounting knowledge.

**Adversarial review checkpoint AR-8:** reconciliation review. Reviewer independently calculates totals from fixture data and challenges every exclusion and negative-spending case.

### Phase 9 — Settings, privacy, and data controls

**Objective:** make sensitive controls accessible without cluttering daily use.

- [ ] Manage identified and ignored sources.
- [ ] Manage merchant defaults and learned rules.
- [ ] Import status and re-scan controls.
- [ ] Privacy explanation and permission state.
- [ ] Delete imported data and reset app with confirmation.
- [ ] Export strategy decision.
- [ ] Developer tools behind debug builds only.

**Exit criteria:** users can understand, inspect, and delete their data safely.

**Adversarial review checkpoint AR-9:** privacy-control and destructive-action review. Reviewer should test permission revocation, reset, source removal, rule deletion, and sensitive-data visibility.

### Phase 10 — Release hardening

**Objective:** validate the complete product as a releasable system.

- [ ] Unit, database, parser, ViewModel, and UI tests.
- [ ] Performance and large-SMS-history testing.
- [ ] Crash and recovery testing.
- [ ] Accessibility pass.
- [ ] Security/privacy review.
- [ ] Play policy and permission declaration preparation.
- [ ] Release build shrinking/obfuscation decision.
- [ ] Documentation and known-limitations review.

**Exit criteria:** release candidate meets defined quality gates.

**Adversarial review checkpoint AR-10:** full release red-team. Reviewers must attempt to break correctness, privacy, navigation, data retention, reconciliation, and recovery before merge.

## 8. Independent adversarial-review protocol

At every checkpoint:

1. Create a dedicated GitHub issue titled `AR-X: <checkpoint name>`.
2. Review the branch at a pinned commit SHA.
3. The reviewer must not rely on the implementation author's assumptions.
4. The review must include:
   - scope examined;
   - evidence and reproduction steps;
   - severity (`blocker`, `high`, `medium`, `low`);
   - likely user impact;
   - recommended fix;
   - tests that should prevent recurrence.
5. Add all findings to the critique log below.
6. Block phase completion for unresolved blocker/high findings.
7. Re-run the review after fixes when the affected behavior is security-, privacy-, data-, or financial-correctness-related.

Recommended independent review roles:

- financial semantics/reconciliation reviewer;
- privacy and hostile-input reviewer;
- Android architecture reviewer;
- accessibility and UX reviewer;
- parser test/red-team reviewer.

## 9. Decision log

| Date | Decision | Rationale | Status |
|---|---|---|---|
| 2026-07-17 | Treat current app as an unreleased prototype rather than a compatibility baseline. | Existing incremental changes and migrations are making the code harder to reason about without protecting real users. | Accepted |
| 2026-07-17 | Use `refactor/product-reset` as the controlled rebuild branch. | Keeps `master` stable and provides one reviewable workstream. | Accepted |
| 2026-07-17 | Maintain this document as the source of truth. | Supports continuity across multiple work sessions and prevents undocumented drift. | Accepted |
| 2026-07-17 | Prefer an automatic user workflow over manual Backfill/Detect/Parse steps. | Users should receive financial value, not operate the implementation pipeline. | Accepted |
| 2026-07-17 | Use Overview, Activity, Review, and Insights as the provisional primary destinations. | Best matches the recommended user mental model from the latest UX review; subject to AR-4 validation. | Provisional |

## 10. Critique and discovery log

Add findings continuously, including issues discovered outside the planned phase.

| ID | Area | Finding | Severity | Status | Planned resolution |
|---|---|---|---|---|---|
| C-001 | Architecture | `MainActivity.kt` combines application, workflow, data, and UI responsibilities. | Blocker | Open | Phases 1–4 |
| C-002 | Privacy | Raw SMS/financial data backup behavior is not adequately constrained. | High | Open | Phase 2 |
| C-003 | Compatibility | `minSdk = 36` unnecessarily limits installability. | High | Open | Phase 0/4 |
| C-004 | Data integrity | SMS deduplication depends on per-record queries without a unique persisted key constraint. | High | Open | Phase 1/2 |
| C-005 | Reliability | Unmanaged coroutine scope and nullable selected-state assertions can outlive UI state. | High | Open | Phase 1/4 |
| C-006 | Parsing | First matching amount and broad keyword rules can select balances or misclassify transactions. | High | Open | Phase 3 |
| C-007 | Testing | Existing tests do not validate product behavior. | Blocker | Open | All phases |
| C-008 | UX | Main navigation exposes features and tools rather than user goals. | High | Open | Phases 4–5 |
| C-009 | UX | Transaction correction is a long technical form with excessive actions and scrolling. | High | Open | Phases 6–7 |
| C-010 | Visual design | App presentation is largely default Material styling with weak product identity and hierarchy. | Medium | Open | Phase 4 |

## 11. Change log

| Date | Change | Commit/PR | Notes |
|---|---|---|---|
| 2026-07-17 | Created product reset branch and control document. | Pending draft PR | Program initialization. |

## 12. Immediate next work

1. Complete a repository inventory and establish whether the current project builds from a clean checkout.
2. Map current entities, DAOs, parser behavior, source behavior, rules, and screens to `retain`, `rewrite`, or `remove`.
3. Decide supported Android API floor based on actual feature needs.
4. Draft the clean domain model and version-1 database schema.
5. Create AR-0 as the first independent reset-assumption review issue before destructive restructuring begins.

## 13. Update discipline

At the end of each meaningful work unit:

- update Program status;
- check completed work;
- update the critique/discovery log;
- add decisions and rationale;
- add a change-log row with commit or PR reference;
- state the next concrete work unit;
- create an adversarial-review issue when a checkpoint is reached.

No phase is considered complete merely because code exists. It must satisfy its exit criteria and pass its checkpoint review.