package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.core.identity.SukoonIdentity
import com.example.ui.components.SukoonTopBar
import com.example.ui.theme.SukoonTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches SUKOON AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SUKOON AI", appName)
    }

    @Test
    fun `permanent top bar renders SUKOON and Personal AI of Priyanksinh Raj`() {
        composeRule.setContent {
            SukoonTheme {
                SukoonTopBar(
                    currentSectionTitle = "Home / Chat",
                    isOnline = true,
                    onMenuClick = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("permanent_ai_name").assertIsDisplayed()
        composeRule.onNodeWithText(SukoonIdentity.AI_NAME).assertIsDisplayed()
        composeRule.onNodeWithText(SukoonIdentity.HEADER_SUBTITLE).assertIsDisplayed()
        composeRule.onNodeWithText("Online").assertIsDisplayed()
    }
}
