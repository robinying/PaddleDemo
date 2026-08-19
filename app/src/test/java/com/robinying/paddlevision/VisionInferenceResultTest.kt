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
                DetectedObject("狗", 0.95f, PixelBox(10f, 10f, 50f, 50f)),
                DetectedObject("人", 0.85f, PixelBox(60f, 10f, 90f, 70f)),
            ),
        )

        assertEquals("检测到 2 个目标，耗时 37 ms", result.summary())
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
}
