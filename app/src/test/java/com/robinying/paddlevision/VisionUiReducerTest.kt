package com.robinying.paddlevision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VisionUiReducerTest {
    @Test
    fun selectingFaceResetsPreviousImageResultAndUsesDetectionOnlyCopy() {
        val current = VisionUiState(
            selectedTask = VisionTask.OCR,
            imageUri = "content://image",
            result = sampleOcrResult(),
        )

        val result = VisionUiReducer.reduce(current, VisionIntent.SelectTask(VisionTask.FACE))

        assertEquals(VisionTask.FACE, result.selectedTask)
        assertNull(result.imageUri)
        assertNull(result.result)
        assertEquals(UiText(R.string.message_face_privacy), result.message)
    }

    @Test
    fun selectingImageEnablesRunAndClearsPreviousResult() {
        val current = VisionUiState(
            selectedTask = VisionTask.OBJECT,
            result = sampleOcrResult(),
        )

        val result = VisionUiReducer.reduce(current, VisionIntent.ImageSelected("content://picked"))

        assertEquals("content://picked", result.imageUri)
        assertNull(result.result)
        assertEquals(UiText(R.string.message_image_selected), result.message)
        assertEquals(true, result.canRun)
    }

    @Test
    fun selectingImageStopsAnyInvalidatedInference() {
        val current = VisionUiState(isRunning = true)

        val result = VisionUiReducer.reduce(current, VisionIntent.ImageSelected("content://picked"))

        assertFalse(result.isRunning)
        assertEquals("content://picked", result.imageUri)
    }

    @Test
    fun successfulRunStoresResultAndMakesSummaryVisible() {
        val current = VisionUiState(selectedTask = VisionTask.OCR, imageUri = "content://picked", isRunning = true)
        val inferenceResult = sampleOcrResult()

        val result = VisionUiReducer.runSucceeded(current, inferenceResult)

        assertEquals(false, result.isRunning)
        assertEquals(inferenceResult, result.result)
        assertEquals(UiText(R.string.result_ocr_summary, listOf<Any>(1, 12L)), result.message)
    }

    @Test
    fun failedRunClearsPreviousResult() {
        val current = VisionUiState(
            imageUri = "content://picked",
            isRunning = true,
            result = sampleOcrResult(),
        )

        val result = VisionUiReducer.runFailed(current, UiText(R.string.message_inference_failed, listOf("INFERENCE_FAILED")))

        assertNull(result.result)
        assertEquals(false, result.isRunning)
    }

    @Test
    fun changingOcrLanguageClearsImageAndResult() {
        val current = VisionUiState(imageUri = "content://picked", result = sampleOcrResult())

        val result = VisionUiReducer.reduce(current, VisionIntent.SelectOcrLanguage(OcrLanguage.FRENCH))

        assertEquals(OcrLanguage.FRENCH, result.ocrLanguage)
        assertNull(result.imageUri)
        assertNull(result.result)
        assertEquals(UiText(R.string.message_language_selected), result.message)
    }

    private fun sampleOcrResult() = VisionInferenceResult(
        task = VisionTask.OCR,
        imageSize = ImageSize(100, 100),
        elapsedMillis = 12,
        textBlocks = listOf(OcrTextBlock("测试", 0.9f, PixelBox(0f, 0f, 10f, 10f))),
    )
}
