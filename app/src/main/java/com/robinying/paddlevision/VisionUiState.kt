package com.robinying.paddlevision

import android.graphics.Bitmap

enum class VisionTask(val title: String, val description: String, val nativeId: String) {
    OCR("OCR", "识别图片中的文字", "ocr"),
    OBJECT("物体检测", "检测类别、位置和置信度", "object"),
    FACE("人脸检测", "仅检测人脸位置，不识别身份", "face"),
}

enum class OcrLanguage(val title: String, val nativeId: String) {
    CHINESE("中文", "zh"),
    ENGLISH("英语", "en"),
    FRENCH("法语", "fr"),
    SPANISH("西班牙语", "es"),
}

data class VisionUiState(
    val selectedTask: VisionTask = VisionTask.OCR,
    val ocrLanguage: OcrLanguage = OcrLanguage.CHINESE,
    val imageUri: String? = null,
    val isRunning: Boolean = false,
    val message: String = "请选择能力并从相册选择图片",
) {
    val canRun: Boolean get() = imageUri != null && !isRunning
}

sealed interface VisionAction {
    data class SelectTask(val task: VisionTask) : VisionAction
    data class SelectOcrLanguage(val language: OcrLanguage) : VisionAction
    data class ImageSelected(val uri: String?) : VisionAction
    data object StartRun : VisionAction
    data class RunFinished(val result: String) : VisionAction
}

object VisionUiReducer {
    fun reduce(state: VisionUiState, action: VisionAction): VisionUiState = when (action) {
        is VisionAction.SelectTask -> state.copy(
            selectedTask = action.task,
            imageUri = null,
            isRunning = false,
            message = if (action.task == VisionTask.FACE) {
                "人脸检测仅显示位置，不识别身份"
            } else {
                "已选择${action.task.title}，请从相册选择图片"
            },
        )
        is VisionAction.SelectOcrLanguage -> state.copy(
            ocrLanguage = action.language,
            imageUri = null,
            isRunning = false,
            message = "已选择${action.language.title} OCR，请重新选择图片",
        )
        is VisionAction.ImageSelected -> if (action.uri == null) state.copy(message = "未选择图片") else state.copy(
            imageUri = action.uri,
            message = "已选择图片，可运行${state.selectedTask.title}",
        )
        VisionAction.StartRun -> state.copy(isRunning = true, message = "正在运行${state.selectedTask.title}…")
        is VisionAction.RunFinished -> state.copy(isRunning = false, message = action.result)
    }
}
