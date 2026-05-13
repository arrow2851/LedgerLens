# LedgerLens Codex Handoff

## Project Overview

LedgerLens is an Android-first personal finance tracker built in Kotlin, Jetpack Compose, and Room.

The current Android package name is:

```kotlin
com.example.ledgerlens
````

This package name is okay for now. Do not rename it unless explicitly asked.

The app is currently being built as a personal/local Android app. It is not being optimized for Play Store publication yet, however that is not an excuse for lack of polish.


## Final Product Vision / North Star

The final goal is not just to parse SMS messages. The final goal is to build a clean personal finance tracker that automatically turns bank and credit card SMS alerts into a useful spending dashboard with minimal manual work.

The ideal end-user workflow should be:

```text
1. User imports or refreshes SMS.
2. App detects sender-level SMS sources.
3. User confirms which senders are real financial sources.
4. App parses transactions from identified sources.
5. App detects merchants/payees.
6. App applies learned merchant/category rules.
7. App shows clean monthly spending summaries.
8. User should only need to intervene for new merchants, ambiguous transactions, exceptions, and corrections.
````

The app should eventually feel more like:

```text
Mint / Copilot-style personal finance categorization
but based on SMS alerts
and controlled locally by the user
```

It should not feel like a manual bookkeeping app where every transaction must be fixed one by one.

## Final Product UX Goal

The final UI should be organized around a few clear workflows:

### 1. Dashboard / Spending Summary

The main view should answer:

* How much did I spend this month?
* Where did my money go?
* Which categories increased?
* Which merchants did I spend the most at?
* What needs my attention?

### 2. Merchant Review

The preferred categorization workflow should be merchant-level.

Example:

```text
Walmart → Groceries
Shell → Gas / Fuel
Netflix → Subscriptions / Streaming
Masjid donation → Charity / Donation
```

The user should not need to open individual Walmart transactions just to decide Walmart is usually groceries.

### 3. Transaction Review Queue

Transaction-level review should be used for:

* parser mistakes
* missing merchants
* missing categories
* ambiguous transfers
* Zelle/Venmo-like transactions
* one-off exceptions
* reimbursements
* transactions that should be excluded from normal spending

### 4. Source Setup

Source setup should be a separate workflow.

The user should be able to review SMS senders and classify them as:

* identified source
* non-source
* uncategorized possible source

### 5. Tools / Maintenance

Technical actions should live in Tools, not clutter main screens.

Examples:

* Backfill SMS
* Refresh SMS
* Detect Sources
* Parse Identified Sources
* Reprocess Transactions
* Clear Test Data
* Export Data later

## Desired End-State Automation

The app should gradually learn from user corrections.

If the user says:

```text
Walmart → Groceries
```

then future Walmart transactions should automatically become groceries.

If the user overrides one Walmart transaction as:

```text
Reimbursement
```

that one transaction-specific override should win over the merchant default.

Final rule priority should be:

```text
1. Transaction-specific override
2. User-created exception rule
3. Merchant default category
4. Source-specific parser/category suggestion
5. Generic fallback category
6. Needs review
```

## Manual Corrections Philosophy

Manual correction tools should exist, but they should not be the main workflow.

Correction tools are fallback tools for:

* parser debugging
* one-off fixes
* merchant/payee cleanup
* exception handling
* applying fixes to similar historical transactions

The final app should minimize user interference after the initial learning/setup phase.

## Important Final Product Distinction

There are different financial views that should not be mixed:

### Spending Summary

Shows actual expenses only.

Should generally exclude:

* credit card payments
* internal transfers
* balance alerts
* informational alerts
* income/deposits
* refunds
* manually excluded transactions

### All Transactions

Shows every parsed transaction, including transfers, income, refunds, payments, and expenses.

### Cashflow View

May later show money moving in/out of checking accounts, including credit card payments.

### Account Movement View

May later show internal movement between accounts.

The MVP is focused on Spending Summary first.


## Recommended File Setup For Codex

Keep this `CODEX_HANDOFF.md` as the detailed product/context document.

Also create an `AGENTS.md` file in the repository root with shorter always-follow instructions for Codex.

`AGENTS.md` should contain durable coding rules, build/test commands, and key project constraints. `CODEX_HANDOFF.md` can contain the longer product vision and evolving roadmap.



## Current MVP Goal

The app imports SMS history from the Android SMS provider, detects sender-level financial sources, lets the user classify those sources, parses transactions from identified sources, and provides screens for:

* source setup
* transaction review
* merchant review
* monthly spending summary
* category drilldown
* transaction detail inspection
* debug/tools actions

The current app is still MVP/WIP. Some UI is temporary and used for testing the data flow.

## Primary Product Direction

Primary ingestion is SMS-first, not notification-listener-first.

The app should read historical and new SMS messages using user-granted SMS permission, then import financial-looking SMS messages into a local Room database.

SMS import should use a stable key:

```text
notificationKey = sms:<sms_id>
```

This prevents duplicate imports when the user refreshes SMS later.

## Important Source Detection Rule

Top-level financial source identity must be the SMS sender/shortcode/phone number.

Correct top-level source:

```text
sender:22838
```

Incorrect top-level source:

```text
sender + institution + account/card hint
```

Institution, account/card hints, and message patterns should be treated as substructure underneath the sender-level source.

Example:

```text
Top-level source:
22838

Under that source:
- institution hints: Chase
- account/card hints: 1234, 9876
- possible message patterns: purchases, payments, balance alerts, fraud alerts
```

Do not make account/card hint the top-level source.

## Source Categories

Every detected sender-level source should fall into one of three categories:

1. Uncategorized possible source
2. Identified source
3. Non-source

New incoming sources should start as uncategorized possible sources.

The user should be able to move a source between categories later if it was misclassified.

Source classification behavior:

```text
Uncategorized possible source:
- detected sender that might be financial
- should not be parsed into transactions yet

Identified source:
- user confirmed it is a valid financial SMS source
- should be eligible for transaction parsing

Non-source:
- user dismissed it as not useful
- should not be parsed into transactions
```

## Parsing Rule

Only parse transactions from identified sources.

Do not parse:

* uncategorized possible sources
* non-sources

The flow should be:

```text
Import SMS
→ Detect sender-level sources
→ User marks sender as identified source
→ Parse transactions only from identified sources
```

## Current Expected Screens

The app is moving toward this screen structure:

```text
Home
├── Spending Summary
├── Review Queue
├── Merchant Review
├── Transactions
├── Sources
└── Tools
```

Expected screens/features:

### Home

Clean navigation hub. Should show basic counts:

* imported SMS alerts
* detected sources
* parsed transactions

### Sources

Shows sender-level sources grouped into:

* Uncategorized Possible Sources
* Identified Sources
* Non-Sources

Tapping a source opens Source Detail.

### Source Detail

Shows:

* source summary
* sender/shortcode
* suggested type
* confirmed type
* matching SMS messages from that sender
* actions to mark as credit card, checking, savings, debit card, unknown, non-source, or uncategorized

### Tools

Should contain noisy setup/debug actions:

* Backfill SMS History
* Refresh Latest SMS
* Detect Sources
* Parse Identified Sources
* Clear All Test Data

These actions should not clutter the main Source screen.

### Transactions

Shows all parsed transactions with temporary filters.

Current filters are WIP/debug-level only:

* All
* Needs Review
* Expenses
* Transfers
* Credit Card Payments
* Excluded from Spending

### Transaction Detail

Shows parsed fields next to the original SMS.

It currently supports quick corrections such as:

* transaction type
* review status
* included/excluded from spending
* merchant/payee
* category
* apply merchant/category/classification to similar transactions

This screen is currently a testing/debug correction screen and should not be treated as the final UX.

### Review Queue

Shows transactions that need attention:

* Needs Review
* Missing Merchant
* Missing Category
* Low Confidence

This is meant to help fix bad parser output without scrolling all transactions.

### Spending Summary

Monthly view of included expense transactions only.

Should support:

* selected month
* total included spending
* spend by category
* category drilldown
* transaction drilldown

### Category Drilldown

From Spending Summary:

```text
Monthly Summary
→ Category
→ Transactions in that category
→ Transaction Detail
```

### Merchant Review

Merchant-level categorization workflow.

Example:

```text
Walmart → Groceries
Shell → Gas / Fuel
Netflix → Subscriptions / Streaming
Masjid donation → Charity / Donation
```

The user should not need to inspect each Walmart transaction just to decide Walmart is usually groceries.

Transaction Detail should remain available for exceptions, unusual transactions, reimbursements, and parser debugging.

## Spending Summary Inclusion Logic

Spending Summary should not include every parsed transaction.

It should include only transactions that represent actual spending.

Current inclusion rule:

```text
transactionType == EXPENSE
AND excludedFromSpending == false
```

Should generally not count as spending:

* credit card payments
* internal transfers
* balance alerts
* payment confirmations
* informational alerts
* income/deposits
* refunds
* transactions explicitly excluded from spending
* non-source messages

There may later be separate views for:

* all transactions
* cashflow
* account movement
* spending summary

## Credit Card Payments

Credit card payments from checking to a credit card should generally not count as expenses in the main spending summary.

Reason:

```text
The actual spending happened when the credit card purchase occurred.
The later checking payment is just paying down the card balance.
Counting both would double count spending.
```

Default behavior:

```text
Credit card purchases = included as expenses
Credit card payments = transfer / credit card payment
Credit card payments = excluded from spending totals
```

## Zelle / Transfer-Like Transactions

Zelle should be treated carefully.

Do not blindly auto-categorize all Zelle transactions.

Zelle can mean:

* reimbursement
* family transfer
* rent split
* repayment
* gift
* income-like receipt
* personal transfer

Default behavior should be conservative:

```text
Detect Zelle
Mark Needs Review
Do not auto-categorize unless user creates a specific rule
```

## Merchant Categorization Philosophy

The ideal end product should minimize manual transaction-by-transaction correction.

The preferred workflow is:

```text
Merchant Review:
Walmart → Groceries

Transaction Exception:
Specific Walmart purchase → Reimbursement
```

Merchant-level category assignment should usually handle normal cases.

Transaction-level overrides should handle:

* one-off exceptions
* reimbursements
* special purchases
* parser mistakes
* unusual treatment
* excluded transactions

## Rule Priority

Future rule hierarchy should be:

1. Transaction-specific override
2. User-created exception rule for phrase/source/message pattern
3. Merchant default category
4. Source-specific parser/category suggestion
5. Generic fallback category
6. Needs review

This matters because a merchant default should not override a specific transaction exception.

Example:

```text
Default merchant rule:
Walmart → Groceries

One-off transaction override:
Specific Walmart purchase for someone else → Reimbursement
```

## Current Saved Rules / Similar Transaction Tooling

The app may currently have WIP functionality for:

```text
Apply merchant to similar transactions
Apply category to similar transactions
Apply classification to similar transactions
```

These are useful testing tools but may feel awkward right now.

Treat them as WIP.

Do not assume this is final UX.

Later, this should become a cleaner rules system, preferably surfaced through:

* Merchant Review
* Review Queue
* Transaction Detail for exceptions
* Settings or Rules screen

## Current Known WIP Areas

### Parser

The SMS transaction parser is intentionally rough and conservative.

Many transactions may currently be marked:

```text
NEEDS_REVIEW
UNKNOWN
missing merchant
missing category
low confidence
```

This is expected.

Parser TODOs:

1. Improve merchant/payee extraction from real bank and credit card SMS formats.
2. Add source-specific parser rules by sender and institution.
3. Add message-pattern grouping under each sender.
4. Distinguish real transaction alerts from:

   * balance alerts
   * payment confirmations
   * statement reminders
   * fraud alerts
   * informational messages
5. Improve transaction type detection:

   * expenses
   * refunds
   * transfers
   * deposits
   * credit card payments
   * Zelle/Venmo-like transactions
6. Add parser diagnostics showing why a message became Needs Review.
7. Add sample-driven parser tuning using raw SMS from selected identified sources.
8. Support reprocessing raw alerts after parser improvements without reimporting SMS.

Desired future parser flow:

```text
identified sender-level source
→ source-specific message pattern detection
→ amount extraction
→ transaction type classification
→ merchant/payee extraction
→ review status assignment
→ merchant/category rule application
```

### UI

The UI is currently in transition from MVP/debug tooling to cleaner app structure.

Current goal is to reduce clutter by separating:

* Home
* Source setup
* Merchant review
* Transaction review queue
* Transactions
* Spending summary/dashboard
* Tools/settings

Avoid adding more correction buttons to already cluttered screens unless asked.

Prefer moving debug/setup functionality into Tools.

## Data Model

Room entities currently include or are expected to include:

* RawAlertEntity
* FinancialSourceEntity
* TransactionEntity
* TransactionRuleEntity

DAOs currently include or are expected to include:

* RawAlertDao
* FinancialSourceDao
* TransactionDao
* TransactionRuleDao

If changing Room schema, add proper migrations.

Do not use destructive migrations unless explicitly asked.

## Current Important Entity Concepts

### RawAlertEntity

Represents imported SMS/raw alert.

Important fields likely include:

* id
* notificationKey
* sourcePackage
* title
* text
* combinedText
* postTimeEpochMs
* capturedAtEpochMs
* processingStatus

For SMS imports:

```text
sourcePackage = sms
notificationKey = sms:<sms_id>
title = SMS from <sender>
```

### FinancialSourceEntity

Represents sender-level SMS source.

Important fields likely include:

* sourceKey
* sourceAddress
* institutionName
* accountHint
* suggestedAccountType
* confirmedAccountType
* displayName
* detectionConfidence
* userConfirmed
* ignored
* messageCount
* firstSeenEpochMs
* lastSeenEpochMs
* sampleMessage

Important sourceKey format:

```text
sender:<sms_sender>
```

### TransactionEntity

Represents parsed transaction.

Important fields likely include:

* id
* rawAlertId
* sourceKey
* transactionType
* amountCents
* currency
* merchantRaw
* displayMerchantName
* categoryName
* sourceInstitution
* accountHint
* occurredAtEpochMs
* receivedAtEpochMs
* parseConfidence
* reviewStatus
* excludedFromSpending
* parserNotes

### TransactionRuleEntity

Represents WIP saved rules from “apply to similar.”

This is not final UX yet.

Important fields likely include:

* sourceKey
* matchPhrase
* normalizedMatchPhrase
* merchantName
* categoryName
* transactionType
* reviewStatus
* excludedFromSpending
* active

## Current Development Preference

Make small, targeted changes.

Do not do large refactors unless explicitly asked.

Preserve working behavior.

Do not rename package `com.example.ledgerlens`.

Do not remove working screens unless asked.

When changing Room schema:

* update entity
* update database version
* add migration
* update DAO if needed

After each task, summarize:

1. Files changed
2. Behavior changed
3. How to test in Android Studio

## Things To Avoid

Do not:

* rename the app package
* switch away from SMS-first ingestion
* make account/card hint the top-level source
* parse uncategorized/non-source senders
* count credit card payments as spending by default
* assume all parsed transactions should show in Spending Summary
* overbuild final UI until workflows are validated
* delete WIP correction tools unless asked
* use destructive migrations without permission

## Current Next Likely Work

Likely next work should focus on one of:

1. Cleaning up navigation/screen structure
2. Fixing compile issues caused by recent rapid changes
3. Improving Merchant Review workflow
4. Making merchant-level category rules persistent for future parsed transactions
5. Improving parser diagnostics
6. Adding reprocess transactions/raw alerts after parser improvements
7. Moving debug tools into Tools if not already done
8. Reducing clutter in Transaction Detail

Before making changes, inspect the current project files because recent code was added manually and may contain duplicated functions, mismatched signatures, or unused old UI.
