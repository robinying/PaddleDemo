package com.robinying.paddlevision

import org.junit.Assert.assertEquals
import org.junit.Test

class VisionUiReducerTest {
    @Test
    fun selectingFaceResetsPreviousImageAndUsesDetectionOnlyCopy() {
        val current = VisionUiState(selectedTask = VisionTask.OCR, imageUri = "content://image")

        val result = VisionUiReducer.reduce(current, VisionAction.SelectTask(VisionTask.FACE))

        assertEquals(VisionTask.FACE, result.selectedTask)
        assertEquals(null, result.imageUri)
        assertEquals("人脸检测仅显示位置，不识别身份", result.message)
    }

    @Test
    fun selectingImageEnablesRunAndShowsTaskSpecificPrompt() {
        val current = VisionUiState(selectedTask = VisionTask.OBJECT)

        val result = VisionUiReducer.reduce(current, VisionAction.ImageSelected("content://picked"))

        assertEquals("content://picked", result.imageUri)
        assertEquals("已选择图片，可运行物体检测", result.message)
        assertEquals(true, result.canRun)
    }

    @Test
    fun nativeResultBecomesVisibleResultMessage() {
        val current = VisionUiState(selectedTask = VisionTask.OCR, imageUri = "content://picked", isRunning = true)

        val result = VisionUiReducer.reduce(current, VisionAction.RunFinished("检测到 2 个文本块"))

        assertEquals(false, result.isRunning)
        assertEquals("检测到 2 个文本块", result.message)
    }

    @Test
    fun changingOcrLanguageClearsImageAndShowsSelectedLanguage() {
        val current = VisionUiState(imageUri = "content://picked")

        val result = VisionUiReducer.reduce(current, VisionAction.SelectOcrLanguage(OcrLanguage.FRENCH))

        assertEquals(OcrLanguage.FRENCH, result.ocrLanguage)
        assertEquals(null, result.imageUri)
        assertEquals("已选择法语 OCR，请重新选择图片", result.message)
    }
}
