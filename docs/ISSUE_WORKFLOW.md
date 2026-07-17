# Reset Program Issue Workflow

Use GitHub issues for work that is independently reviewable, externally blocked, or large enough to need explicit acceptance criteria.

## Issue types

### Product gate

A decision that can invalidate or materially constrain the product, such as SMS distribution viability or raw-message retention.

### Engineering blocker

A condition preventing trusted implementation, such as CI not executing.

### Adversarial review

An independent checkpoint with a pinned commit and required finding format.

### Implementation slice

A runnable vertical slice with demo and test acceptance criteria. Create these as code implementation begins rather than pre-creating a large backlog that may become stale.

## Closure rule

An issue closes only when:

- evidence is linked;
- acceptance criteria are met;
- relevant control-document entries are updated;
- blocker/high findings are resolved or explicitly accepted with rationale;
- tests/validation are identified.
