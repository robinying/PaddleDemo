package com.robinying.paddlevision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Covers the pure OCR post-processing helpers that previously had no JVM coverage. */
class OcrPostProcessingTest {
    private val dictionary = listOf("a", "b", "c")
    private val classCount = 4
    private val boundingBox = PixelBox(0f, 0f, 10f, 10f)

    @Test
    fun ctcDecodingCollapsesRepeatedClassesAcrossTimesteps() {
        val block = decodeOcrRecognition(
            values = flatten(oneHot(1), oneHot(1), oneHot(1), blank()),
            shape = longArrayOf(1, 4, classCount.toLong()),
            dictionary = dictionary,
            boundingBox = boundingBox,
        )

        assertEquals("a", block?.text)
    }

    @Test
    fun ctcDecodingKeepsRepeatsThatAreSeparatedByABlank() {
        val block = decodeOcrRecognition(
            values = flatten(oneHot(1), blank(), oneHot(1)),
            shape = longArrayOf(1, 3, classCount.toLong()),
            dictionary = dictionary,
            boundingBox = boundingBox,
        )

        assertEquals("aa", block?.text)
    }

    @Test
    fun ctcDecodingTrimsLeadingAndTrailingBlanks() {
        val block = decodeOcrRecognition(
            values = flatten(blank(), oneHot(2), oneHot(3), blank(), blank()),
            shape = longArrayOf(1, 5, classCount.toLong()),
            dictionary = dictionary,
            boundingBox = boundingBox,
        )

        assertEquals("bc", block?.text)
        assertEquals(boundingBox, block?.boundingBox)
    }

    @Test
    fun ctcDecodingReturnsNullWhenEveryTimestepIsBlank() {
        val block = decodeOcrRecognition(
            values = flatten(blank(), blank()),
            shape = longArrayOf(1, 2, classCount.toLong()),
            dictionary = dictionary,
            boundingBox = boundingBox,
        )

        assertEquals(null, block)
    }

    @Test
    fun ctcDecodingDropsClassesThatTheDictionaryDoesNotCover() {
        val widerClassCount = 6
        val block = decodeOcrRecognition(
            values = flatten(
                hot(widerClassCount, 5),
                blank(widerClassCount),
            ),
            shape = longArrayOf(1, 2, widerClassCount.toLong()),
            dictionary = dictionary,
            boundingBox = boundingBox,
        )

        assertEquals(null, block)
    }

    @Test
    fun ctcDecodingRejectsAnOutputShapeThatDoesNotMatchTheData() {
        try {
            decodeOcrRecognition(
                values = floatArrayOf(0.1f, 0.9f),
                shape = longArrayOf(1, 0, classCount.toLong()),
                dictionary = dictionary,
                boundingBox = boundingBox,
            )
            fail("Expected the recognition output contract to be enforced")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.INFERENCE_FAILED, exception.code)
            assertEquals(UiText(R.string.error_ocr_recognition_output_invalid), exception.text)
        }
    }

    private fun oneHot(index: Int): FloatArray = hot(classCount, index)

    private fun blank(count: Int = classCount): FloatArray = hot(count, 0)

    private fun hot(count: Int, index: Int): FloatArray = FloatArray(count) { position ->
        if (position == index) 0.9f else 0.1f
    }

    private fun flatten(vararg rows: FloatArray): FloatArray = FloatArray(rows.sumOf { it.size }).also { flat ->
        var offset = 0
        rows.forEach { row ->
            row.copyInto(flat, offset)
            offset += row.size
        }
    }

    @Test
    fun detectorInputIsScaledDownToTheModelMaximumAndRoundedToThirtyTwo() {
        assertEquals(ImageSize(960, 480), calculateOcrDetectorSize(4000, 2000))
    }

    @Test
    fun detectorInputIsNeverUpscaled() {
        assertEquals(ImageSize(128, 64), calculateOcrDetectorSize(100, 50))
    }

    @Test
    fun detectorInputKeepsAMinimumOfThirtyTwoPixelsPerSide() {
        assertEquals(ImageSize(32, 32), calculateOcrDetectorSize(1, 1))
    }

    @Test
    fun recognitionWidthFollowsTheAspectRatioWithinItsClamp() {
        assertEquals(100, calculateRecognitionWidth(100, 32))
    }

    @Test
    fun recognitionWidthIsClampedToTheModelRange() {
        assertEquals(320, calculateRecognitionWidth(4000, 32))
        assertEquals(32, calculateRecognitionWidth(4, 32))
    }

    @Test
    fun recognitionWidthFallsBackWhenTheCropHasNoHeight() {
        assertEquals(32, calculateRecognitionWidth(100, 0))
    }

    @Test
    fun ocrRegionsAreOrderedByReadingOrderRatherThanByScanIndex() {
        // The lower line is found first by the row-major scan, so a top-to-bottom result proves
        // the explicit reading-order sort rather than an accidental match with scan order. The
        // two components are kept two columns apart so 8-connected flood fill cannot merge them.
        val regions = extractOcrRegions(
            values = floatArrayOf(
                0f, 0f, 0f, 0.9f, 0.9f, 0f,
                0.9f, 0.9f, 0f, 0f, 0f, 0f,
            ),
            mapWidth = 6,
            mapHeight = 2,
            sourceSize = ImageSize(60, 20),
        )

        assertEquals(
            listOf(PixelBox(30f, 0f, 50f, 10f), PixelBox(0f, 10f, 20f, 20f)),
            regions,
        )
    }

    @Test
    fun theRegionLimitKeepsTheReadingOrderPrefix() {
        val regions = extractOcrRegions(
            values = floatArrayOf(
                0f, 0f, 0f, 0.9f, 0.9f, 0f,
                0.9f, 0.9f, 0f, 0f, 0f, 0f,
            ),
            mapWidth = 6,
            mapHeight = 2,
            sourceSize = ImageSize(60, 20),
            maxRegions = 1,
        )

        assertEquals(listOf(PixelBox(30f, 0f, 50f, 10f)), regions)
    }

    @Test
    fun ocrRegionsBelowTheLowConfidenceThresholdAreIgnored() {
        val regions = extractOcrRegions(
            values = floatArrayOf(0.29f, 0.9f, 0.9f, 0f),
            mapWidth = 4,
            mapHeight = 1,
            sourceSize = ImageSize(40, 10),
        )

        assertEquals(listOf(PixelBox(10f, 0f, 30f, 10f)), regions)
    }

    @Test
    fun ocrRegionsSmallerThanTheMinimumRegionSizeAreDropped() {
        val regions = extractOcrRegions(
            values = floatArrayOf(0.9f, 0.9f, 0.9f, 0.9f),
            mapWidth = 2,
            mapHeight = 2,
            sourceSize = ImageSize(2, 2),
        )

        assertTrue(regions.isEmpty())
    }

    @Test
    fun ocrRegionExtractionRejectsInvalidMapDimensions() {
        try {
            extractOcrRegions(
                values = floatArrayOf(0.9f),
                mapWidth = 0,
                mapHeight = 1,
                sourceSize = ImageSize(10, 10),
            )
            fail("Expected invalid detection map dimensions to be rejected")
        } catch (exception: VisionInferenceException) {
            assertEquals(UiText(R.string.error_ocr_detection_output_invalid), exception.text)
        }
    }
}
