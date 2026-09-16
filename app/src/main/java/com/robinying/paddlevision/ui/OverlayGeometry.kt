package com.robinying.paddlevision.ui

import com.robinying.paddlevision.ImageSize
import com.robinying.paddlevision.PixelBox

/**
 * A rectangle in composable pixels.
 *
 * Result geometry arrives as a [PixelBox] in the *decoded inference bitmap's* pixel space, while the
 * workspace draws a separately decoded preview bitmap. The two decodes bound the same source image
 * with the same aspect-preserving policy, so they agree on shape but not on absolute size. Every box
 * is therefore mapped through normalized coordinates rather than by a pixel ratio — see [mapBox].
 */
internal data class DisplayRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * The rect a `ContentScale.Fit` image occupies inside its container: uniformly scaled to fit and
 * then centred, which leaves letterbox bars on one axis unless the two aspects match exactly.
 *
 * Returns null when either side is degenerate, so callers skip drawing instead of dividing by zero.
 */
internal fun fitImageBounds(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
): DisplayRect? {
    if (containerWidth <= 0f || containerHeight <= 0f) return null
    if (imageWidth <= 0 || imageHeight <= 0) return null
    val scale = minOf(containerWidth / imageWidth, containerHeight / imageHeight)
    val displayedWidth = imageWidth * scale
    val displayedHeight = imageHeight * scale
    val left = (containerWidth - displayedWidth) / 2f
    val top = (containerHeight - displayedHeight) / 2f
    return DisplayRect(left, top, left + displayedWidth, top + displayedHeight)
}

/**
 * Maps [box], expressed in [sourceSize] pixel space, onto [this] image bounds.
 *
 * Normalizing by [sourceSize] first is what makes the inference bitmap and the preview bitmap
 * interchangeable here: both preserve the source aspect ratio, so a box covers the same fraction of
 * the image whichever decode produced the numbers.
 *
 * The result is clamped to [this] so a malformed box can never paint over the letterbox bars. A box
 * that lies entirely outside the image collapses to a zero-sized rect, which callers skip.
 */
internal fun DisplayRect.mapBox(box: PixelBox, sourceSize: ImageSize): DisplayRect {
    if (sourceSize.width <= 0 || sourceSize.height <= 0) return DisplayRect(left, top, left, top)
    val boxLeft = box.left.coerceIn(0f, sourceSize.width.toFloat())
    val boxTop = box.top.coerceIn(0f, sourceSize.height.toFloat())
    val boxRight = box.right.coerceIn(0f, sourceSize.width.toFloat())
    val boxBottom = box.bottom.coerceIn(0f, sourceSize.height.toFloat())
    val scaleX = width / sourceSize.width
    val scaleY = height / sourceSize.height
    val mappedLeft = left + minOf(boxLeft, boxRight) * scaleX
    val mappedTop = top + minOf(boxTop, boxBottom) * scaleY
    val mappedRight = left + maxOf(boxLeft, boxRight) * scaleX
    val mappedBottom = top + maxOf(boxTop, boxBottom) * scaleY
    return DisplayRect(
        left = mappedLeft.coerceIn(left, right),
        top = mappedTop.coerceIn(top, bottom),
        right = mappedRight.coerceIn(left, right),
        bottom = mappedBottom.coerceIn(top, bottom),
    )
}
