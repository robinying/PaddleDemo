package com.robinying.paddlevision

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionRouteInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cancellingTheSystemPhotoPickerReturnsToTheImageSelectionState() {
        composeRule.onNodeWithContentDescription(string(R.string.pick_image_accessibility)).performClick()

        assertTrue(
            "Expected the system Photo Picker to take over the foreground",
            waitForForegroundChange(foregroundExpected = false),
        )
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        assertTrue("Expected the app to come back after cancelling", waitForForegroundChange(foregroundExpected = true))

        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeRule.onNodeWithContentDescription(
                    string(R.string.analysis_result_accessibility, string(R.string.message_no_image_selected)),
                ).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    /**
     * The OCR selector must not offer a language whose model is not bundled, otherwise the user
     * can walk into a run that always fails without ever learning why.
     */
    @Test
    fun theLanguageSelectorOnlyOffersPackagedOcrLanguages() {
        composeRule.onAllNodesWithText(string(R.string.language_english)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.language_french)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.language_spanish)).assertCountEquals(0)

        composeRule
            .onNodeWithText(string(R.string.selected_language, string(R.string.language_chinese)))
            .assertExists()
        composeRule.onNodeWithText(string(R.string.ocr_language_note)).assertExists()
    }

    @Test
    fun recreatingTheActivityKeepsTheSelectedCapability() {
        composeRule.onNodeWithContentDescription(
            string(R.string.select_task, string(R.string.task_face)),
        ).performClick()
        composeRule.onNodeWithText(string(R.string.task_face_description)).assertExists()

        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeRule.onNodeWithText(string(R.string.task_face_description)).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun string(resourceId: Int): String = composeRule.activity.getString(resourceId)

    private fun string(resourceId: Int, vararg args: Any): String = composeRule.activity.getString(resourceId, *args)

    /** The activity is RESUMED only while it owns the foreground, whatever picker the device ships. */
    private fun isActivityResumed(): Boolean {
        var resumed = false
        composeRule.runOnUiThread {
            resumed = composeRule.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        return resumed
    }

    private fun waitForForegroundChange(foregroundExpected: Boolean, timeoutMillis: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (isActivityResumed() == foregroundExpected) return true
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
