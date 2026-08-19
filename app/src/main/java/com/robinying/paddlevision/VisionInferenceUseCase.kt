package com.robinying.paddlevision

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.withContext

interface VisionInferenceUseCase {
    suspend fun run(
        task: VisionTask,
        ocrLanguage: OcrLanguage,
        imageUri: String,
    ): VisionInferenceResult
}

class LocalVisionInferenceUseCase(
    private val context: Context,
    private val imageDecoder: ImageDecoder = ImageDecoder(context),
    private val modelStore: ModelStore = ModelStore(context),
    private val paddleLiteEngine: PaddleLiteEngine = PaddleLiteEngine(),
) : VisionInferenceUseCase {
    override suspend fun run(
        task: VisionTask,
        ocrLanguage: OcrLanguage,
        imageUri: String,
    ): VisionInferenceResult = withContext(kotlinx.coroutines.Dispatchers.Default) {
        val decodedImage = imageDecoder.decode(Uri.parse(imageUri))
        try {
            val models = modelStore.prepare()
            paddleLiteEngine.run(
                task = task,
                bitmap = decodedImage.bitmap,
                modelDirectory = models.rootDirectory,
                ocrLanguage = ocrLanguage,
            )
        } finally {
            decodedImage.bitmap.recycle()
        }
    }
}
