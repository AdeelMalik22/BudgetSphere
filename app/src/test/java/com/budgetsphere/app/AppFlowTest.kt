package com.budgetsphere.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = BudgetSphereApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) {
        compose.waitUntil(30000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun signupCreateProfileAddExpenseAndPrivacyNavigation() {
        compose.onNodeWithText("New here? Create local account").performScrollTo().performClick()
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.onNodeWithText("Name must be 1–100 characters").assertExists()
        compose.onNodeWithText("Full name").performTextInput("Test Owner")
        compose.onNodeWithText("Username or email").performTextInput("ui-owner")
        compose.onNodeWithText("Password").performTextInput("a secure local password")
        compose.onNodeWithText("Confirm password").performTextInput("a secure local password")
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        waitFor("Your workspaces")
        compose.onNodeWithText("Create profile").performScrollTo().performClick()
        compose.onNodeWithText("Profile name").performTextInput("Personal")
        compose.onNodeWithText("Save").performClick()
        waitFor("Your money, in focus")
        compose.onNodeWithText("PKR123.45", substring = true).assertDoesNotExist()
        compose
            .onNodeWithContentDescription("Show financial information — authentication required")
            .performClick()
        compose.onNodeWithText("Account password").performTextInput("incorrect password")
        compose.onNodeWithText("Verify password").performClick()
        compose.waitUntil(30000) {
            compose
                .onAllNodes(hasText("Verify password") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose
            .onNodeWithContentDescription("Show financial information — authentication required")
            .assertExists()
        compose.onNodeWithText("Account password").assertExists()
        compose.onNodeWithText("Account password").performTextInput("a secure local password")
        compose.onNodeWithText("Verify password").performClick()
        compose.waitUntil(30000) {
            compose
                .onAllNodesWithContentDescription("Hide all financial information")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Expense"))
        compose.onNodeWithText("Expense").performClick()
        waitFor("New transaction")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Amount (PKR)"))
        compose.onNodeWithText("Amount (PKR)").performTextInput("123.45")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Expense title"))
        compose.onNodeWithText("Expense title").performTextInput("Lunch")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Choose Category"))
        compose.onNodeWithText("Choose Category").performClick()
        compose.onNodeWithText("Food & Dining").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Save transaction"))
        compose.onNodeWithText("Save transaction").performClick()
        waitFor("Details")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Edit"))
        compose.onNodeWithText("Edit").performClick()
        waitFor("Edit transaction")
        compose.onNodeWithText("Expense title").performTextReplacement("Lunch updated")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Amount (PKR)"))
        compose.onNodeWithText("Amount (PKR)").performTextReplacement("150.50")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Save transaction"))
        compose.onNodeWithText("Save transaction").performClick()
        waitFor("Details")
        compose.onNodeWithText("Budgets").performClick()
        compose.onNodeWithText("Create budget").performClick()
        compose.onNodeWithText("Amount (PKR)").performTextInput("1000")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(30000) {
            compose
                .onAllNodesWithText("1,000.00", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Hide all financial information").performClick()
        compose.onNodeWithText("1,000.00", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("••••••").assertCountEquals(3)
        compose.onNodeWithText("Create budget").assertIsNotEnabled()
        compose.onNodeWithText("Analytics").performClick()
        waitFor("Income vs expenses")
        compose.onNodeWithText("150.50", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Transactions").performClick()
        waitFor("Transactions")
        compose.onNodeWithText("Lunch updated").assertDoesNotExist()
        compose.onNodeWithText("150.50", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Switch profile").performClick()
        compose.onNodeWithText("Manage profiles").performClick()
        waitFor("Your workspaces")
        compose.onNodeWithText("Create profile").performScrollTo().performClick()
        compose.onNodeWithText("Profile name").performTextInput("Friend")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(30000) {
            compose.onAllNodesWithText("Profile name").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Back to workspace").performClick()
        waitFor("Your money, in focus")
        compose.onNodeWithText("Lunch updated").assertDoesNotExist()
        compose.onNodeWithText("Transactions").performClick()
        waitFor("Transactions")
        compose.onNodeWithText("Lunch updated").assertDoesNotExist()
        compose.onNodeWithContentDescription("Switch profile").performClick()
        compose.onNodeWithText("Personal").performClick()
        waitFor("Your money, in focus")
        compose
            .onNodeWithContentDescription("Show financial information — authentication required")
            .assertExists()
        compose.onNodeWithText("Transactions").performClick()
        waitFor("Transactions")
        compose.onNodeWithText("Lunch updated").assertDoesNotExist()
        compose.onNodeWithText("150.50", substring = true).assertDoesNotExist()
    }
}
