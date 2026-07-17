# Next Work Unit — Slice 0: Build and Fixture Harness

## Objective

Create a trusted execution loop before replacing production behavior.

## Planned code changes

1. centralize KSP and Room versions in `libs.versions.toml`;
2. add deterministic domain-only money/spending types;
3. add redacted fixture ledger test data;
4. replace template unit test with financial-semantics tests;
5. add a documented local verification command;
6. obtain CI execution and fix any build/lint failures;
7. avoid changing the current production UI or database behavior in this slice.

## Demo

A unit test independently proves that:

```text
$100 purchase
+ $3 fee
- $20 refund
+ $0 transfer
+ $0 credit-card payment
+ $0 salary
= $83 spending
```

Cash withdrawal and unknown events remain unresolved and contribute zero until reviewed.

## Acceptance criteria

- `testDebugUnitTest` passes;
- `lintDebug` passes or every remaining issue is recorded and intentionally scoped;
- fixture financial semantics match `docs/DOMAIN_AND_SCHEMA_V1.md`;
- production behavior has not been silently altered;
- control and critique documents are refreshed;
- implementation remains buildable.
