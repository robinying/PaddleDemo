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
        assertEquals("人脸检测仅显示位置，不识别身份", result.message)
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
        assertEquals("已选择图片，可运行物体检测", result.message)
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
        assertEquals("识别到 1 个文本块，耗时 12 ms", result.message)
    }

    @Test
    fun failedRunClearsPreviousResult() {
        val current = VisionUiState(
            imageUri = "content://picked",
            isRunning = true,
            result = sampleOcrResult(),
        )

        val result = VisionUiReducer.runFailed(current, "INFERENCE_FAILED: 推理失败")

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
        assertEquals("已选择法语 OCR，请重新选择图片", result.message)
    }

    private fun sampleOcrResult() = VisionInferenceResult(
        task = VisionTask.OCR,
        imageSize = ImageSize(100, 100),
        elapsedMillis = 12,
        textBlocks = listOf(OcrTextBlock("测试", 0.9f, PixelBox(0f, 0f, 10f, 10f))),
    )
}
