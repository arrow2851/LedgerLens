package com.example.ledgerlens

import com.example.ledgerlens.ui.AppScreen
import com.example.ledgerlens.ui.LedgerBackAction
import com.example.ledgerlens.ui.LedgerNavDestination
import com.example.ledgerlens.ui.navDestinationForScreen
import com.example.ledgerlens.ui.resolveLedgerBackAction
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerNavigationTest {

    @Test
    fun bottomNavDestinationsMapToDailyWorkflowScreens() {
        assertEquals(
            listOf("Spending", "Review", "Activity", "More"),
            LedgerNavDestination.entries.map { it.label }
        )
        assertEquals(AppScreen.SUMMARY, LedgerNavDestination.SPENDING.screen)
        assertEquals(AppScreen.REVIEW_QUEUE, LedgerNavDestination.REVIEW.screen)
        assertEquals(AppScreen.TRANSACTIONS, LedgerNavDestination.ACTIVITY.screen)
        assertEquals(AppScreen.TOOLS, LedgerNavDestination.MORE.screen)
    }

    @Test
    fun childManagementScreensSelectMoreInBottomNav() {
        assertEquals(LedgerNavDestination.MORE, navDestinationForScreen(AppScreen.TOOLS))
        assertEquals(LedgerNavDestination.MORE, navDestinationForScreen(AppScreen.SOURCES))
        assertEquals(LedgerNavDestination.MORE, navDestinationForScreen(AppScreen.RULES))
        assertEquals(LedgerNavDestination.MORE, navDestinationForScreen(AppScreen.MERCHANTS))
    }

    @Test
    fun primaryScreensSelectExpectedBottomNavItems() {
        assertEquals(LedgerNavDestination.SPENDING, navDestinationForScreen(AppScreen.SUMMARY))
        assertEquals(LedgerNavDestination.REVIEW, navDestinationForScreen(AppScreen.REVIEW_QUEUE))
        assertEquals(LedgerNavDestination.ACTIVITY, navDestinationForScreen(AppScreen.TRANSACTIONS))
    }

    @Test
    fun backDismissesSheetBeforeNavigating() {
        assertEquals(
            LedgerBackAction.DISMISS_SHEET,
            resolveLedgerBackAction(
                showSheet = true,
                hasSelectedTransaction = true,
                hasSelectedSource = false,
                hasSelectedMerchant = false,
                activeScreen = AppScreen.TRANSACTIONS
            )
        )
    }

    @Test
    fun backFromDetailClosesDetail() {
        assertEquals(
            LedgerBackAction.CLOSE_TRANSACTION_DETAIL,
            resolveLedgerBackAction(
                showSheet = false,
                hasSelectedTransaction = true,
                hasSelectedSource = false,
                hasSelectedMerchant = false,
                activeScreen = AppScreen.REVIEW_QUEUE
            )
        )
    }

    @Test
    fun backFromRootActivityExitsApp() {
        assertEquals(
            LedgerBackAction.EXIT_APP,
            resolveLedgerBackAction(
                showSheet = false,
                hasSelectedTransaction = false,
                hasSelectedSource = false,
                hasSelectedMerchant = false,
                activeScreen = AppScreen.TRANSACTIONS
            )
        )
    }

    @Test
    fun backFromNestedMoreScreenReturnsMore() {
        assertEquals(
            LedgerBackAction.GO_MORE,
            resolveLedgerBackAction(
                showSheet = false,
                hasSelectedTransaction = false,
                hasSelectedSource = false,
                hasSelectedMerchant = false,
                activeScreen = AppScreen.SOURCES
            )
        )
    }

    @Test
    fun backFromMerchantReviewReturnsReview() {
        assertEquals(
            LedgerBackAction.GO_REVIEW,
            resolveLedgerBackAction(
                showSheet = false,
                hasSelectedTransaction = false,
                hasSelectedSource = false,
                hasSelectedMerchant = false,
                activeScreen = AppScreen.MERCHANTS
            )
        )
    }

    @Test
    fun backFromSpendingExitsApp() {
        assertEquals(
            LedgerBackAction.EXIT_APP,
            resolveLedgerBackAction(
                showSheet = false,
                hasSelectedTransaction = false,
                hasSelectedSource = false,
                hasSelectedMerchant = false,
                activeScreen = AppScreen.SUMMARY
            )
        )
    }
}
