package com.robinying.paddlevision

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robinying.paddlevision.ui.ImageWorkspace
import com.robinying.paddlevision.ui.RESULT_OVERLAY_TEST_TAG
import com.robinying.paddlevision.ui.theme.VisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the overlay to the state it is supposed to follow.
 *
 * The geometry itself is covered by `OverlayGeometryTest` on the JVM. What can only be checked with
 * a real composition is the wiring: that the canvas appears once a result exists, disappears while a
 * run is in flight, and never renders for a state that has no analysed image behind it.
 *
 * The image URI is deliberately unreadable, so `ImagePreview` falls back to its failure placeholder.
 * That keeps the preview decode out of the assertion — the overlay must not depend on it.
 */
@RunWith(AndroidJUnit4::class)
class ResultOverlayInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theOverlayIsDrawnWhenAResultIsPresent() {
        composeRule.setContent {
            VisionTheme {
                ImageWorkspace(state = stateWithResult())
            }
        }

        composeRule.onNodeWithTag(RESULT_OVERLAY_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun theOverlayIsAbsentBeforeAnyImageIsSelected() {
        composeRule.setContent {
            VisionTheme {
                ImageWorkspace(state = VisionUiState(imageUri = null, result = sampleResult()))
            }
        }

        composeRule.onNodeWithTag(RESULT_OVERLAY_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun theOverlayIsAbsentWhileARunIsInFlight() {
        composeRule.setContent {
            VisionTheme {
                ImageWorkspace(state = stateWithResult(isRunning = true))
            }
        }

        composeRule.onNodeWithTag(RESULT_OVERLAY_TEST_TAG).assertDoesNotExist()
    }

    /**
     * The state carries no result until a run succeeds, so an overlay that keyed off the selected
     * task rather than the result would draw boxes over an image nobody has analysed yet.
     */
    @Test
    fun theOverlayIsAbsentWhenAStaleTaskChangeClearedTheResult() {
        var state by mutableStateOf(stateWithResult())
        composeRule.setContent {
            VisionTheme {
                ImageWorkspace(state = state)
            }
        }
        composeRule.onNodeWithTag(RESULT_OVERLAY_TEST_TAG).assertIsDisplayed()

        composeRule.runOnUiThread {
            state = VisionUiReducer.reduce(state, VisionIntent.SelectTask(VisionTask.FACE))
        }

        composeRule.onNodeWithTag(RESULT_OVERLAY_TEST_TAG).assertDoesNotExist()
    }

    private fun stateWithResult(isRunning: Boolean = false) = VisionUiState(
        selectedTask = VisionTask.OBJECT,
        imageUri = "content://unreadable-in-test",
        isRunning = isRunning,
        result = sampleResult(),
    )

    private fun sampleResult() = VisionInferenceResult(
        task = VisionTask.OBJECT,
        imageSize = ImageSize(3000, 2000),
        elapsedMillis = 120,
        detections = listOf(
            DetectedObject(PASCAL_VOC_DOG_CATEGORY_ID, 0.95f, PixelBox(110f, 185f, 350f, 545f)),
            DetectedObject(0, 0.61f, PixelBox(0f, 0f, 3000f, 2000f)),
        ),
    )
}
