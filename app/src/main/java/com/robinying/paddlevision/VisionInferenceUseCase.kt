package com.robinying.paddlevision

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface VisionInferenceUseCase {
    suspend fun run(
        task: VisionTask,
        ocrLanguage: OcrLanguage,
        imageUri: String,
    ): VisionInferenceResult
}

/**
 * Serializes non-cancellable work.
 *
 * Cancelling a coroutine that is blocked inside a Paddle Lite JNI call does not stop the
 * native computation; it keeps running to completion and only its result is discarded.
 * Without a gate, a replacement request would start a second copy of the models, the
 * decoded bitmaps and the inference threads on top of the first, roughly doubling the
 * memory peak. A waiting caller is suspended cancellably, so an obsolete request that is
 * cancelled while queued never runs at all.
 */
class InferenceGate {
    private val mutex = Mutex()

    suspend fun <T> runExclusive(block: suspend () -> T): T = mutex.withLock { block() }
}

class LocalVisionInferenceUseCase(
    context: Context,
    private val imageDecoder: ImageDecoder = ImageDecoder(context),
    private val modelStore: ModelStore = ModelStore(context),
    private val paddleLiteEngine: PaddleLiteEngine = PaddleLiteEngine(),
    private val inferenceGate: InferenceGate = InferenceGate(),
) : VisionInferenceUseCase {
    override suspend fun run(
        task: VisionTask,
        ocrLanguage: OcrLanguage,
        imageUri: String,
    ): VisionInferenceResult = withContext(Dispatchers.Default) {
        inferenceGate.runExclusive {
            val startedAtNanos = System.nanoTime()
            val decodedImage = imageDecoder.decode(imageUri.toUri())
            try {
                val models = modelStore.prepare(task)
                val inference = paddleLiteEngine.run(
                    task = task,
                    bitmap = decodedImage.bitmap,
                    modelDirectory = models.rootDirectory,
                    ocrLanguage = ocrLanguage,
                )
                inference.copy(
                    elapsedMillis = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI,
                    inferenceMillis = inference.elapsedMillis,
                )
            } finally {
                decodedImage.bitmap.recycle()
            }
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
