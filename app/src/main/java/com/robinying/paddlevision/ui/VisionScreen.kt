package com.robinying.paddlevision.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinying.paddlevision.ImageSize
import com.robinying.paddlevision.OcrLanguage
import com.robinying.paddlevision.OcrTextBlock
import com.robinying.paddlevision.PixelBox
import com.robinying.paddlevision.R
import com.robinying.paddlevision.UiText
import com.robinying.paddlevision.VisionInferenceResult
import com.robinying.paddlevision.VisionIntent
import com.robinying.paddlevision.VisionTask
import com.robinying.paddlevision.VisionUiState
import com.robinying.paddlevision.ui.theme.VisionTheme

@Composable
fun VisionScreen(
    state: VisionUiState,
    onIntent: (VisionIntent) -> Unit,
) {
    Surface(color = VisionTheme.colors.mineral) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Header()
            TaskSelector(
                selected = state.selectedTask,
                enabled = !state.isRunning,
                onIntent = onIntent,
            )
            if (state.selectedTask == VisionTask.OCR) {
                LanguageSelector(
                    selected = state.ocrLanguage,
                    enabled = !state.isRunning,
                    onIntent = onIntent,
                )
            }
            ImageWorkspace(state = state)
            ActionArea(state = state, onIntent = onIntent)
            ResultPanel(state = state)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Header() {
    val colors = VisionTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                color = colors.ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Text(stringResource(R.string.app_subtitle), color = colors.slate, fontSize = 14.sp)
        }
        Text(
            text = stringResource(R.string.offline),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.tealMist)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = colors.signalTeal,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TaskSelector(
    selected: VisionTask,
    enabled: Boolean,
    onIntent: (VisionIntent) -> Unit,
) {
    val colors = VisionTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.analysis_mode))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VisionTask.entries.forEach { task ->
                val isSelected = task == selected
                val selectTaskDescription = stringResource(R.string.select_task, stringResource(task.titleRes))
                OutlinedButton(
                    enabled = enabled,
                    onClick = { onIntent(VisionIntent.SelectTask(task)) },
                    modifier = Modifier.semantics { contentDescription = selectTaskDescription },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) colors.signalTeal else colors.paper,
                        contentColor = if (isSelected) colors.paper else colors.ink,
                    ),
                    border = BorderStroke(1.dp, if (isSelected) colors.signalTeal else colors.line),
                ) {
                    Text(
                        text = stringResource(task.titleRes),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(stringResource(selected.descriptionRes), color = colors.slate, fontSize = 13.sp)
    }
}

/**
 * Renders only the OCR languages whose model is actually bundled, so a user can never pick a
 * language and then be sent into a run that is guaranteed to fail. Languages still on the
 * roadmap are described by the note instead of being offered as dead buttons.
 */
@Composable
private fun LanguageSelector(
    selected: OcrLanguage,
    enabled: Boolean,
    onIntent: (VisionIntent) -> Unit,
) {
    val colors = VisionTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.recognition_language))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OcrLanguage.packaged.forEach { language ->
                val selectedLanguage = language == selected
                OutlinedButton(
                    enabled = enabled,
                    onClick = { onIntent(VisionIntent.SelectOcrLanguage(language)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedLanguage) colors.tealMist else colors.paper,
                        contentColor = if (selectedLanguage) colors.signalTeal else colors.slate,
                    ),
                ) {
                    Text(
                        if (selectedLanguage) {
                            stringResource(R.string.selected_language, stringResource(language.titleRes))
                        } else {
                            stringResource(language.titleRes)
                        },
                    )
                }
            }
        }
        if (OcrLanguage.packaged.size < OcrLanguage.entries.size) {
            Text(stringResource(R.string.ocr_language_note), color = colors.slate, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActionArea(state: VisionUiState, onIntent: (VisionIntent) -> Unit) {
    val colors = VisionTheme.colors
    val pickImageDescription = stringResource(R.string.pick_image_accessibility)
    val runTaskText = stringResource(R.string.run_task, stringResource(state.selectedTask.titleRes))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            enabled = !state.isRunning,
            onClick = { onIntent(VisionIntent.PickImageClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = pickImageDescription },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.ink),
        ) {
            Text(
                stringResource(if (state.imageUri == null) R.string.pick_image else R.string.change_image),
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            enabled = state.canRun,
            onClick = { onIntent(VisionIntent.RunRequested) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = runTaskText },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.signalTeal,
                disabledContainerColor = colors.line,
                disabledContentColor = colors.slate,
            ),
        ) {
            Text(
                if (state.isRunning) stringResource(R.string.analyzing) else runTaskText,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun VisionScreenEmptyPreview() {
    VisionTheme { VisionScreen(state = VisionUiState(), onIntent = {}) }
}

@Preview(name = "Running", showBackground = true)
@Composable
private fun VisionScreenRunningPreview() {
    VisionTheme {
        VisionScreen(
            state = VisionUiState(selectedTask = VisionTask.OBJECT, isRunning = true, imageUri = "content://preview"),
            onIntent = {},
        )
    }
}

@Preview(name = "OcrResult", showBackground = true)
@Composable
private fun VisionScreenOcrResultPreview() {
    val result = VisionInferenceResult(
        task = VisionTask.OCR,
        imageSize = ImageSize(100, 100),
        elapsedMillis = 412,
        inferenceMillis = 380,
        textBlocks = listOf(OcrTextBlock("特价 45元", 0.93f, PixelBox(0f, 0f, 10f, 10f))),
    )
    VisionTheme {
        VisionScreen(
            state = VisionUiState(imageUri = "content://preview", result = result, message = result.summaryText()),
            onIntent = {},
        )
    }
}

@Preview(name = "FaceResult", showBackground = true)
@Composable
private fun VisionScreenFaceResultPreview() {
    VisionTheme {
        VisionScreen(
            state = VisionUiState(
                selectedTask = VisionTask.FACE,
                imageUri = "content://preview",
                message = UiText(R.string.message_face_privacy),
            ),
            onIntent = {},
        )
    }
}
