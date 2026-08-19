package com.robinying.paddlevision

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaddleLiteEngineInstrumentedTest {
    @Test
    fun objectModelRunsOnBundledGoldenSample() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = context.assets.open("samples/object_dog.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare()
            val result = PaddleLiteEngine().run(VisionTask.OBJECT, bitmap, models.rootDirectory)

            assertTrue("Expected at least one object detection", result.detections.isNotEmpty())
            assertTrue(result.detections.all { detection -> detection.boundingBox.width > 0f })
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun faceModelRunsOnBundledGoldenSampleWithoutIdentityData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = context.assets.open("samples/face.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare()
            val result = PaddleLiteEngine().run(VisionTask.FACE, bitmap, models.rootDirectory)

            assertTrue("Expected at least one face detection", result.faces.isNotEmpty())
            assertTrue(result.faces.all { face -> face.boundingBox.width > 0f && face.confidence > 0f })
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun ocrModelsRecognizeAtLeastOneTextBlockFromBundledSample() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = context.assets.open("samples/ocr/test.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare()
            val result = PaddleLiteEngine().run(VisionTask.OCR, bitmap, models.rootDirectory)

            assertTrue("Expected at least one OCR text block", result.textBlocks.isNotEmpty())
            assertTrue(result.textBlocks.all { block -> block.text.isNotBlank() && block.confidence > 0f })
        } finally {
            bitmap.recycle()
        }
    }
}
