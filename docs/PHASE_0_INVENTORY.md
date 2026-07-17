# Phase 0 Repository Inventory

_Last updated: 2026-07-17_

This inventory classifies the current LedgerLens prototype as **retain concept**, **rewrite**, or **remove**. “Retain concept” means the behavior or product idea remains valuable; it does not mean the current implementation should be copied unchanged.

## 1. Baseline summary

- **Application type:** single-module Android application using Kotlin, Jetpack Compose, and Room.
- **Default package/application ID:** `com.example.ledgerlens`.
- **Compile/target SDK:** 36.
- **Minimum SDK after baseline correction:** 23.
- **Kotlin:** 2.0.21.
- **KSP after baseline correction:** 2.0.21-1.0.28.
- **Room:** 2.8.4.
- **Gradle:** 8.13.
- **Android Gradle Plugin:** 8.12.3.
- **Main application file:** `MainActivity.kt`, approximately 4,319 lines.
- **Current automated tests:** Android template tests only; no product behavior coverage.
- **Remote build status:** CI workflow has been added, but no GitHub status/check has been emitted yet. A clean remote build remains unverified.

## 2. Global classification rules

### Retain concept

Retain the product behavior, user need, or business rule, but reimplement behind clean domain and repository boundaries.

### Rewrite

Replace the implementation rather than incrementally reorganizing it. Rewriting is justified where the existing unit combines responsibilities, depends directly on Room entities, exposes prototype operations, or embeds unsafe financial semantics.

### Remove

Delete behavior or files that exist only for prototype compatibility, IDE state, template scaffolding, or developer-facing workflow that should not exist in a release product.

## 3. Build and project configuration

| Path | Classification | Notes |
|---|---|---|
| `.gitignore` | Retain/update | Updated to ignore the complete `.idea/` directory and generated build state. Room schemas will be intentionally versioned once the clean v1 schema exists. |
| `.idea/**` | Remove | Local IDE and device state. Removed from the reset branch. |
| `.github/workflows/ci.yml` | Retain/update | New baseline CI. Later add formatter/static analysis, parser tests, database tests, UI tests, and pinned action SHAs. |
| `build.gradle.kts` | Retain/update | Minimal top-level plugin catalog usage is acceptable. Revisit after dependency architecture is finalized. |
| `settings.gradle.kts` | Retain/update | Standard project setup. Add repository/content restrictions only if needed. |
| `gradle.properties` | Retain/update | Review warning mode, configuration cache, parallelism, and Kotlin settings after the build is stable. |
| `gradle/libs.versions.toml` | Rewrite | Centralize all versions, including KSP and Room. Remove duplicate direct dependency versions. Add Navigation, Lifecycle ViewModel, testing, and possibly DI dependencies only when architecture requires them. |
| `gradle/wrapper/**` | Retain | Gradle 8.13 aligns with the current AGP baseline. Revalidate during release hardening. |
| `gradlew`, `gradlew.bat` | Retain | Standard wrapper launchers. |
| `app/build.gradle.kts` | Rewrite incrementally | Immediate tooling errors were corrected. Final version should use the version catalog, typed compiler options, Room schema configuration, test dependencies, release hardening, and build-type-specific debug tooling. |
| `app/proguard-rules.pro` | Replace before release | Current file is template text only. Define release rules after architecture and serialization choices are known. |

### Build findings

1. The original KSP plugin targeted Kotlin 2.1.21 while the project used Kotlin 2.0.21. This was a likely clean-build blocker and has been corrected.
2. Lifecycle Runtime was declared twice at different versions. The direct duplicate was removed.
3. `minSdk = 36` excluded every pre-Android-16 device despite no product requirement. The support floor is now 23, matching Room 2.8.x.
4. Room schema export was enabled in the database but no schema output location was configured. KSP schema output is now configured.
5. Navigation Compose, ViewModel Compose, lifecycle-aware collection, and structured screen-state dependencies are not yet present.
6. The package/application ID is still a template identifier. A permanent identifier must be decided before beta distribution.

## 4. Manifest, privacy, and Android resources

| Path | Classification | Notes |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | Rewrite | Keep launcher activity and temporary `READ_SMS`, but add a privacy-reviewed application configuration, final package identity, appropriate backup policy, and any required feature declarations. |
| `res/xml/backup_rules.xml` | Replace | Template rules do not protect SMS and financial data. |
| `res/xml/data_extraction_rules.xml` | Replace | Template rules do not define cloud backup or device-transfer behavior. |
| `res/values/themes.xml` | Rewrite | Current platform theme does not establish the final Material 3 product shell. |
| `ui/theme/Color.kt` | Rewrite | Template purple/pink palette does not match the LedgerLens product direction. |
| `ui/theme/Theme.kt` | Rewrite | Dynamic color should not silently override a financial product identity without an explicit decision. The theme is also not currently applied by the Activity. |
| `ui/theme/Type.kt` | Rewrite | Template typography only. Define LedgerLens typography and accessibility behavior. |
| launcher icons and colors | Replace later | Keep temporarily so builds remain runnable; replace in the design-system phase. |
| `strings.xml` | Rewrite/expand | Move all user-facing copy out of Kotlin. Add privacy, permission, empty, error, loading, undo, and accessibility strings. |

### Privacy findings

- Full SMS bodies and account hints are stored locally and shown directly in normal screens.
- Backup is enabled while sensitive database exclusions are undefined.
- Permission denial is only logged and has no user recovery flow.
- The product needs a distribution-policy decision because `READ_SMS` is a hard-restricted permission and Play distribution has narrow permitted use cases.
- Raw SMS should be collapsed, masked, or moved behind an explicit “view original message” action.

## 5. Data layer

### `AppDatabase.kt`

**Classification:** Rewrite.

Retain only the concept of local-first Room storage. Remove database versions 1–5 and establish a clean version 1 schema because no released user data exists.

Current problems:

- migrations preserve prototype history rather than a released contract;
- singleton construction is embedded in the database class;
- no explicit foreign keys;
- sensitive backup/storage policy is external and incomplete;
- schema concepts mix parsed facts and user decisions.

### Entities

| Entity | Classification | Retained concept | Required redesign |
|---|---|---|---|
| `RawAlertEntity` | Rewrite | Durable source-message record and deterministic deduplication. | Rename to an SMS/message-specific model; add unique provider identity, normalized sender identity, ingestion status, parse status, and clear retention policy. Do not encode sender inside a synthetic title. |
| `FinancialSourceEntity` | Rewrite | Sender-level source identity and user confirmation. | Replace `userConfirmed` + `ignored` booleans with typed source status; separate detected institutions/accounts from sender; avoid comma-joined account hints; store user label independently. |
| `TransactionEntity` | Rewrite | Parsed financial event linked to a source message. | Separate parsed values, user overrides, effective values, review state, and spending impact. Use typed enums/value objects and foreign keys. Avoid using one boolean to represent all spending semantics. |
| `TransactionRuleEntity` | Rewrite | Reusable user corrections. | Define rule scope, matcher type, priority, effect, provenance, active state, and conflict behavior. Do not store an arbitrary collection of nullable fields as an implicit patch object. |

### DAOs

| DAO | Classification | Notes |
|---|---|---|
| `RawAlertDao` | Rewrite | Replace per-record duplicate count checks with unique insert semantics and batch operations. Add paged/date-bounded queries rather than routinely loading all raw messages. |
| `FinancialSourceDao` | Rewrite | Remove `deleteLegacyNonSenderSources()`. Use typed status transitions and transactional updates. |
| `TransactionDao` | Rewrite | Split query responsibilities from correction/rule application. Replace raw `LIKE` bulk mutations with a domain rule engine and previewable transactions. |
| `TransactionRuleDao` | Rewrite | It currently updates the `transactions` table directly and can force transactions to `EXPENSE`/`REVIEWED`. A rule DAO must persist rules only; applying financial meaning belongs in a use case/domain service. |

### New data-boundary recommendation

```text
data/
├── local/
│   ├── LedgerLensDatabase.kt
│   ├── dao/
│   ├── entity/
│   └── converter/
├── sms/
│   ├── SmsDataSource.kt
│   └── AndroidSmsDataSource.kt
├── repository/
│   ├── MessageRepository.kt
│   ├── SourceRepository.kt
│   ├── TransactionRepository.kt
│   └── RuleRepository.kt
└── mapper/
```

## 6. Domain layer

### `SourceDetector.kt`

**Classification:** Retain concept, rewrite implementation.

Retain:

- sender/shortcode/phone number as the top-level source identity;
- institution and account hints as attributes beneath the sender;
- confidence and suggested source/account information;
- explicit user confirmation before parsing into financial totals.

Rewrite because:

- it accepts Room entities and produces a Room entity directly;
- sender identity is extracted from the display title `"SMS from ..."` rather than a dedicated field;
- institutions and account hints are flattened into display strings;
- confidence scores are hard-coded without reasons;
- one sender can represent several institutions/accounts and needs structured candidates.

### `SmsTransactionParser.kt`

**Classification:** Retain concept and fixtures, rewrite as a parser strategy system.

Retain:

- amount extraction as a parser concern;
- account hint extraction;
- institution inference;
- transaction-treatment suggestion;
- merchant/payee suggestion;
- confidence and review routing.

Rewrite because:

- it selects the first matching amount;
- it assumes USD;
- it uses broad keyword matching;
- it cannot distinguish transaction amount from balance, limit, minimum payment, or available credit;
- it merges treatment and spending inclusion;
- it produces Room entities directly;
- confidence is a fixed number rather than explainable evidence.

### Rule functions embedded in `MainActivity.kt`

- `normalizeRulePhrase()` — retain as a small domain normalization concept, not as a top-level UI utility.
- `applyRulesToTransaction()` — rewrite completely with deterministic precedence, typed effects, conflict handling, and tests.

### Review predicates embedded in `MainActivity.kt`

- missing merchant;
- missing category;
- low confidence;
- any review issue.

**Classification:** Retain concept, move to domain policy. The review reason should be a typed set with priority, explanation, and suggested resolution.

## 7. Application workflow

### SMS permission and import

**Classification:** Retain concept, rewrite.

Current workflow:

1. user manually selects backfill or refresh;
2. app requests permission;
3. Activity queries SMS directly;
4. broad heuristic decides whether a message looks financial;
5. Activity inserts one record at a time;
6. user separately runs source detection;
7. user separately runs transaction parsing.

Target workflow:

1. privacy explanation;
2. permission request and recovery;
3. one idempotent ingestion coordinator;
4. deterministic batch dedupe;
5. source detection;
6. user confirmation only where needed;
7. automatic parsing for identified sources;
8. rule application;
9. progress/result state;
10. retry only failed or incomplete stages.

### Destructive reset

**Classification:** Retain as a Settings data-control feature; remove from normal Tools UI. Require explicit confirmation and explain exactly what is deleted.

## 8. UI inventory from `MainActivity.kt`

`MainActivity.kt` should be replaced by a minimal Activity plus app/navigation shell. Its screen concepts are classified below.

| Current function/concept | Classification | Target destination |
|---|---|---|
| `LedgerLensSourceSetupApp` | Remove/rewrite | Replaced by `LedgerLensApp`, NavHost, ViewModels, and repositories. |
| `AppScreen` conditional navigation | Remove | Navigation Compose/type-safe routes. |
| `HomeScreen`, `HomeStatusCard`, `HomeNavCard` | Rewrite | Overview dashboard. |
| `ToolsScreen`, `ToolActionCard`, `SetupActionsCard` | Remove from release | Automatic workflow; debug-only diagnostics where necessary. |
| `SourceListScreen` | Retain concept, rewrite | Settings → Sources and onboarding confirmation. |
| `SourceDetailScreen` | Retain concept, rewrite | Source management with masked message samples and clear status. |
| `SourceActionCard` | Rewrite | Compact selection/confirmation controls. |
| `SmsMessageCard` | Rewrite | Collapsed, masked original-message preview. |
| `TransactionReviewScreen` | Retain concept, rewrite | Activity destination. |
| `TransactionCard` and duplicate transaction row variants | Rewrite into one component | Shared date-grouped transaction row. |
| `TransactionFilterCard` | Rewrite | Search plus chips/sheets; no large filter card. |
| `TransactionDetailScreen` | Retain concept, rewrite | User-relevant detail first; parser internals collapsed. |
| `TransactionCorrectionCard` | Rewrite | Focused edit sheet/form with effective spending impact clearly shown. |
| `MerchantCorrectionCard` | Retain concept, rewrite | Inline merchant edit and merchant default option. |
| `CategoryCorrectionCard` | Retain concept, rewrite | Structured category picker; no duplicated preset grids. |
| `SimilarTransactionsCorrectionCard` | Replace | Human-readable “remember this choice” flow with preview, scope, and undo. |
| `SpendingSummaryScreen` | Retain concept, rewrite | Overview + Insights. |
| `CategoryDrilldownScreen` | Retain concept, rewrite | Insights category detail. |
| `ReviewQueueScreen` | Strongly retain concept, rewrite | Dedicated Review destination optimized for Save & next. |
| review summary/filter cards | Rewrite | Compact queue progress and reason chips. |
| `MerchantReviewScreen` | Retain concept, reposition | Insights/merchant detail and Settings → learned defaults. |
| `MerchantDetailScreen` | Retain concept, rewrite | Merchant insight/default screen. |
| date/month helper functions | Retain concept, replace implementation | Use `java.time` with explicit timezone and testable clock. |

### UI duplication findings

- Several transaction card functions repeat merchant, amount, date, category, and review rendering.
- Category preset grids are duplicated in transaction and merchant screens.
- Screen top bars repeatedly implement a text “Back” button rather than standard navigation behavior.
- Nearly every screen is a `LazyColumn` of generic `Card` components with 12dp padding and full-width buttons.
- Internal fields such as source key, parser status, raw SMS, and confidence are shown too prominently.
- Business calculations run inside Composables over complete entity lists.

## 9. Tests

| Current test | Classification |
|---|---|
| `ExampleUnitTest.addition_isCorrect` | Remove |
| `ExampleInstrumentedTest.useAppContext` | Replace |

Required test families:

1. sender normalization and source identity;
2. source detection with multiple institutions/account hints;
3. parser fixtures by institution and message pattern;
4. multiple-amount, declined, reversal, refund, transfer, payment, and balance cases;
5. spending-impact and reconciliation tests;
6. rule precedence/conflict tests;
7. Room constraints and repository tests;
8. ingestion idempotency/interruption tests;
9. ViewModel state tests;
10. Compose navigation, review, accessibility, and destructive-action tests.

## 10. Retain/rewrite/remove totals

### Retain largely as infrastructure

- Gradle wrapper and basic single-module project shell;
- launcher resources temporarily;
- the local-first Room choice;
- the core product concepts of source confirmation, parsing, review, merchant defaults, categories, rules, and spending summaries.

### Rewrite

- application architecture;
- database schema and every entity/DAO;
- SMS ingestion;
- source detection implementation;
- parser implementation;
- rule engine;
- all user-facing screens;
- theme/design system;
- privacy and backup configuration;
- tests and build dependency management.

### Remove

- prototype migrations 1–5;
- legacy source cleanup behavior;
- manual Backfill/Detect/Parse release workflow;
- template tests;
- committed IDE state;
- duplicate UI components;
- internal parser/data fields as primary user interface;
- direct DAO calls from Composables;
- stringly typed financial state and implicit nullable rule patches.

## 11. Phase 0 decisions

1. **Minimum SDK:** API 23.
2. **Compile/target SDK:** remain 36 for now.
3. **Compatibility threshold:** start preserving database migrations when the first explicitly labeled external beta build is distributed and users are told their locally stored data will persist between upgrades.
4. **Current prototype data:** disposable during the reset.
5. **MainActivity:** replacement, not extraction-in-place.
6. **Database:** clean version 1 schema, not migration 6.
7. **CI:** required before Phase 1 can close; the current lack of a reported status remains open.
8. **Application ID:** unresolved; must be finalized before external beta.

## 12. Phase 0 exit gaps

- [x] Repository inventory complete.
- [x] Retain/rewrite/remove map complete.
- [x] Minimum SDK decided.
- [x] Beta compatibility threshold defined.
- [x] IDE state removed.
- [x] CI workflow added.
- [ ] Confirm a clean build and lint result from CI or a reproducible local environment.
- [ ] Complete AR-0 and resolve accepted blocker/high findings.
- [ ] Finalize the proposed clean domain model and database schema before Phase 1 implementation.
