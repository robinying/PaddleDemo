package com.robinying.paddlevision

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Covers the shared decode-size policy used by both the preview and the inference path. */
class ImageDecoderSizingTest {
    @Test
    fun smallImagesAreDecodedAtFullSize() {
        assertEquals(
            ImageSize(100, 50),
            calculateTargetSize(100, 50, maxDimension = 2048, maxPixels = 4_000_000),
        )
    }

    @Test
    fun longEdgesAreBoundedByTheDimensionLimit() {
        assertEquals(
            ImageSize(2048, 1024),
            calculateTargetSize(4000, 2000, maxDimension = 2048, maxPixels = 4_000_000),
        )
    }

    @Test
    fun hugeImagesAreBoundedByThePixelBudget() {
        assertEquals(
            ImageSize(2000, 2000),
            calculateTargetSize(8000, 8000, maxDimension = 2048, maxPixels = 4_000_000),
        )
    }

    @Test
    fun previewUsesTheSamePolicyWithASmallerDimensionLimit() {
        assertEquals(
            ImageSize(1200, 600),
            calculateTargetSize(
                4000,
                2000,
                maxDimension = ImageDecoder.PREVIEW_MAX_DIMENSION,
                maxPixels = ImageDecoder.MAX_PIXELS,
            ),
        )
    }

    @Test
    fun aspectRatioIsPreserved() {
        val size = calculateTargetSize(3000, 1500, maxDimension = 1200, maxPixels = 4_000_000)

        assertEquals(2f, size.width.toFloat() / size.height, 0.01f)
    }

    @Test
    fun degenerateDimensionsAreReportedAsAnInvalidImage() {
        try {
            calculateTargetSize(0, 100, maxDimension = 2048, maxPixels = 4_000_000)
            fail("Expected a zero-sized image to be rejected")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.IMAGE_DECODE_FAILED, exception.code)
            assertEquals(UiText(R.string.error_image_invalid), exception.text)
        }
    }
}
