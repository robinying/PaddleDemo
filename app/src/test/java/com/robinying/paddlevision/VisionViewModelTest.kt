package com.robinying.paddlevision

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun pickImageIntentEmitsPhotoPickerEffect() = runTest {
        val viewModel = VisionViewModel(FakeVisionInferenceUseCase())

        viewModel.onIntent(VisionIntent.PickImageClicked)

        assertEquals(VisionEffect.OpenPhotoPicker, viewModel.effects.first())
    }

    @Test
    fun runIntentPublishesInferenceSummary() = runTest {
        val viewModel = VisionViewModel(
            FakeVisionInferenceUseCase(
                result = VisionInferenceResult(
                    task = VisionTask.OBJECT,
                    imageSize = ImageSize(100, 100),
                    elapsedMillis = 8,
                    detections = listOf(DetectedObject("狗", 0.9f, PixelBox(0f, 0f, 10f, 10f))),
                ),
            ),
        )
        viewModel.onIntent(VisionIntent.ImageSelected("content://picked"))

        viewModel.onIntent(VisionIntent.RunRequested)

        assertEquals(UiText(R.string.result_object_summary, listOf<Any>(1, 8L)), viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun repeatedRunIntentIsIgnoredWhileInferenceIsRunning() = runTest {
        val inferenceUseCase = FakeVisionInferenceUseCase(holdResult = true)
        val viewModel = VisionViewModel(inferenceUseCase)
        viewModel.onIntent(VisionIntent.ImageSelected("content://picked"))

        viewModel.onIntent(VisionIntent.RunRequested)
        viewModel.onIntent(VisionIntent.RunRequested)

        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(1, inferenceUseCase.runCount)
    }

    @Test
    fun switchingTaskInvalidatesRunningInferenceResult() = runTest {
        val inferenceUseCase = ControllableVisionInferenceUseCase()
        val viewModel = VisionViewModel(inferenceUseCase)
        viewModel.onIntent(VisionIntent.ImageSelected("content://ocr"))
        viewModel.onIntent(VisionIntent.RunRequested)
        inferenceUseCase.awaitRunStarted()

        viewModel.onIntent(VisionIntent.SelectTask(VisionTask.FACE))
        inferenceUseCase.complete(
            VisionInferenceResult(VisionTask.OCR, ImageSize(100, 100), elapsedMillis = 5),
        )
        advanceUntilIdle()

        assertEquals(VisionTask.FACE, viewModel.uiState.value.selectedTask)
        assertNull(viewModel.uiState.value.imageUri)
        assertNull(viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun openingPickerInvalidatesRunningInferenceResult() = runTest {
        val inferenceUseCase = ControllableVisionInferenceUseCase()
        val viewModel = VisionViewModel(inferenceUseCase)
        viewModel.onIntent(VisionIntent.ImageSelected("content://first"))
        viewModel.onIntent(VisionIntent.RunRequested)
        inferenceUseCase.awaitRunStarted()

        viewModel.onIntent(VisionIntent.PickImageClicked)
        inferenceUseCase.complete(
            VisionInferenceResult(VisionTask.OCR, ImageSize(100, 100), elapsedMillis = 5),
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun selectingNewImageInvalidatesRunningInferenceResult() = runTest {
        val inferenceUseCase = ControllableVisionInferenceUseCase()
        val viewModel = VisionViewModel(inferenceUseCase)
        viewModel.onIntent(VisionIntent.ImageSelected("content://first"))
        viewModel.onIntent(VisionIntent.RunRequested)
        inferenceUseCase.awaitRunStarted()

        viewModel.onIntent(VisionIntent.ImageSelected("content://second"))
        inferenceUseCase.complete(
            VisionInferenceResult(VisionTask.OCR, ImageSize(100, 100), elapsedMillis = 5),
        )
        advanceUntilIdle()

        assertEquals("content://second", viewModel.uiState.value.imageUri)
        assertNull(viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isRunning)
    }
}

private class FakeVisionInferenceUseCase(
    private val result: VisionInferenceResult = VisionInferenceResult(
        task = VisionTask.OCR,
        imageSize = ImageSize(1, 1),
        elapsedMillis = 1,
    ),
    private val holdResult: Boolean = false,
) : VisionInferenceUseCase {
    var runCount = 0

    override suspend fun run(
        task: VisionTask,
        ocrLanguage: OcrLanguage,
        imageUri: String,
    ): VisionInferenceResult {
        runCount++
        if (holdResult) {
            kotlinx.coroutines.awaitCancellation()
        }
        return result
    }
}

private class ControllableVisionInferenceUseCase : VisionInferenceUseCase {
    private val started = CompletableDeferred<Unit>()
    private val result = CompletableDeferred<VisionInferenceResult>()

    suspend fun awaitRunStarted() = started.await()

    fun complete(value: VisionInferenceResult) {
        result.complete(value)
    }

    override suspend fun run(
        task: VisionTask,
        ocrLanguage: OcrLanguage,
        imageUri: String,
    ): VisionInferenceResult {
        started.complete(Unit)
        return result.await()
    }
}
