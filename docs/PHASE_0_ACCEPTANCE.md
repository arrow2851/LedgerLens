# Phase 0 Acceptance Checklist

Phase 0 closes only when every required item below is satisfied.

## Completed

- [x] Controlled reset branch exists.
- [x] Draft PR exists.
- [x] Living control document exists.
- [x] Repository inventory and retain/rewrite/remove map are complete.
- [x] Prototype compatibility policy is documented.
- [x] Minimum Android API is decided as 23.
- [x] Beta migration threshold is documented.
- [x] IDE state is removed.
- [x] KSP/Kotlin mismatch and duplicate Lifecycle dependency are corrected.
- [x] CI workflow file exists.
- [x] AR-0 issue exists.
- [x] Internal AR-0 self-critique exists.
- [x] Clean domain/schema V1 proposal exists.
- [x] Continuously runnable implementation-slice plan exists.
- [x] SMS distribution/permission gate is documented.

## Required before closure

- [ ] GitHub Actions or a reproducible clean environment confirms build and lint on a named commit.
- [ ] AR-0 receives genuinely independent review.
- [ ] All accepted AR-0 blocker/high findings are resolved or incorporated into the plan.
- [ ] Distribution channel and restricted SMS-permission path have a named initial decision, even if fixture development continues while final approval remains pending.
- [ ] Domain/schema proposal has an AR-1 review issue with a pinned commit.
- [ ] Financial-semantics examples are independently reconciled.
- [ ] Control document is updated with final Phase 0 decisions and next implementation slice.

## Non-requirements

Phase 0 does not require:

- new production database entities;
- replacement UI screens;
- real-device SMS import;
- external beta package identity;
- final encryption implementation.

Those belong to later slices, but their decision gates must be visible.
