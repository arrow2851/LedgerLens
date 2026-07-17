# AR-0 Self-Critique — Product Reset Assumptions

_Last updated: 2026-07-17_

This document is an internal adversarial self-review of the reset plan. It does **not** satisfy the independent-review requirement in Issue #2. Its purpose is to expose likely blind spots before implementation and give an independent reviewer concrete assumptions to challenge.

## Review scope

- `docs/PRODUCT_RESET_CONTROL.md`
- `docs/PHASE_0_INVENTORY.md`
- current prototype architecture, data model, parser, rules, and UI
- proposed sequencing for the product reset

## Executive assessment

The decision to substantially restructure LedgerLens is justified, but a “rewrite everything” interpretation would create its own risks. The correct strategy is a **controlled product reset with continuously runnable vertical slices**, not a long-lived blank-slate rewrite.

The largest overlooked program risk is not code architecture. It is whether the intended SMS-permission distribution model can be approved and sustained for the target distribution channel. That policy/distribution question must be treated as an early product gate.

## Findings

### AR0-S1 — A broad rewrite can create a long non-runnable branch

- **Severity:** High
- **Evidence:** The inventory classifies nearly every application layer as rewrite. The current app is highly coupled, making it tempting to remove large areas before replacements exist.
- **Impact:** Weeks of changes could accumulate without a usable app, making regressions and product-direction errors harder to detect.
- **Adjustment:** Use a vertical-slice reset:
  1. establish typed domain models and clean schema;
  2. create a minimal app shell;
  3. implement one end-to-end ingestion and review path;
  4. keep the branch buildable after each coherent commit;
  5. remove prototype code only after its replacement is exercised.
- **Validation:** CI must pass on every commit that changes executable code. Each phase should include a demonstrable runnable slice, not only structural code.

### AR0-S2 — SMS distribution and policy viability is an existential dependency

- **Severity:** Blocker
- **Evidence:** The core product requires `READ_SMS`, a highly restricted permission. The plan includes eventual policy preparation but currently places it in release hardening.
- **Impact:** A technically successful app could be impossible to distribute through the intended channel or could require a narrower approved use case than the product assumes.
- **Adjustment:** Move distribution-policy validation to Phase 0/2. Define the intended channel:
  - private sideload/internal use;
  - managed enterprise distribution;
  - Google Play public distribution;
  - alternative store.
  Document the exact permitted-use-case argument and required disclosure/consent UX before deep UI investment.
- **Validation:** Written distribution decision and policy checklist, reviewed before the automatic ingestion feature is considered stable.

### AR0-S3 — Sender-level source identity is necessary but not sufficient

- **Severity:** High
- **Evidence:** One SMS sender can represent multiple accounts, cards, or even institutions. The current source model flattens account hints into a comma-separated string.
- **Impact:** Transactions can be attributed to the wrong account, source confirmation can become misleading, and merchant/rule scope can be too broad.
- **Adjustment:** Keep sender as the top-level message source, but introduce structured account candidates beneath it. A transaction may reference a source and optionally a confirmed account profile.
- **Validation:** Fixture with one sender, two account last-four values, and two account types must remain distinguishable while sharing the same sender identity.

### AR0-S4 — “Identified sources only” needs a preview state

- **Severity:** Medium
- **Evidence:** Strictly refusing to create any transaction representation before source confirmation protects totals but may make onboarding feel empty and prevent the user from understanding what is being confirmed.
- **Impact:** Reduced trust and slower setup; users may not recognize a shortcode without transaction examples.
- **Adjustment:** Allow provisional parse previews for unconfirmed sources, but keep them outside persisted financial activity and all totals. Confirmation can then promote/reprocess eligible messages.
- **Validation:** An unconfirmed source may show masked examples and candidate transaction count while contributing exactly zero to Activity and Insights.

### AR0-S5 — The migration threshold may be too late for serious dogfooding

- **Severity:** Medium
- **Evidence:** The plan begins compatibility at external beta. Internal testing may still accumulate valuable corrections, merchant defaults, and redacted parser fixtures.
- **Impact:** Testers may lose meaningful configuration, reducing confidence and discouraging continued use.
- **Adjustment:** Keep the external-beta threshold as the formal contract, but permit an earlier “persistent dogfood” milestone if internal data becomes valuable. Before that milestone, builds must be visibly labeled disposable.
- **Validation:** The control document must record whether each distributed build is disposable, persistent dogfood, beta, or production.

### AR0-S6 — Four primary destinations may be too much for MVP

- **Severity:** Medium
- **Evidence:** Overview, Activity, Review, and Insights are reasonable concepts, but Insights may not have enough value until transaction quality and history are sufficient.
- **Impact:** Empty or low-value navigation destination, increased implementation scope, and duplicated summary content.
- **Adjustment:** Treat the four-destination model as provisional. MVP may use Overview, Activity, and Review, with Insights entered from Overview until enough data/features justify a primary destination.
- **Validation:** AR-4 navigation testing must compare three- and four-destination structures using first-run and low-data states.

### AR0-S7 — Avoid architecture over-engineering

- **Severity:** Medium
- **Evidence:** The target package structure includes repositories, use cases, mappers, ViewModels, parser strategies, and multiple feature packages. These boundaries are useful, but applying them mechanically can create boilerplate without product value.
- **Impact:** Slow iteration, difficult navigation, and abstractions with only one implementation.
- **Adjustment:** Keep one Android application module initially. Add interfaces where there is a real boundary: Android SMS access, persistence, parser strategies, and testable workflows. Avoid a DI framework or multi-module split until complexity demonstrates the need.
- **Validation:** Every abstraction in Phase 1 should have a stated responsibility and testing benefit. Remove pass-through layers.

### AR0-S8 — Parsed facts and user truth must remain separately auditable

- **Severity:** Blocker
- **Evidence:** The current model mutates parsed values directly. The reset plan says they should be separated, but the exact persistence strategy was not yet defined.
- **Impact:** Re-parsing could overwrite user corrections, rules could silently alter history, and users could not understand why a value changed.
- **Adjustment:** Persist parsed facts separately from explicit user overrides. Compute effective values deterministically. A parser rerun may replace parser-owned facts but never overwrite user-owned corrections.
- **Validation:** Reparse a corrected transaction and prove that user merchant, category, treatment, and spending decisions survive.

### AR0-S9 — Spending requires a signed contribution model, not an include/exclude flag

- **Severity:** Blocker
- **Evidence:** The current `excludedFromSpending` boolean cannot correctly represent refunds, reversals, reimbursements, partial offsets, or uncertain treatment.
- **Impact:** Incorrect monthly totals and category reconciliation.
- **Adjustment:** Model transaction direction/treatment separately and derive a signed spending contribution:
  - purchase/fee/cash withdrawal: positive spend;
  - refund/reversal/reimbursement: negative spend where linked or classified appropriately;
  - transfer/card payment/income/informational: zero;
  - unknown: zero until reviewed or explicitly included.
  Permit a user override of spending treatment when necessary.
- **Validation:** A fixture ledger containing purchases, refund, transfer, card payment, income, and cash withdrawal must reconcile to a manually calculated total.

### AR0-S10 — Rules must not retroactively mutate history without preview and provenance

- **Severity:** Blocker
- **Evidence:** Current phrase rules immediately update many transactions using raw `LIKE` matching.
- **Impact:** Accidental mass recategorization or treatment changes, loss of prior user decisions, and inability to explain totals.
- **Adjustment:** Rules should be persisted as declarative inputs. Applying a rule must:
  1. respect precedence;
  2. never override transaction-specific user decisions unless explicitly requested;
  3. preview affected records for bulk application;
  4. record rule provenance;
  5. support undo or safe recomputation.
- **Validation:** Conflicting merchant, source, and transaction rules must yield deterministic results with an explanation of the winning rule.

### AR0-S11 — Raw SMS retention needs an explicit product policy

- **Severity:** High
- **Evidence:** The product currently stores complete message text indefinitely. The reset plan says data should be private but does not choose a retention strategy.
- **Impact:** Larger privacy exposure, backup risk, and unclear user expectations.
- **Adjustment:** Decide among:
  - retain full message locally for audit/reparse;
  - retain for a configurable period;
  - retain redacted text plus deterministic fingerprint;
  - delete after successful parse while retaining a limited audit record.
  The decision must balance explainability, parser improvement, and privacy.
- **Validation:** Settings must accurately explain retention, deletion must be testable, and parser/review behavior must remain coherent after raw text deletion.

### AR0-S12 — Local-first does not automatically mean sufficiently protected

- **Severity:** High
- **Evidence:** On-device Room storage without backup restrictions or field/database encryption may expose sensitive SMS and transaction data through device backup, rooted-device access, or debugging artifacts.
- **Impact:** Privacy breach involving financial messages and account hints.
- **Adjustment:** Phase 2 must make an explicit security decision covering:
  - backup exclusion;
  - screenshot/recent-app preview behavior for sensitive screens;
  - encrypted database or sensitive-field encryption;
  - logging redaction;
  - debug artifact handling;
  - export format and user consent.
- **Validation:** AR-2 privacy review and a documented threat model before beta.

### AR0-S13 — Parser strategy scope must be deliberately narrow at first

- **Severity:** Medium
- **Evidence:** A generic parser for all financial SMS formats is an open-ended problem. The current heuristic gives an illusion of broad support without reliable correctness.
- **Impact:** High review burden and incorrect totals.
- **Adjustment:** Define supported institutions/message families explicitly. Unknown formats should be marked unsupported or review-required rather than forced into a transaction.
- **Validation:** Published support matrix and fixture coverage for every claimed message family.

### AR0-S14 — Currency and locale assumptions need explicit scope

- **Severity:** Medium
- **Evidence:** The prototype hardcodes USD and US-style amount parsing.
- **Impact:** Incorrect values for international users or messages using alternative separators/currency symbols.
- **Adjustment:** Declare US/USD-only MVP if that is the actual scope. Persist ISO currency codes and design parser APIs so additional locales can be added without changing the core schema.
- **Validation:** Product copy and tests must match the declared currency scope.

### AR0-S15 — Account linking and duplicate financial events may remain ambiguous

- **Severity:** High
- **Evidence:** The same real-world event may generate multiple SMS messages, such as authorization and posting, or a transfer alert from both sending and receiving accounts.
- **Impact:** Double counting despite unique message ingestion.
- **Adjustment:** Distinguish message deduplication from financial-event deduplication. Preserve one transaction candidate per message initially but add duplicate-event detection/review evidence and avoid claiming perfect reconciliation.
- **Validation:** Fixtures for authorization/posting pairs, reversal pairs, and two-sided transfers.

### AR0-S16 — The project needs explicit clocks and timezone semantics

- **Severity:** Medium
- **Evidence:** Current code uses `System.currentTimeMillis()`, `Calendar`, and device-local month boundaries throughout UI code.
- **Impact:** Nondeterministic tests and monthly totals that shift around timezone changes or daylight-saving boundaries.
- **Adjustment:** Store instants in UTC epoch milliseconds, inject a `Clock`, and calculate reporting periods using an explicit user/device `ZoneId` at query/domain boundaries.
- **Validation:** Tests across DST transitions and a transaction near UTC/local month boundaries.

## Recommended sequencing changes

1. Keep Phase 0 open until the distribution channel and SMS-permission viability are documented.
2. Draft and review domain/financial semantics before creating Room entities.
3. Build one runnable vertical slice after the clean schema:
   - redacted fixture ingestion;
   - sender confirmation;
   - one supported parser;
   - Activity row;
   - Review correction;
   - reconciled Overview total.
4. Delay broad Insights and merchant-management UI until this slice passes financial and UX review.
5. Keep the old prototype executable only until the replacement slice covers its essential behaviors, then remove it decisively.

## Self-review conclusion

The reset remains warranted. The plan should be adjusted from “replace all layers by phase” to “replace the foundation through continuously runnable vertical slices.” The following are blockers before broad implementation:

- distribution and `READ_SMS` policy/channel decision;
- separation of parsed facts and user overrides;
- signed spending-contribution semantics;
- declarative, previewable rule behavior;
- raw SMS retention/security decision;
- verified CI execution.

Issue #2 remains open for an independent reviewer to challenge these findings and discover additional ones.
