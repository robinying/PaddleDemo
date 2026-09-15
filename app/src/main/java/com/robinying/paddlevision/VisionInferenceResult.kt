package com.robinying.paddlevision

/**
 * Immutable output returned by the Android vision inference boundary.
 * Coordinates are always in decoded source-image pixels.
 *
 * [elapsedMillis] covers the whole user-visible pipeline the app reports as
 * end-to-end time: image decoding, model preparation, preprocessing, inference
 * and post-processing. [inferenceMillis] is the model-run portion measured by
 * [PaddleLiteEngine] and exists only for diagnostics.
 */
data class VisionInferenceResult(
    val task: VisionTask,
    val imageSize: ImageSize,
    val elapsedMillis: Long,
    val inferenceMillis: Long = 0L,
    val textBlocks: List<OcrTextBlock> = emptyList(),
    val detections: List<DetectedObject> = emptyList(),
    val faces: List<DetectedFace> = emptyList(),
) {
    fun summaryText(): UiText = when (task) {
        VisionTask.OCR -> UiText(
            resourceId = R.plurals.result_ocr_summary,
            args = listOf(textBlocks.size, elapsedMillis),
            quantity = textBlocks.size,
        )
        VisionTask.OBJECT -> UiText(
            resourceId = R.plurals.result_object_summary,
            args = listOf(detections.size, elapsedMillis),
            quantity = detections.size,
        )
        VisionTask.FACE -> UiText(
            resourceId = R.plurals.result_face_summary,
            args = listOf(faces.size, elapsedMillis),
            quantity = faces.size,
        )
    }
}

data class OcrTextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: PixelBox,
)

/**
 * Carries the language-neutral Pascal VOC [categoryId] instead of a display name.
 * The UI resolves it against `R.array.object_categories` so the label follows the
 * device locale rather than the language the model was trained on.
 */
data class DetectedObject(
    val categoryId: Int,
    val confidence: Float,
    val boundingBox: PixelBox,
)

/** Contains only local face-position data and never an identity or biometric template. */
data class DetectedFace(
    val confidence: Float,
    val boundingBox: PixelBox,
)

/**
 * Classification of inference failures. [messageRes] is the localized fallback shown
 * when a failure is raised without an explicit [UiText].
 */
enum class VisionErrorCode(val messageRes: Int) {
    ASSET_MISSING(R.string.error_code_asset_missing),
    MODEL_INITIALIZATION_FAILED(R.string.error_code_model_initialization_failed),
    IMAGE_DECODE_FAILED(R.string.error_code_image_decode_failed),
    UNSUPPORTED_TASK(R.string.error_code_unsupported_task),
    INFERENCE_FAILED(R.string.error_code_inference_failed),
}

/**
 * Carries a localizable [text] for the UI so the screen never shows an internal enum name.
 * [code] is for logging and classification only, and [Exception.message] stays a
 * non-localized diagnostic string that is never rendered.
 */
class VisionInferenceException(
    val code: VisionErrorCode,
    val text: UiText,
    cause: Throwable? = null,
) : Exception(code.name, cause)
