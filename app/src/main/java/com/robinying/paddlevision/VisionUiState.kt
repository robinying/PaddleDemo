package com.robinying.paddlevision

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

sealed interface VisionIntent {
    data class SelectTask(val task: VisionTask) : VisionIntent
    data class SelectOcrLanguage(val language: OcrLanguage) : VisionIntent
    data object PickImageClicked : VisionIntent
    data class ImageSelected(val uri: String?) : VisionIntent
    data object RunRequested : VisionIntent
}

sealed interface VisionEffect {
    data object OpenPhotoPicker : VisionEffect
}

object VisionUiReducer {
    fun reduce(state: VisionUiState, intent: VisionIntent): VisionUiState = when (intent) {
        is VisionIntent.SelectTask -> state.copy(
            selectedTask = intent.task,
            imageUri = null,
            isRunning = false,
            message = if (intent.task == VisionTask.FACE) {
                "人脸检测仅显示位置，不识别身份"
            } else {
                "已选择${intent.task.title}，请从相册选择图片"
            },
        )
        is VisionIntent.SelectOcrLanguage -> state.copy(
            ocrLanguage = intent.language,
            imageUri = null,
            isRunning = false,
            message = "已选择${intent.language.title} OCR，请重新选择图片",
        )
        VisionIntent.PickImageClicked -> state
        is VisionIntent.ImageSelected -> if (intent.uri == null) {
            state.copy(message = "未选择图片")
        } else {
            state.copy(
                imageUri = intent.uri,
                message = "已选择图片，可运行${state.selectedTask.title}",
            )
        }
        VisionIntent.RunRequested -> state
    }

    fun startRun(state: VisionUiState): VisionUiState = state.copy(
        isRunning = true,
        message = "正在运行${state.selectedTask.title}…",
    )

    fun runSucceeded(state: VisionUiState, result: VisionInferenceResult): VisionUiState = state.copy(
        isRunning = false,
        message = result.summary(),
    )

    fun runFailed(state: VisionUiState, userMessage: String): VisionUiState = state.copy(
        isRunning = false,
        message = userMessage,
    )
}
