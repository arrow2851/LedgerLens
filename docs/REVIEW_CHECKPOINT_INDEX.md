# LedgerLens Adversarial Review Checkpoint Index

_Last updated: 2026-07-17_

| Checkpoint | Scope | Required reviewer mindset | Status |
|---|---|---|---|
| AR-0 | Reset assumptions and sequencing | Challenge rewrite scope, hidden requirements, privacy/distribution gaps, and discarded behavior. | Open — Issue #2 |
| AR-1 | Domain model, schema, and financial semantics | Independently reconcile totals; challenge ownership, rules, duplicates, refunds, transfers, and account/source identity. | Ready to open against `docs/DOMAIN_AND_SCHEMA_V1.md` |
| AR-2 | Ingestion privacy and failure modes | Assume hostile/malformed input, permission changes, interrupted jobs, restore, leakage, and deletion failures. | Future |
| AR-3 | Parser and rule red team | Construct misleading messages, multiple amounts, negations, reversals, and conflicting rules. | Future |
| AR-4 | Navigation, design system, accessibility | Test destination comprehension, back behavior, large text, TalkBack, contrast, and state restoration. | Future |
| AR-5 | Onboarding/first run | Deny/grant/revoke permission; no supported data; ambiguous sources; partial setup. | Future |
| AR-6 | Activity and transaction comprehension | Challenge treatment, spending effect, corrections, account attribution, and technical-detail disclosure. | Future |
| AR-7 | Review and bulk-action safety | Search for misleading defaults, accidental actions, rule overreach, and weak undo. | Future |
| AR-8 | Insights reconciliation | Independently compute totals and challenge exclusions, negatives, period boundaries, and duplicate events. | Future |
| AR-9 | Settings/privacy/destructive actions | Test source removal, rule deletion, raw-data deletion, reset, export, and permission revocation. | Future |
| AR-10 | Release red team | Attempt to break the complete product across correctness, privacy, recovery, performance, and accessibility. | Future |

## Independence rule

Assistant-authored self-critiques help prepare a checkpoint but do not satisfy independence. A checkpoint requires a reviewer/subtask that is instructed to challenge the current implementation rather than continue it.

## Finding format

Every finding must include:

- evidence/reproduction;
- severity (`blocker`, `high`, `medium`, `low`);
- user/engineering impact;
- recommended correction;
- regression test or validation.
