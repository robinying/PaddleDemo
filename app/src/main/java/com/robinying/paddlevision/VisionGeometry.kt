package com.robinying.paddlevision

import kotlin.math.max
import kotlin.math.min

data class ImageSize(val width: Int, val height: Int)

data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class PixelBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

object VisionGeometry {
    fun normalizedToPixels(box: NormalizedBox, imageSize: ImageSize): PixelBox {
        val left = box.left.coerceIn(0f, 1f) * imageSize.width
        val top = box.top.coerceIn(0f, 1f) * imageSize.height
        val right = box.right.coerceIn(0f, 1f) * imageSize.width
        val bottom = box.bottom.coerceIn(0f, 1f) * imageSize.height
        return PixelBox(
            left = min(left, right),
            top = min(top, bottom),
            right = max(left, right),
            bottom = max(top, bottom),
        )
    }

    fun iou(first: PixelBox, second: PixelBox): Float {
        val intersectionWidth = max(0f, min(first.right, second.right) - max(first.left, second.left))
        val intersectionHeight = max(0f, min(first.bottom, second.bottom) - max(first.top, second.top))
        val intersection = intersectionWidth * intersectionHeight
        val union = first.width * first.height + second.width * second.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    fun nonMaximumSuppression(
        boxes: List<PixelBox>,
        scores: List<Float>,
        threshold: Float,
    ): List<Int> {
        require(boxes.size == scores.size)
        require(threshold in 0f..1f)
        val candidates = scores.indices
            .filter { scores[it].isFinite() }
            .sortedByDescending { scores[it] }
            .toMutableList()
        val kept = mutableListOf<Int>()
        while (candidates.isNotEmpty()) {
            val selected = candidates.removeAt(0)
            kept += selected
            candidates.removeAll { VisionGeometry.iou(boxes[selected], boxes[it]) > threshold }
        }
        return kept
    }
}
