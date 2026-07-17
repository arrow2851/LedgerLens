# LedgerLens Reset Quality Gates

## Build gate

- clean Gradle configuration;
- unit tests and lint execute in CI;
- branch remains buildable.

## Financial gate

- signed spending contribution is independently reconciled;
- refunds, reversals, transfers, card payments, income, cash withdrawal, and unknown cases are tested;
- parser facts and user overrides remain separate.

## Data-integrity gate

- message ingestion is idempotent;
- user overrides survive reparse;
- rule conflicts are deterministic;
- full reset and deletion are transactional.

## Privacy gate

- distribution channel decided;
- restricted permission path documented;
- backup/retention/encryption/logging controls reviewed;
- raw SMS hidden from ordinary UI.

## UX gate

- navigation follows user goals;
- Review is fast and reversible;
- technical details are progressive disclosure;
- empty/loading/error/permission states exist;
- accessibility review passes.

## Release gate

- no unresolved blocker/high review findings;
- policy/declaration materials prepared;
- performance and recovery tested;
- known limitations documented.
