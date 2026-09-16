package com.robinying.paddlevision

import com.robinying.paddlevision.ui.DisplayRect
import com.robinying.paddlevision.ui.fitImageBounds
import com.robinying.paddlevision.ui.mapBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the coordinate mapping that places detection boxes on the workspace preview.
 *
 * These are pure functions, so the whole overlay geometry is pinned here rather than through a
 * screenshot: a wrong scale or a wrong letterbox offset produces boxes that look plausible in a
 * rendered image but are simply in the wrong place.
 */
class OverlayGeometryTest {
    @Test
    fun anImageWiderThanItsContainerIsLetterboxedTopAndBottom() {
        val bounds = fitImageBounds(containerWidth = 200f, containerHeight = 200f, imageWidth = 400, imageHeight = 200)

        assertNotNull(bounds)
        assertEquals(0f, bounds!!.left, DELTA)
        assertEquals(200f, bounds.right, DELTA)
        assertEquals("A 2:1 image leaves bars on the vertical axis", 50f, bounds.top, DELTA)
        assertEquals(150f, bounds.bottom, DELTA)
    }

    @Test
    fun anImageTallerThanItsContainerIsPillarboxedLeftAndRight() {
        val bounds = fitImageBounds(containerWidth = 200f, containerHeight = 200f, imageWidth = 200, imageHeight = 400)

        assertNotNull(bounds)
        assertEquals(50f, bounds!!.left, DELTA)
        assertEquals(150f, bounds.right, DELTA)
        assertEquals(0f, bounds.top, DELTA)
        assertEquals(200f, bounds.bottom, DELTA)
    }

    @Test
    fun matchingAspectsFillTheContainerExactly() {
        val bounds = fitImageBounds(containerWidth = 300f, containerHeight = 150f, imageWidth = 800, imageHeight = 400)

        assertNotNull(bounds)
        assertEquals(0f, bounds!!.left, DELTA)
        assertEquals(0f, bounds.top, DELTA)
        assertEquals(300f, bounds.right, DELTA)
        assertEquals(150f, bounds.bottom, DELTA)
    }

    @Test
    fun degenerateContainersOrImagesHaveNoBounds() {
        assertNull(fitImageBounds(0f, 200f, 400, 200))
        assertNull(fitImageBounds(200f, 0f, 400, 200))
        assertNull(fitImageBounds(200f, 200f, 0, 200))
        assertNull(fitImageBounds(200f, 200f, 400, 0))
    }

    @Test
    fun aFullFrameBoxCoversTheWholeImageBounds() {
        val bounds = requireNotNull(fitImageBounds(200f, 200f, 400, 200))

        val mapped = bounds.mapBox(PixelBox(0f, 0f, 400f, 200f), ImageSize(400, 200))

        assertEquals(bounds.left, mapped.left, DELTA)
        assertEquals(bounds.top, mapped.top, DELTA)
        assertEquals(bounds.right, mapped.right, DELTA)
        assertEquals(bounds.bottom, mapped.bottom, DELTA)
    }

    @Test
    fun aQuarterFrameBoxMapsToAQuarterOfTheImageBounds() {
        val bounds = requireNotNull(fitImageBounds(containerWidth = 200f, containerHeight = 200f, imageWidth = 400, imageHeight = 400))

        val mapped = bounds.mapBox(PixelBox(0f, 0f, 200f, 200f), ImageSize(400, 400))

        assertEquals(0f, mapped.left, DELTA)
        assertEquals(0f, mapped.top, DELTA)
        assertEquals(100f, mapped.width, DELTA)
        assertEquals(100f, mapped.height, DELTA)
    }

    /**
     * The load-bearing property of the whole overlay: the engine reports boxes against the inference
     * decode (up to 2048 px) while the workspace shows a preview decode (up to 1200 px). Both keep
     * the source aspect ratio, so the same region has to land on the same spot either way. Mapping by
     * pixel ratio instead of by fraction would place every box at the wrong scale.
     */
    @Test
    fun theSameRegionMapsIdenticallyWhateverSizeTheSourceDecodeWas() {
        val bounds = requireNotNull(fitImageBounds(200f, 200f, 400, 200))

        val fromInferenceDecode = bounds.mapBox(PixelBox(400f, 200f, 800f, 400f), ImageSize(1600, 800))
        val fromPreviewDecode = bounds.mapBox(PixelBox(300f, 150f, 600f, 300f), ImageSize(1200, 600))

        assertEquals(fromInferenceDecode.left, fromPreviewDecode.left, DELTA)
        assertEquals(fromInferenceDecode.top, fromPreviewDecode.top, DELTA)
        assertEquals(fromInferenceDecode.width, fromPreviewDecode.width, DELTA)
        assertEquals(fromInferenceDecode.height, fromPreviewDecode.height, DELTA)
    }

    /** A box reaching past the source must not paint over the letterbox bars. */
    @Test
    fun aBoxExtendingPastTheSourceIsClampedToTheImageBounds() {
        val bounds = requireNotNull(fitImageBounds(200f, 200f, 400, 200))

        val mapped = bounds.mapBox(PixelBox(-50f, -50f, 500f, 500f), ImageSize(400, 200))

        assertTrue("left must stay inside the image", mapped.left >= bounds.left - DELTA)
        assertTrue("top must stay inside the image", mapped.top >= bounds.top - DELTA)
        assertTrue("right must stay inside the image", mapped.right <= bounds.right + DELTA)
        assertTrue("bottom must stay inside the image", mapped.bottom <= bounds.bottom + DELTA)
        assertEquals(bounds.left, mapped.left, DELTA)
        assertEquals(bounds.right, mapped.right, DELTA)
    }

    @Test
    fun anInvertedBoxIsNormalizedRatherThanDrawnInsideOut() {
        val bounds = requireNotNull(fitImageBounds(200f, 200f, 400, 200))

        val inverted = bounds.mapBox(PixelBox(200f, 100f, 0f, 0f), ImageSize(400, 200))
        val upright = bounds.mapBox(PixelBox(0f, 0f, 200f, 100f), ImageSize(400, 200))

        assertEquals(upright.left, inverted.left, DELTA)
        assertEquals(upright.top, inverted.top, DELTA)
        assertEquals(upright.right, inverted.right, DELTA)
        assertEquals(upright.bottom, inverted.bottom, DELTA)
        assertTrue("A normalized box must have a positive extent", inverted.width > 0f && inverted.height > 0f)
    }

    /** A zero-sized source would otherwise divide by zero and hand NaN to the canvas. */
    @Test
    fun aBoxAgainstADegenerateSourceCollapsesInsteadOfProducingNaN() {
        val bounds = DisplayRect(0f, 0f, 100f, 100f)

        val mapped = bounds.mapBox(PixelBox(0f, 0f, 10f, 10f), ImageSize(0, 0))

        assertEquals(0f, mapped.width, DELTA)
        assertEquals(0f, mapped.height, DELTA)
        assertTrue("width must be a real number", !mapped.width.isNaN())
    }

    @Test
    fun mappedGeometryIsFiniteForARealisticDetection() {
        val bounds = requireNotNull(
            fitImageBounds(containerWidth = 660f, containerHeight = 440f, imageWidth = 3000, imageHeight = 2000),
        )

        val mapped = bounds.mapBox(PixelBox(110f, 185f, 350f, 545f), ImageSize(3000, 2000))

        listOf(mapped.left, mapped.top, mapped.right, mapped.bottom).forEach { coordinate ->
            assertTrue("$coordinate must be a real number", coordinate.isFinite())
        }
        assertTrue("The mapped dog box must keep a positive extent", mapped.width > 0f && mapped.height > 0f)
        assertTrue("The box must stay inside the image bounds", mapped.left >= bounds.left && mapped.right <= bounds.right)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
