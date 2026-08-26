package com.robinying.paddlevision

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaddleLiteEngineInstrumentedSmokeTest {
    @Test
    fun paddleLiteRuntimeCreatesPredictorAndRunsObjectModel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = context.assets.open("samples/object_dog.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare()
            val result = PaddleLiteEngine().run(VisionTask.OBJECT, bitmap, models.rootDirectory)

            assertFalse("Expected a real Paddle Lite inference result", result.detections.isEmpty())
            val dog = result.detections.firstOrNull { it.categoryName == "狗" }
            assertTrue("Expected the bundled dog sample to detect a dog", dog != null)
            assertTrue(
                "Expected the dog box to align with the annotated dog",
                VisionGeometry.iou(
                    requireNotNull(dog).boundingBox,
                    PixelBox(left = 110f, top = 185f, right = 350f, bottom = 545f),
                ) >= 0.75f,
            )
            assertTrue(result.detections.all { detection -> detection.boundingBox.width > 0f })
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun faceModelRunsOnBundledSmokeSampleWithoutIdentityData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = context.assets.open("samples/face.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare()
            val result = PaddleLiteEngine().run(VisionTask.FACE, bitmap, models.rootDirectory)

            assertTrue("Expected the crowd sample to contain many faces", result.faces.size >= 30)
            assertTrue(result.faces.all { face -> face.boundingBox.width > 0f && face.confidence > 0f })
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun ocrModelsRecognizeAtLeastOneTextBlockFromBundledSmokeSample() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = context.assets.open("samples/ocr/test.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare()
            val result = PaddleLiteEngine().run(VisionTask.OCR, bitmap, models.rootDirectory)

            assertTrue("Expected the text sample to contain multiple lines", result.textBlocks.size >= 10)
            assertTrue("Expected the price line to be recognized", result.textBlocks.any { it.text.contains("45元") })
            assertTrue("Expected the volume line to be recognized", result.textBlocks.any { it.text.contains("220") })
            assertTrue(
                "Expected text blocks to preserve top-to-bottom reading order",
                result.textBlocks.zipWithNext().all { (first, second) -> first.boundingBox.top <= second.boundingBox.top },
            )
            assertTrue(result.textBlocks.all { block -> block.text.isNotBlank() && block.confidence > 0f })
        } finally {
            bitmap.recycle()
        }
    }
}
