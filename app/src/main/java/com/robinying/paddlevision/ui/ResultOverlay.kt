package com.robinying.paddlevision.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinying.paddlevision.PixelBox
import com.robinying.paddlevision.R
import com.robinying.paddlevision.VisionInferenceResult
import com.robinying.paddlevision.VisionTask
import com.robinying.paddlevision.objectCategoryLabel
import com.robinying.paddlevision.ui.theme.VisionTheme

/** Identifies the overlay canvas in tests. */
internal const val RESULT_OVERLAY_TEST_TAG = "result_overlay"

/** One box to draw, with the chip text it carries — or null for boxes that stay unlabelled. */
private data class OverlayEntry(val box: PixelBox, val label: String?)

/**
 * Draws the detection geometry on top of the workspace preview.
 *
 * The overlay is decorative: every box it draws is already listed in [ResultPanel], which is the
 * accessible surface, so the canvas clears its own semantics rather than announcing the same result
 * twice to a screen reader.
 *
 * Boxes are positioned through [fitImageBounds] and [mapBox], which mirror the `ContentScale.Fit`
 * layout the preview image uses. They only line up because [ImageWorkspace] renders the preview with
 * that same scale mode — changing one without the other silently misplaces every box.
 */
@Composable
internal fun ResultOverlay(result: VisionInferenceResult, modifier: Modifier = Modifier) {
    val colors = VisionTheme.colors
    val accent = taskAccentColor(result.task)
    val context = LocalContext.current
    val objectLabels = stringArrayResource(R.array.object_categories).toList()
    val entries = result.overlayEntries(context.getString(R.string.object_unknown_category), objectLabels)
    if (entries.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val strokeWidth = with(density) { 2.dp.toPx() }
    val chipPadding = with(density) { 4.dp.toPx() }
    // Below these sizes a chip would cover the region it labels, so the box is drawn bare.
    val labelledMinWidth = with(density) { 44.dp.toPx() }
    val labelledMinHeight = with(density) { 20.dp.toPx() }
    val chipTextStyle = TextStyle(color = colors.paper, fontSize = 10.sp, fontWeight = FontWeight.Bold)

    Canvas(
        modifier = modifier
            .testTag(RESULT_OVERLAY_TEST_TAG)
            .clearAndSetSemantics {},
    ) {
        val bounds = fitImageBounds(
            containerWidth = size.width,
            containerHeight = size.height,
            imageWidth = result.imageSize.width,
            imageHeight = result.imageSize.height,
        ) ?: return@Canvas
        entries.forEach { entry ->
            val rect = bounds.mapBox(entry.box, result.imageSize)
            if (rect.width <= 0f || rect.height <= 0f) return@forEach
            drawRect(
                color = accent,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(width = strokeWidth),
            )
            val label = entry.label ?: return@forEach
            if (rect.width < labelledMinWidth || rect.height < labelledMinHeight) return@forEach
            val layout = textMeasurer.measure(AnnotatedString(label), style = chipTextStyle)
            val chipWidth = layout.size.width + chipPadding * 2
            val chipHeight = layout.size.height + chipPadding
            // Prefer sitting above the box; fall back to its top edge when that would leave the image.
            val chipTop = (rect.top - chipHeight).coerceAtLeast(bounds.top)
            val chipLeft = rect.left.coerceIn(bounds.left, (bounds.right - chipWidth).coerceAtLeast(bounds.left))
            drawRect(
                color = accent,
                topLeft = Offset(chipLeft, chipTop),
                size = Size(chipWidth, chipHeight),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(chipLeft + chipPadding, chipTop + chipPadding / 2f),
            )
        }
    }
}

/**
 * Builds the draw list for [result].
 *
 * Labels are drawn per task so the overlay stays readable: object detections carry the localized
 * category and confidence, faces carry only their index (never an identity, matching the privacy
 * contract), and OCR regions stay bare because their text is already enumerated in the panel and up
 * to [PixelBox]-count regions would otherwise overlap into an unreadable block.
 */
private fun VisionInferenceResult.overlayEntries(
    unknownCategoryFormat: String,
    objectLabels: List<String>,
): List<OverlayEntry> = when (task) {
    VisionTask.OCR -> textBlocks.map { OverlayEntry(it.boundingBox, label = null) }
    VisionTask.OBJECT -> detections.map { detection ->
        val categoryLabel = objectCategoryLabel(detection.categoryId, objectLabels) { unknownId ->
            String.format(unknownCategoryFormat, unknownId)
        }
        OverlayEntry(detection.boundingBox, "$categoryLabel ${confidencePercent(detection.confidence)}")
    }
    VisionTask.FACE -> faces.mapIndexed { index, face ->
        OverlayEntry(face.boundingBox, "#${index + 1}")
    }
}
