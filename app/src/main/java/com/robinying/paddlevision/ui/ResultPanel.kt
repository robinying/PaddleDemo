package com.robinying.paddlevision.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinying.paddlevision.R
import com.robinying.paddlevision.VisionInferenceResult
import com.robinying.paddlevision.VisionTask
import com.robinying.paddlevision.VisionUiState
import com.robinying.paddlevision.objectCategoryLabel
import com.robinying.paddlevision.ui.theme.VisionTheme
import java.util.Locale

internal data class ResultEntry(val label: String, val confidence: Float)

@Composable
internal fun ResultPanel(state: VisionUiState) {
    val colors = VisionTheme.colors
    val context = LocalContext.current
    val objectLabels = stringArrayResource(R.array.object_categories).toList()
    val message = localizedText(state.message)
    val resultDescription = stringResource(R.string.analysis_result_accessibility, message)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = resultDescription },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.paper),
        border = BorderStroke(1.dp, colors.line),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(stringResource(R.string.analysis_result))
            state.result?.let { result ->
                ResultMetrics(result)
                when (result.task) {
                    VisionTask.OCR -> ResultEntries(
                        heading = stringResource(R.string.recognized_text),
                        emptyMessage = stringResource(R.string.no_text_recognized),
                        entries = result.textBlocks.map { ResultEntry(it.text, it.confidence) },
                    )
                    VisionTask.OBJECT -> ResultEntries(
                        heading = stringResource(R.string.detected_objects),
                        emptyMessage = stringResource(R.string.no_objects_detected),
                        entries = result.detections.map { detection ->
                            ResultEntry(
                                label = objectCategoryLabel(detection.categoryId, objectLabels) { unknownId ->
                                    context.getString(R.string.object_unknown_category, unknownId)
                                },
                                confidence = detection.confidence,
                            )
                        },
                    )
                    VisionTask.FACE -> ResultEntries(
                        heading = stringResource(R.string.detected_faces),
                        emptyMessage = stringResource(R.string.no_faces_detected),
                        entries = result.faces.mapIndexed { index, face ->
                            ResultEntry(stringResource(R.string.face_number, index + 1), face.confidence)
                        },
                    )
                }
            }
            Text(message, color = colors.ink, fontSize = 15.sp)
            if (state.result?.task == VisionTask.FACE || state.selectedTask == VisionTask.FACE) {
                Text(
                    stringResource(R.string.face_privacy_note),
                    color = colors.slate,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ResultEntries(
    heading: String,
    emptyMessage: String,
    entries: List<ResultEntry>,
) {
    val colors = VisionTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(heading)
        if (entries.isEmpty()) {
            Text(emptyMessage, color = colors.slate, fontSize = 14.sp)
        } else {
            entries.forEach { entry ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.mineral),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.label,
                            modifier = Modifier.weight(1f),
                            color = colors.ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(
                                R.string.ocr_confidence,
                                String.format(Locale.getDefault(), "%.0f%%", entry.confidence * 100),
                            ),
                            color = colors.signalTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultMetrics(result: VisionInferenceResult) {
    val colors = VisionTheme.colors
    val (label, count) = when (result.task) {
        VisionTask.OCR -> stringResource(R.string.text_blocks) to result.textBlocks.size
        VisionTask.OBJECT -> stringResource(R.string.detected_objects) to result.detections.size
        VisionTask.FACE -> stringResource(R.string.detected_faces) to result.faces.size
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(label, color = colors.slate, fontSize = 12.sp)
            Text(count.toString(), color = colors.signalTeal, fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.end_to_end_time), color = colors.slate, fontSize = 12.sp)
            Text(
                "${result.elapsedMillis} ms",
                color = colors.ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = VisionTheme.colors.slate,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
    )
}
