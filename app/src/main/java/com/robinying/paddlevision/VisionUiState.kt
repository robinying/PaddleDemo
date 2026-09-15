package com.robinying.paddlevision

enum class VisionTask(val titleRes: Int, val descriptionRes: Int, val nativeId: String) {
    OCR(R.string.task_ocr, R.string.task_ocr_description, "ocr"),
    OBJECT(R.string.task_object, R.string.task_object_description, "object"),
    FACE(R.string.task_face, R.string.task_face_description, "face"),
}

/**
 * OCR languages the product plans to support. [isPackaged] is the single source of truth
 * for availability: [packaged] drives the selector, so a language whose model is not
 * bundled can never be chosen and can never lead the user into a run that always fails.
 */
enum class OcrLanguage(val titleRes: Int, val nativeId: String, val isPackaged: Boolean) {
    CHINESE(R.string.language_chinese, "zh", true),
    ENGLISH(R.string.language_english, "en", false),
    FRENCH(R.string.language_french, "fr", false),
    SPANISH(R.string.language_spanish, "es", false),
    ;

    companion object {
        val packaged: List<OcrLanguage> = entries.filter(OcrLanguage::isPackaged)
    }
}

/**
 * A localizable piece of UI text.
 *
 * [resourceId] is a `string` resource unless [quantity] is set, in which case it is a
 * `plurals` resource resolved with that count. Entries in [args] may themselves be
 * [UiText] values, which are resolved recursively so a message can embed another
 * localized fragment.
 */
data class UiText(
    val resourceId: Int,
    val args: List<Any> = emptyList(),
    val quantity: Int? = null,
)

data class VisionUiState(
    val selectedTask: VisionTask = VisionTask.OCR,
    val ocrLanguage: OcrLanguage = OcrLanguage.CHINESE,
    val imageUri: String? = null,
    val isRunning: Boolean = false,
    val result: VisionInferenceResult? = null,
    val message: UiText = UiText(R.string.message_choose_capability),
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
            result = null,
            message = UiText(if (intent.task == VisionTask.FACE) R.string.message_face_privacy else R.string.message_task_selected),
        )
        is VisionIntent.SelectOcrLanguage -> state.copy(
            ocrLanguage = intent.language,
            imageUri = null,
            isRunning = false,
            result = null,
            message = UiText(R.string.message_language_selected),
        )
        VisionIntent.PickImageClicked -> state
        is VisionIntent.ImageSelected -> if (intent.uri == null) {
            state.copy(isRunning = false, message = UiText(R.string.message_no_image_selected))
        } else {
            state.copy(
                imageUri = intent.uri,
                isRunning = false,
                result = null,
                message = UiText(R.string.message_image_selected),
            )
        }
        VisionIntent.RunRequested -> state
    }

    fun startRun(state: VisionUiState): VisionUiState = state.copy(
        isRunning = true,
        result = null,
        message = UiText(R.string.message_running),
    )

    fun runSucceeded(state: VisionUiState, result: VisionInferenceResult): VisionUiState = state.copy(
        isRunning = false,
        result = result,
        message = result.summaryText(),
    )

    fun runFailed(state: VisionUiState, userMessage: UiText): VisionUiState = state.copy(
        isRunning = false,
        result = null,
        message = userMessage,
    )
}

/**
 * Resolves a Pascal VOC category id against the packaged label list.
 * Out-of-range ids fall back to [fallback] rather than leaking a raw index into the UI.
 */
fun objectCategoryLabel(categoryId: Int, labels: List<String>, fallback: (Int) -> String): String =
    labels.getOrNull(categoryId) ?: fallback(categoryId)
