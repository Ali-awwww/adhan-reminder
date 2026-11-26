package com.adhanreminder.app.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.adhanreminder.app.ui.screens.SettingsScreen
import com.adhanreminder.app.ui.theme.AdhanReminderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsOurAppsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingOurAppsOpensExternalBrowserIntent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        composeRule.setContent {
            AdhanReminderTheme {
                SettingsScreen(
                    onBack = {},
                    onOpenDeviceCheck = {},
                    onClearData = {},
                )
            }
        }

        val button = composeRule.onNodeWithTag("settings_our_apps_button")
        button.performScrollTo()
        button.assertIsDisplayed()
        composeRule.onNodeWithText("تطبيقاتنا").assertIsDisplayed()
        button.performClick()

        val startedIntent = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull("Expected ACTION_VIEW intent to be launched", startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals("https://apps-showcase.pages.dev", startedIntent.dataString)
    }
}
