package com.robinying.paddlevision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionGeometryTest {
    @Test
    fun normalizedBoxIsClampedAndMappedToPixels() {
        val result = VisionGeometry.normalizedToPixels(
            NormalizedBox(left = -0.2f, top = 0.1f, right = 1.2f, bottom = 0.8f),
            ImageSize(width = 1000, height = 500),
        )

        assertEquals(PixelBox(0f, 50f, 1000f, 400f), result)
    }

    @Test
    fun iouReturnsIntersectionOverUnion() {
        val result = VisionGeometry.iou(
            PixelBox(0f, 0f, 10f, 10f),
            PixelBox(5f, 5f, 15f, 15f),
        )

        assertEquals(1f / 7f, result, 0.0001f)
    }

    @Test
    fun ocrRegionExtractionSeparatesComponentsAndOrdersThemForReading() {
        val regions = extractOcrRegions(
            values = floatArrayOf(
                0f, 0.9f, 0.9f, 0f, 0f, 0f,
                0f, 0.9f, 0.9f, 0f, 0.8f, 0.8f,
                0f, 0f, 0f, 0f, 0.8f, 0.8f,
            ),
            mapWidth = 6,
            mapHeight = 3,
            sourceSize = ImageSize(60, 30),
        )

        assertEquals(
            listOf(
                PixelBox(10f, 0f, 30f, 20f),
                PixelBox(40f, 10f, 60f, 30f),
            ),
            regions,
        )
    }

    @Test
    fun ocrRegionExtractionIgnoresSinglePixelNoise() {
        val regions = extractOcrRegions(
            values = floatArrayOf(0.9f, 0f, 0f, 0f),
            mapWidth = 2,
            mapHeight = 2,
            sourceSize = ImageSize(20, 20),
        )

        assertTrue(regions.isEmpty())
    }

    @Test
    fun ocrRegionExtractionLimitsOutputRegionCount() {
        val regions = extractOcrRegions(
            values = floatArrayOf(0.9f, 0.9f, 0f, 0.8f, 0.8f, 0f, 0.7f, 0.7f, 0.7f),
            mapWidth = 9,
            mapHeight = 1,
            sourceSize = ImageSize(90, 10),
            maxRegions = 2,
        )

        assertEquals(2, regions.size)
    }

    @Test
    fun nonMaximumSuppressionKeepsHighestScoreAndSeparateBox() {
        val result = VisionGeometry.nonMaximumSuppression(
            boxes = listOf(
                PixelBox(0f, 0f, 10f, 10f),
                PixelBox(1f, 1f, 9f, 9f),
                PixelBox(20f, 20f, 30f, 30f),
            ),
            scores = listOf(0.8f, 0.9f, 0.7f),
            threshold = 0.5f,
        )

        assertEquals(listOf(1, 2), result)
        assertTrue(result.none { it == 0 })
    }
}
