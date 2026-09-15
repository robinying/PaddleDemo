package com.robinying.paddlevision

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
            requireNotNull(resolver.openOutputStream(uri)).use { output ->
                output.write(TestAssets.bytes("object_dog.jpg"))
            }

            val result = LocalVisionInferenceUseCase(context).run(
                task = VisionTask.OBJECT,
                ocrLanguage = OcrLanguage.CHINESE,
                imageUri = uri.toString(),
            )

            assertTrue("Expected a content URI to reach the Paddle Lite object pipeline", result.detections.isNotEmpty())
            assertTrue(
                "Expected the selected dog image to retain its dog detection",
                result.detections.any { it.categoryId == PASCAL_VOC_DOG_CATEGORY_ID },
            )
            assertTrue(
                "End-to-end time must cover at least the measured model run",
                result.elapsedMillis >= result.inferenceMillis,
            )
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    /**
     * Two callers racing on one use case must serialize rather than run at the same time; the
     * gate makes the second wait, so both still return a complete result instead of one of them
     * being starved or the shared predictor being corrupted.
     */
    @Test
    fun concurrentRunsSerialiseAndBothComplete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "paddle-vision-concurrent-test.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PaddleVisionTest")
                },
            ),
        )
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { output ->
                output.write(TestAssets.bytes("object_dog.jpg"))
            }
            val useCase = LocalVisionInferenceUseCase(context)

            val results = listOf(
                async { useCase.run(VisionTask.OBJECT, OcrLanguage.CHINESE, uri.toString()) },
                async { useCase.run(VisionTask.OBJECT, OcrLanguage.CHINESE, uri.toString()) },
            ).awaitAll()

            assertEquals(2, results.size)
            results.forEach { result ->
                assertTrue(
                    "Every serialized run must still produce the dog detection",
                    result.detections.any { it.categoryId == PASCAL_VOC_DOG_CATEGORY_ID },
                )
            }
        } finally {
            resolver.delete(uri, null, null)
        }
    }
}
