package com.example.ledgerlens.ui

enum class AppScreen {
    SOURCES,
    TRANSACTIONS,
    SUMMARY,
    REVIEW_QUEUE,
    MERCHANTS,
    RULES,
    TOOLS
}

enum class LedgerNavDestination(
    val label: String,
    val icon: String,
    val screen: AppScreen
) {
    SPENDING("Spending", "S", AppScreen.SUMMARY),
    REVIEW("Review", "R", AppScreen.REVIEW_QUEUE),
    ACTIVITY("Activity", "A", AppScreen.TRANSACTIONS),
    MORE("More", "M", AppScreen.TOOLS)
}

fun navDestinationForScreen(screen: AppScreen): LedgerNavDestination {
    return when (screen) {
        AppScreen.SUMMARY -> LedgerNavDestination.SPENDING
        AppScreen.REVIEW_QUEUE -> LedgerNavDestination.REVIEW
        AppScreen.TRANSACTIONS -> LedgerNavDestination.ACTIVITY
        AppScreen.SOURCES,
        AppScreen.MERCHANTS,
        AppScreen.RULES,
        AppScreen.TOOLS -> LedgerNavDestination.MORE
    }
}

enum class ReviewQueueFilter {
    ALL_ISSUES,
    NEEDS_REVIEW,
    MISSING_MERCHANT,
    MISSING_CATEGORY,
    POSSIBLE_TRANSFERS,
    LOW_CONFIDENCE
}

enum class TransactionFilter {
    ALL,
    NEEDS_REVIEW,
    EXPENSES,
    TRANSFERS,
    CREDIT_CARD_PAYMENTS,
    EXCLUDED_FROM_SPENDING
}

enum class LedgerBackAction {
    DISMISS_SHEET,
    CLOSE_TRANSACTION_DETAIL,
    CLOSE_SOURCE_DETAIL,
    CLOSE_MERCHANT_DETAIL,
    GO_REVIEW,
    GO_SPENDING,
    EXIT_APP
}

fun resolveLedgerBackAction(
    showSheet: Boolean,
    hasSelectedTransaction: Boolean,
    hasSelectedSource: Boolean,
    hasSelectedMerchant: Boolean,
    activeScreen: AppScreen
): LedgerBackAction {
    return when {
        showSheet -> LedgerBackAction.DISMISS_SHEET
        hasSelectedTransaction -> LedgerBackAction.CLOSE_TRANSACTION_DETAIL
        hasSelectedSource -> LedgerBackAction.CLOSE_SOURCE_DETAIL
        hasSelectedMerchant -> LedgerBackAction.CLOSE_MERCHANT_DETAIL
        activeScreen == AppScreen.MERCHANTS -> LedgerBackAction.GO_REVIEW
        activeScreen != AppScreen.SUMMARY -> LedgerBackAction.GO_SPENDING
        else -> LedgerBackAction.EXIT_APP
    }
}
