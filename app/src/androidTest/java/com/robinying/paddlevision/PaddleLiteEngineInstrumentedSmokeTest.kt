package com.robinying.paddlevision

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaddleLiteEngineInstrumentedSmokeTest {
    @Test
    fun paddleLiteRuntimeCreatesPredictorAndRunsObjectModel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = TestAssets.open("object_dog.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare(VisionTask.OBJECT)
            val result = PaddleLiteEngine().run(VisionTask.OBJECT, bitmap, models.rootDirectory)

            assertFalse("Expected a real Paddle Lite inference result", result.detections.isEmpty())
            val dog = result.detections.firstOrNull { it.categoryId == PASCAL_VOC_DOG_CATEGORY_ID }
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
        val bitmap = TestAssets.open("face.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare(VisionTask.FACE)
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
        val bitmap = TestAssets.open("ocr/test.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare(VisionTask.OCR)
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

    /** Object detection must stay language neutral: the UI, not the engine, picks the label. */
    @Test
    fun objectDetectionReturnsCategoryIdsRatherThanDisplayNames() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = TestAssets.open("object_dog.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare(VisionTask.OBJECT)
            val result = PaddleLiteEngine().run(VisionTask.OBJECT, bitmap, models.rootDirectory)

            assertTrue(result.detections.isNotEmpty())
            assertTrue(
                "Category ids must stay inside the packaged Pascal VOC label range",
                result.detections.all { it.categoryId in 0..PASCAL_VOC_LABEL_COUNT - 1 },
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun unpackagedOcrLanguagesAreRejectedBeforeAnyModelIsLoaded() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = TestAssets.open("ocr/test.jpg").use(BitmapFactory::decodeStream)
        try {
            val models = ModelStore(context).prepare(VisionTask.OCR)
            PaddleLiteEngine().run(VisionTask.OCR, bitmap, models.rootDirectory, OcrLanguage.ENGLISH)
            org.junit.Assert.fail("Expected an unpackaged OCR language to be rejected")
        } catch (exception: VisionInferenceException) {
            assertTrue(exception.code == VisionErrorCode.UNSUPPORTED_TASK)
            assertTrue(exception.text == UiText(R.string.error_ocr_language_unsupported))
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * [ModelStore] installs the model before the engine runs, so a path that no longer holds the
     * file is the one case where the private copy and the contract disagree — a wipe mid-run, a
     * half-finished install, or a directory left over from an older model version. The engine has
     * to classify that as [VisionErrorCode.ASSET_MISSING] rather than hand the path to the runtime.
     */
    @Test
    fun aModelFileThatIsNotInstalledIsRejectedBeforeTheRuntimeSeesIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = TestAssets.open("object_dog.jpg").use(BitmapFactory::decodeStream)
        try {
            val missingDirectory = File(context.cacheDir, "paddle-vision-absent-models").apply { deleteRecursively() }

            PaddleLiteEngine().run(VisionTask.OBJECT, bitmap, missingDirectory)
            fail("Expected an uninstalled model file to be rejected")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.ASSET_MISSING, exception.code)
            assertEquals(
                UiText(R.string.error_model_file_missing, listOf(File(OBJECT_MODEL_ASSET).name)),
                exception.text,
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** A truncated install leaves a zero-length file, which `isFile` alone would accept. */
    @Test
    fun anEmptyModelFileIsRejectedInsteadOfBeingParsed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = TestAssets.open("object_dog.jpg").use(BitmapFactory::decodeStream)
        val directory = File(context.cacheDir, "paddle-vision-empty-model")
        try {
            val emptyModel = File(directory, OBJECT_MODEL_ASSET)
            emptyModel.parentFile?.mkdirs()
            emptyModel.writeBytes(ByteArray(0))

            PaddleLiteEngine().run(VisionTask.OBJECT, bitmap, directory)
            fail("Expected an empty model file to be rejected")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.ASSET_MISSING, exception.code)
            assertEquals(
                UiText(R.string.error_model_file_missing, listOf(File(OBJECT_MODEL_ASSET).name)),
                exception.text,
            )
        } finally {
            directory.deleteRecursively()
            bitmap.recycle()
        }
    }

    /**
     * The OCR pipeline is the only one split across several assets, so the dictionary is the one
     * file that can be missing while the models are present. It must be detected before the
     * recognizer runs, otherwise every region would decode against an empty alphabet and the user
     * would get an empty result instead of an explanation.
     */
    @Test
    fun aMissingOcrDictionaryIsRejectedBeforeTheRecognizerRuns() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = TestAssets.open("ocr/test.jpg").use(BitmapFactory::decodeStream)
        try {
            val missingDirectory = File(context.cacheDir, "paddle-vision-absent-ocr").apply { deleteRecursively() }

            PaddleLiteEngine().run(VisionTask.OCR, bitmap, missingDirectory, OcrLanguage.CHINESE)
            fail("Expected the OCR dictionary to be required")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.ASSET_MISSING, exception.code)
            assertEquals(UiText(R.string.error_ocr_dictionary_missing), exception.text)
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val PASCAL_VOC_LABEL_COUNT = 21
    }
}
