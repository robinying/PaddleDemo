package com.robinying.paddlevision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionInferenceResultTest {
    @Test
    fun objectSummaryContainsCountAndDuration() {
        val result = VisionInferenceResult(
            task = VisionTask.OBJECT,
            imageSize = ImageSize(100, 100),
            elapsedMillis = 37,
            detections = listOf(
                DetectedObject(12, 0.95f, PixelBox(10f, 10f, 50f, 50f)),
                DetectedObject(15, 0.85f, PixelBox(60f, 10f, 90f, 70f)),
            ),
        )

        assertEquals(
            UiText(R.plurals.result_object_summary, listOf<Any>(2, 37L), quantity = 2),
            result.summaryText(),
        )
    }

    @Test
    fun summariesUsePluralResourcesSoEveryLanguageCanPickItsOwnForm() {
        val single = VisionInferenceResult(
            task = VisionTask.FACE,
            imageSize = ImageSize(10, 10),
            elapsedMillis = 3,
            faces = listOf(DetectedFace(0.9f, PixelBox(0f, 0f, 1f, 1f))),
        )

        assertEquals(R.plurals.result_face_summary, single.summaryText().resourceId)
        assertEquals(1, single.summaryText().quantity)
    }

    @Test
    fun detectedObjectCarriesALanguageNeutralCategoryId() {
        val detection = DetectedObject(12, 0.95f, PixelBox(10f, 10f, 50f, 50f))

        assertEquals(12, detection.categoryId)
        assertTrue(detection.confidence > 0f)
        assertTrue(detection.boundingBox.width > 0f)
    }

    @Test
    fun faceResultOnlyContainsPositionAndConfidence() {
        val face = DetectedFace(
            confidence = 0.9f,
            boundingBox = PixelBox(0f, 0f, 20f, 20f),
        )

        assertEquals(0.9f, face.confidence, 0f)
        assertTrue(face.boundingBox.width > 0f)
    }

    @Test
    fun everyErrorCodeHasItsOwnLocalizedFallback() {
        val fallbackResources = VisionErrorCode.entries.map(VisionErrorCode::messageRes)

        assertEquals(VisionErrorCode.entries.size, fallbackResources.toSet().size)
    }
}
