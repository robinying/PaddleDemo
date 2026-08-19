package com.robinying.paddlevision

/**
 * Immutable output returned by the Android vision inference boundary.
 * Coordinates are always in decoded source-image pixels.
 */
data class VisionInferenceResult(
    val task: VisionTask,
    val imageSize: ImageSize,
    val elapsedMillis: Long,
    val textBlocks: List<OcrTextBlock> = emptyList(),
    val detections: List<DetectedObject> = emptyList(),
    val faces: List<DetectedFace> = emptyList(),
) {
    fun summary(): String = when (task) {
        VisionTask.OCR -> "识别到 ${textBlocks.size} 个文本块，耗时 ${elapsedMillis} ms"
        VisionTask.OBJECT -> "检测到 ${detections.size} 个目标，耗时 ${elapsedMillis} ms"
        VisionTask.FACE -> "检测到 ${faces.size} 张人脸，耗时 ${elapsedMillis} ms"
    }
}

data class OcrTextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: PixelBox,
)

data class DetectedObject(
    val categoryName: String,
    val confidence: Float,
    val boundingBox: PixelBox,
)

/** Contains only local face-position data and never an identity or biometric template. */
data class DetectedFace(
    val confidence: Float,
    val boundingBox: PixelBox,
)

enum class VisionErrorCode {
    ASSET_MISSING,
    MODEL_INITIALIZATION_FAILED,
    IMAGE_DECODE_FAILED,
    UNSUPPORTED_TASK,
    INFERENCE_FAILED,
}

class VisionInferenceException(
    val code: VisionErrorCode,
    userMessage: String,
) : Exception(userMessage)
