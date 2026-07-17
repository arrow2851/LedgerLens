# Change Policy During Product Reset

## Allowed before Phase 0 closes

- documentation and review artifacts;
- safe build/dependency corrections;
- test-only fixture/domain code;
- CI configuration;
- removal of generated/IDE state.

## Requires reviewed domain/schema direction

- new Room entities/DAOs;
- parser rewrite;
- rule-engine implementation;
- financial calculation changes.

## Requires privacy/distribution gate

- real-device SMS permission/onboarding implementation intended for release;
- raw-message retention/encryption/deletion decisions;
- store-facing release assumptions.

## Requires runnable replacement

- removal of old production screens;
- deletion of prototype database code;
- replacement of current `MainActivity`.

Every executable change must identify which gate permits it.
