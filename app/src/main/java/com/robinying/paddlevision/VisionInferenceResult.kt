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
    fun summaryText(): UiText = when (task) {
        VisionTask.OCR -> UiText(R.string.result_ocr_summary, listOf(textBlocks.size, elapsedMillis))
        VisionTask.OBJECT -> UiText(R.string.result_object_summary, listOf(detections.size, elapsedMillis))
        VisionTask.FACE -> UiText(R.string.result_face_summary, listOf(faces.size, elapsedMillis))
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
