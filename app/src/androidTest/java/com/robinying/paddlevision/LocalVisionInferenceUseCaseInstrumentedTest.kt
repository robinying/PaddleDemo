package com.robinying.paddlevision

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalVisionInferenceUseCaseInstrumentedTest {
    @Test
    fun contentUriFlowsThroughDecodeModelPreparationAndObjectInference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "paddle-vision-object-test.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PaddleVisionTest")
                },
            ),
        )
        try {
            context.assets.open("samples/object_dog.jpg").use { input ->
                requireNotNull(resolver.openOutputStream(uri)).use(input::copyTo)
            }

            val result = LocalVisionInferenceUseCase(context).run(
                task = VisionTask.OBJECT,
                ocrLanguage = OcrLanguage.CHINESE,
                imageUri = uri.toString(),
            )

            assertTrue("Expected a content URI to reach the Paddle Lite object pipeline", result.detections.isNotEmpty())
            assertTrue("Expected the selected dog image to retain its dog detection", result.detections.any { it.categoryName == "狗" })
        } finally {
            resolver.delete(uri, null, null)
        }
    }
}
