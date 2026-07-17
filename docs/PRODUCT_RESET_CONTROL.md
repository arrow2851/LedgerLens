# LedgerLens Product Reset — Living Control Document

_Last updated: 2026-07-17_

This is the authoritative status and decision record for the LedgerLens reset. Detailed design and review material lives in the linked supporting documents.

## 1. Current status

| Item | Current value |
|---|---|
| Repository | `arrow2851/LedgerLens` |
| Working branch | `refactor/product-reset` |
| Draft PR | #1 — Product reset: architecture, data, and UX rebuild |
| Release status | Unreleased prototype |
| Current phase | Phase 0 — reset preparation and reviewed foundation |
| Current implementation slice | Slice 0 complete; typed financial-semantics work started |
| Latest verified executable commit | `4001176e816a3f05f3a2a782443d9dcef038ab43` |
| CI evidence | Android CI run `29602093855` / run #106 passed |
| Build verification | Gradle wrapper, `testDebugUnitTest`, `lintDebug`, diagnostics upload, and final gate passed |
| Android minimum | API 23 |
| Compile/target SDK | 36 |
| Database compatibility | Prototype data is disposable until an explicit persistent-dogfood/beta contract |

### Phase 0 remains open because

- AR-0 independent reset review is open in Issue #2;
- the SMS distribution/permission product gate is open in Issue #4;
- AR-1 domain/schema reconciliation review is open in Issue #5.

No production Room V1 replacement or release-intended real SMS ingestion begins until the relevant gates are satisfied.

## 2. Supporting documents

- [`PHASE_0_INVENTORY.md`](PHASE_0_INVENTORY.md) — retain/rewrite/remove inventory.
- [`AR_0_SELF_CRITIQUE.md`](AR_0_SELF_CRITIQUE.md) — internal adversarial critique; not an independent approval.
- [`DOMAIN_AND_SCHEMA_V1.md`](DOMAIN_AND_SCHEMA_V1.md) — proposed typed domain model and Room V1 schema.
- [`IMPLEMENTATION_SLICES.md`](IMPLEMENTATION_SLICES.md) — continuously runnable replacement sequence.
- [`DISTRIBUTION_AND_SMS_PERMISSION_GATE.md`](DISTRIBUTION_AND_SMS_PERMISSION_GATE.md) — restricted-permission release gate.
- [`REVIEW_CHECKPOINT_INDEX.md`](REVIEW_CHECKPOINT_INDEX.md) — AR-0 through AR-10 schedule.
- [`PHASE_0_ACCEPTANCE.md`](PHASE_0_ACCEPTANCE.md) — Phase 0 closure checklist.
- [`QUALITY_GATES.md`](QUALITY_GATES.md) — build, financial, integrity, privacy, UX, and release gates.

## 3. Product mission

LedgerLens should be a private, local-first financial activity assistant that turns supported financial SMS alerts into an understandable transaction history, focused review workflow, and trustworthy spending picture.

It should feel:

- trustworthy and privacy-forward;
- calm rather than alarmist;
- automatic rather than procedural;
- fast to review and correct;
- clear about what counts as spending;
- transparent when uncertain.

The user should experience goals and results, not Backfill/Detect/Parse pipeline operations.

## 4. Binding product rules

### Source identity

- The top-level source is the SMS sender, shortcode, or phone number.
- Institution and account/card candidates are structured children beneath that sender.
- One sender may represent several accounts.

### Confirmation gate

- Only identified sources create effective Activity transactions or contribute to Insights.
- Candidate sources may show masked previews and candidate counts without affecting totals.

### Financial meaning

- Parsed facts, user overrides, rule/default results, and effective values remain distinguishable.
- Treatment, account-flow direction, category, and spending effect are separate concepts.
- Transaction amounts are non-negative minor-unit magnitudes.
- Spending effect produces a signed contribution:
  - purchase/fee → increase;
  - refund/reversal/reimbursement → decrease;
  - transfer/card payment/income/informational → none;
  - cash withdrawal/unknown → unresolved by default.

### User ownership

- Parser reruns may replace parser-owned facts.
- Parser reruns never overwrite transaction-specific user corrections.
- Source detection reruns never overwrite user source status or labels.

### Rules

- Transaction-specific overrides have highest precedence.
- Rules are declarative, deterministic, previewable, explainable, and reversible.
- Equal-priority conflicts create review instead of relying on database row order.
- Rule DAOs never bulk-rewrite financial history.

### Compatibility

- The app has not shipped; prototype schema and UI compatibility are not requirements.
- A clean Room version 1 is planned rather than migration version 6.
- Begin durable migrations at the first build explicitly declared persistent dogfood or external beta.

### Privacy

- Processing remains on-device unless a later feature explicitly changes that contract.
- Raw SMS and account hints are hidden or masked in ordinary UI.
- Backup, encryption, retention, logging, export, and deletion policies must be decided before beta.

## 5. Target user experience

Provisional structure:

1. **Overview** — current-period spending, attention count, category preview, recent activity.
2. **Activity** — searchable and filterable transaction history.
3. **Review** — one focused uncertain item at a time with Save & next, Skip, and Undo.
4. **Insights** — category, merchant, and trend analysis when sufficient data exists.

Settings contains Sources, accounts, SMS/privacy state, import status, rules/defaults, and data controls.

AR-4 must independently compare a three-destination MVP against the four-destination proposal.

## 6. Target architecture rules

- `MainActivity` becomes a minimal host and is replaced rather than gradually hollowed out.
- One Android application module remains sufficient initially.
- Composables render immutable UI state and emit events.
- ViewModels coordinate screen state and use cases.
- Repositories own persistence/provider access.
- Use cases own workflows and financial policy.
- DAOs access only their own tables and contain no cross-table financial business rules.
- Android SMS access is isolated behind a data-source boundary.
- Domain models use typed enums/value objects.
- Room entities are not UI models.
- Time uses an injectable clock and explicit reporting timezone.
- Debug tooling is excluded from release UI.
- Each executable slice leaves the branch buildable.

## 7. Execution slices

| Slice | Goal | Status |
|---|---|---|
| 0 | Build, CI diagnostics, fixture/reconciliation harness | Complete |
| 1 | Typed financial semantics and review reasons | Started |
| 2 | Clean Room V1 foundation alongside prototype | Blocked pending AR-1 |
| 3 | One supported fixture parser vertical path | Planned |
| 4 | Minimal app shell, theme, and navigation | Planned |
| 5 | Activity and transaction correction | Planned |
| 6 | Focused Review queue | Planned |
| 7 | Rule preview and merchant defaults | Planned |
| 8 | Overview and reconciliation | Planned |
| 9 | Real Android SMS ingestion and onboarding | Blocked pending Issue #4/privacy decisions |
| 10 | Insights, settings, privacy controls, hardening | Planned |

Old code is removed only after replacement behavior is demonstrated, but prototype compatibility is not preserved once that replacement exists.

## 8. Completed work

### Program setup

- created `refactor/product-reset`;
- created draft PR #1;
- created AR-0 Issue #2;
- created CI blocker #3 and closed it with evidence;
- created distribution gate #4;
- created AR-1 Issue #5;
- established this living control document and supporting review documents.

### Baseline audit and cleanup

- inventoried repository behavior and dependencies;
- classified major code as retain concept, rewrite, or remove;
- identified `MainActivity.kt` as approximately 4,319 lines;
- removed committed `.idea` state;
- strengthened `.gitignore`;
- centralized KSP and Room versions;
- corrected Kotlin/KSP mismatch;
- removed duplicate Lifecycle dependency;
- changed `minSdk` from 36 to 23;
- configured Room schema output;
- moved adaptive icons to `mipmap-anydpi-v26`;
- declared telephony hardware optional for non-telephony device compatibility.

### CI and tests

- added Android CI with wrapper verification, unit tests, lint, and diagnostic artifacts;
- fixed missing wrapper executable mode in CI;
- replaced Android Studio template tests;
- added typed money, treatment, direction, spending effect, review reasons, and testable clock;
- added reconciliation, policy mapping, unresolved cash withdrawal, override, currency, overflow, validation, and clock tests;
- verified green CI at commit `4001176`.

## 9. Critique and risk log

| ID | Finding | Severity | Status / destination |
|---|---|---|---|
| C-001 | `MainActivity.kt` combines application, workflow, persistence, calculations, navigation, and nearly all UI. | Blocker | Open — Slices 2–6 |
| C-002 | Sensitive SMS/financial backup, retention, and storage controls are unresolved. | High | Open — Issue #4 / AR-2 |
| C-003 | `minSdk = 36` unnecessarily excluded older devices. | High | Resolved — API 23 |
| C-004 | SMS dedupe lacks a unique durable ingestion fingerprint. | High | Open — Slice 2 |
| C-005 | Unmanaged coroutine scope and nullable `!!` state can outlive UI state. | High | Open — Slice 4 |
| C-006 | Generic first-amount/keyword parser can select balances or misclassify events. | High | Open — Slice 3 / AR-3 |
| C-007 | Prototype tests did not validate product behavior. | Blocker | Partially resolved — Slice 0 tests added; broad coverage remains |
| C-008 | Home/Tools expose implementation operations instead of user goals. | High | Open — Slice 4 |
| C-009 | Transaction correction is a long technical form. | High | Open — Slice 5/6 |
| C-010 | Visual design is largely default Material styling. | Medium | Open — Slice 4 |
| C-011 | KSP targeted a different Kotlin compiler line. | Blocker | Resolved |
| C-012 | Current rule DAO can force matching rows to `EXPENSE` and `REVIEWED`. | Blocker | Open — Slice 2/7 |
| C-013 | Conditional navigation has incorrect back destinations. | High | Open — Slice 4 |
| C-014 | Package ID remains `com.example.ledgerlens`. | Medium | Open — before persistent dogfood/beta |
| C-015 | CI did not execute and initially failed before Gradle. | High | Resolved — Issue #3 closed |
| C-016 | Raw substring rules mutate history without preview, precedence, provenance, or undo. | High | Open — Slice 7 |
| C-017 | Parsed facts, overrides, effective values, spending effect, and category are conflated. | Blocker | Design proposed — Issue #5 |
| C-018 | IDE project state was committed. | Low | Resolved |
| C-019 | One sender may represent several accounts; flattened hints lose identity. | High | Design proposed — Issue #5 |
| C-020 | Message dedupe and duplicate real-world financial events are distinct. | High | Design proposed — Issue #5 / AR-3 |
| C-021 | Public distribution viability for restricted SMS access is unresolved. | Blocker | Open — Issue #4 |
| C-022 | Broad rewrite could leave the branch unusable for too long. | High | Resolved in plan — vertical slices required |
| C-023 | Adaptive icons were incorrectly available to pre-26 resource linking after lowering minSdk. | High | Resolved |
| C-024 | `READ_SMS` implied required telephony hardware in manifest. | High | Resolved by optional feature declaration; unsupported-device UX remains future work |

## 10. Open GitHub gates and reviews

| Issue | Purpose | Status |
|---|---|---|
| #2 | AR-0 reset assumptions | Open; independent review required |
| #3 | Enable and verify Android CI | Closed with successful run evidence |
| #4 | Distribution path and SMS permission viability | Open product gate |
| #5 | AR-1 domain model and database V1 | Open; independent financial/schema review required |

Assistant self-review supports but does not satisfy independent adversarial review.

## 11. Decision log

| Date | Decision | Status |
|---|---|---|
| 2026-07-17 | Treat current app as an unreleased prototype, not a compatibility baseline. | Accepted |
| 2026-07-17 | Use `refactor/product-reset` and draft PR #1 as the controlled workstream. | Accepted |
| 2026-07-17 | Maintain one Android module initially; avoid premature multi-module/DI complexity. | Accepted |
| 2026-07-17 | Use continuously runnable vertical slices rather than a long blank-slate rewrite. | Accepted |
| 2026-07-17 | Use API 23 as minimum because the current Room line requires it and no feature requires API 36 minimum. | Accepted |
| 2026-07-17 | Replace `MainActivity`; do not extract it in place indefinitely. | Accepted |
| 2026-07-17 | Establish clean Room database version 1 after AR-1 rather than migration version 6. | Accepted |
| 2026-07-17 | Preserve parsed facts separately from user overrides. | Proposed; awaiting AR-1 |
| 2026-07-17 | Use signed spending contribution rather than `excludedFromSpending`. | Implemented at domain-contract level; awaiting AR-1 |
| 2026-07-17 | Default cash withdrawal to unresolved rather than automatically counting it. | Proposed/implemented in Slice 0; awaiting AR-1 |
| 2026-07-17 | Keep permanent application ID unresolved until ownership naming is confirmed, but resolve before persistent distribution. | Accepted |

## 12. Immediate next work

1. Obtain genuinely independent findings on Issues #2 and #5.
2. Decide the initial distribution route and restricted SMS-permission path in Issue #4.
3. Continue Slice 1 only in pure domain/test code where it does not pre-empt AR-1 decisions.
4. Refine review-reason policy, signed spending examples, and rule precedence from review findings.
5. Begin production Room V1 entities only after accepted AR-1 blocker/high findings are resolved.
6. Keep every executable commit green in Android CI.

## 13. Update rule

After every meaningful work unit:

- update this document’s status, completed work, critique log, decisions, and next work;
- link the verified commit/CI evidence;
- create or update the relevant review/gate issue;
- do not close a phase merely because code exists;
- do not hide blocker/high findings in a backlog without explicit rationale.
