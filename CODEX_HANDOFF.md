# LedgerLens Codex Handoff

## Product North Star

LedgerLens is an Android-first personal finance tracker built in Kotlin, Jetpack Compose, and Room. It turns bank and credit card SMS alerts into a clean local spending dashboard with minimal manual work.

Current Android package/namespace:

```kotlin
com.example.ledgerlens
```

Do not rename the package unless explicitly asked.

LedgerLens is a personal/local Android app. It is not being optimized for Play Store release yet, but the app should still feel polished, trustworthy, and intentionally designed.

## Mockup Direction

Use the May 2026 mockups as the product target. The app should feel like a modern finance app with clear primary tabs:

```text
Spending
Review
Activity
More
```

The main experience should not expose an old MVP/debug shell. Legacy setup, parser, source, and correction tools should either be migrated into the new information architecture, clearly placed under `More`, or removed when replaced.

The product should look and behave closer to the mockups:

- `Spending`: monthly totals, category donut/list, top merchants, month controls, filters.
- `Review`: action center for merchants needing categories, transactions needing review, unassigned spending, and possible transfers.
- `Merchant Review`: select one or more merchants, choose a category, apply merchant-level defaults.
- `Activity`: searchable/filterable transaction feed grouped by date.
- `Transaction Detail`: locked transaction facts, editable merchant/category/accounting treatment/spending inclusion/review status, collapsed advanced/debug details.
- `Category Picker`: bottom sheet with search, common categories, all categories, create category action, apply/cancel actions.
- `More`: categories, merchants, sources/accounts, rules, SMS import/export history, exports, advanced tools, settings, help, privacy.

## Ideal Workflow

```text
1. User imports or refreshes SMS.
2. App detects sender-level SMS sources.
3. User confirms which senders are real financial sources.
4. App parses transactions from identified sources.
5. App detects merchants/payees.
6. App applies learned merchant/category rules.
7. App shows clean monthly spending summaries.
8. User intervenes only for new merchants, ambiguous transactions, exceptions, and corrections.
```

The app should not become manual bookkeeping where every transaction needs a one-off fix.

## Durable Data Rules

Primary ingestion is SMS-first. SMS imports must use a stable key:

```text
notificationKey = sms:<sms_id>
```

Top-level financial source identity must be the SMS sender/shortcode/phone number.

Correct:

```text
sender:22838
```

Incorrect:

```text
sender + institution + account/card hint
```

Institution names, account/card hints, and message patterns are substructure below the sender-level source.

Every detected sender-level source belongs to one of three states:

1. Uncategorized possible source
2. Identified source
3. Non-source

New incoming sources start as uncategorized possible sources. Only identified sources are eligible for transaction parsing.

## Spending Logic

Spending views include only actual spending:

```text
transactionType == EXPENSE
AND excludedFromSpending == false
```

Do not inflate spending totals with:

- credit card payments
- internal transfers
- balance alerts
- payment confirmations
- informational alerts
- income/deposits
- refunds
- manually excluded transactions
- non-source messages

Credit card purchases count as expenses. Credit card payments usually count as transfers/payments and are excluded from spending totals to avoid double counting.

Zelle/Venmo-like transactions are ambiguous. Default them to review unless the user creates a specific rule.

## Categorization Model

Prefer merchant-level categorization.

```text
Walmart -> Groceries
Shell -> Transportation
Netflix -> Subscriptions
Masjid donation -> Charity
```

Transaction-level corrections should handle exceptions, reimbursements, unusual purchases, parser mistakes, and excluded transactions.

Rule priority should move toward:

1. Transaction-specific override
2. User-created exception rule
3. Merchant default category
4. Source-specific parser/category suggestion
5. Generic fallback category
6. Needs review

## Architecture Direction

`MainActivity` should not own the product UI. Keep it focused on Android framework concerns:

- database initialization
- permission launcher
- SMS import trigger
- export/share trigger
- `setContent`

Compose should be organized by feature under `ui`, with shared visual primitives under `ui/components`.

Recommended direction:

```text
ui/
  LedgerLensApp.kt
  LedgerLensAppState.kt
  components/
  activity/
  review/
  spending/
  transactions/
  sources/
  more/
  rules/
  merchants/
domain/
  parser/
  source/
  summary/
  merchants/
  rules/
  export/
data/
  dao/
  entity/
```

Refactoring is encouraged when it makes the app easier to evolve toward the mockups. Do not preserve obsolete screen routes just because they compile.

## Important Entities And DAOs

Room entities:

- `RawAlertEntity`
- `FinancialSourceEntity`
- `TransactionEntity`
- `TransactionRuleEntity`

DAOs:

- `RawAlertDao`
- `FinancialSourceDao`
- `TransactionDao`
- `TransactionRuleDao`

When changing Room schema:

- update the entity
- bump the database version
- add a proper migration
- update DAOs/call sites
- do not use destructive migrations unless explicitly requested

## Current WIP Areas

### Parser

The parser is intentionally conservative. It may mark many transactions as `NEEDS_REVIEW`, `UNKNOWN`, missing merchant, missing category, or low confidence.

High-value parser work:

- source-specific parser rules by sender/institution
- message pattern grouping under sender-level sources
- better separation of purchases, refunds, transfers, deposits, card payments, and P2P transfers
- parser diagnostics explaining why an item needs review
- reprocessing raw alerts after parser improvements without reimporting SMS

### UI

The UI should continue moving away from the original MVP/debug layout. Prefer fewer top-level surfaces, clearer bottom navigation, bottom sheets for focused edits, and dense but calm finance-app screens.

Do not add more correction buttons to dense screens unless the task specifically asks for it. Move technical or destructive actions into `More > Advanced tools`.

## Testing Notes

Use Android Studio or:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

For schema changes, verify migrations and run a debug build.
