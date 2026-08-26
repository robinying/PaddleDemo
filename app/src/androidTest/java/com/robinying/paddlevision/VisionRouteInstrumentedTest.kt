package com.robinying.paddlevision

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VisionRouteInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cancellingTheSystemPhotoPickerReturnsToTheImageSelectionState() {
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.pick_image_accessibility)).performClick()

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(
            "Expected the system Photo Picker to open",
            device.wait(Until.hasObject(By.pkg("com.google.android.providers.media.module")), 5_000),
        )
        device.pressBack()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeRule.onNodeWithContentDescription(
                    composeRule.activity.getString(
                        R.string.analysis_result_accessibility,
                        composeRule.activity.getString(R.string.message_no_image_selected),
                    ),
                ).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }
}
