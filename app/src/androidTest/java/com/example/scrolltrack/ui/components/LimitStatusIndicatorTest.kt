package com.example.scrolltrack.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.scrolltrack.ui.limit.LimitInfo
import org.junit.Rule
import org.junit.Test

class LimitStatusIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun limitStatusIndicator_noLimit_showsEmptyHourglass() {
        composeTestRule.setContent {
            LimitStatusIndicator(limitInfo = null)
        }

        composeTestRule.onNodeWithContentDescription("Time Limit Status").assertIsDisplayed()
        // We can't easily assert the drawable, but we can check for the absence of the time limit text
        composeTestRule.onNodeWithText("5m").assertDoesNotExist()
    }

    @Test
    fun limitStatusIndicator_limitWithTimeRemaining_showsStartHourglassAndTime() {
        val limitInfo = LimitInfo(timeLimitMillis = 300000, timeRemainingMillis = 120000)
        composeTestRule.setContent {
            LimitStatusIndicator(limitInfo = limitInfo)
        }

        composeTestRule.onNodeWithContentDescription("Time Limit Status").assertIsDisplayed()
        composeTestRule.onNodeWithText("5m").assertIsDisplayed()
    }

    @Test
    fun limitStatusIndicator_limitWithNoTimeRemaining_showsEndHourglassAndTime() {
        val limitInfo = LimitInfo(timeLimitMillis = 300000, timeRemainingMillis = 0)
        composeTestRule.setContent {
            LimitStatusIndicator(limitInfo = limitInfo)
        }

        composeTestRule.onNodeWithContentDescription("Time Limit Status").assertIsDisplayed()
        composeTestRule.onNodeWithText("5m").assertIsDisplayed()
    }
}