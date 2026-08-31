package com.robinying.paddlevision

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF101B2D)
private val Mineral = Color(0xFFF2F5F7)
private val Paper = Color(0xFFFFFFFF)
private val SignalTeal = Color(0xFF087E8B)
private val TealMist = Color(0xFFDDF4F5)
private val Amber = Color(0xFFE59F23)
private val Slate = Color(0xFF64748B)
private val Line = Color(0xFFD7E0E6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(color = Mineral) {
                    VisionRoute()
                }
            }
        }
    }
}

@Composable
private fun VisionRoute() {
    val context = LocalContext.current
    val viewModel: VisionViewModel = viewModel(factory = VisionViewModel.factory(context))
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onIntent(VisionIntent.ImageSelected(uri?.toString()))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is VisionEffect.OpenPhotoPicker) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
    VisionScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
private fun VisionScreen(
    state: VisionUiState,
    onIntent: (VisionIntent) -> Unit,
) {
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

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Text(stringResource(R.string.app_subtitle), color = Slate, fontSize = 14.sp)
        }
        Text(
            text = stringResource(R.string.offline),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(TealMist)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = SignalTeal,
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
                        containerColor = if (isSelected) SignalTeal else Paper,
                        contentColor = if (isSelected) Paper else Ink,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) SignalTeal else Line,
                    ),
                ) {
                    Text(
                        text = stringResource(task.titleRes),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(stringResource(selected.descriptionRes), color = Slate, fontSize = 13.sp)
    }
}

@Composable
private fun LanguageSelector(
    selected: OcrLanguage,
    enabled: Boolean,
    onIntent: (VisionIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.recognition_language))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OcrLanguage.entries.forEach { language ->
                val selectedLanguage = language == selected
                OutlinedButton(
                    enabled = enabled,
                    onClick = { onIntent(VisionIntent.SelectOcrLanguage(language)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedLanguage) TealMist else Paper,
                        contentColor = if (selectedLanguage) SignalTeal else Slate,
                    ),
                ) {
                    Text(if (selectedLanguage) stringResource(R.string.selected_language, stringResource(language.titleRes)) else stringResource(language.titleRes))
                }
            }
        }
    }
}

@Composable
private fun ImageWorkspace(state: VisionUiState) {
    val taskColor = when (state.selectedTask) {
        VisionTask.OCR -> SignalTeal
        VisionTask.OBJECT -> Amber
        VisionTask.FACE -> Color(0xFF6950A1)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(12.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, taskColor, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.imageUri == null -> EmptyWorkspace(state.selectedTask)
                state.isRunning -> RunningWorkspace(state.selectedTask, state.imageUri)
                else -> SelectedWorkspace(state.selectedTask, state.imageUri)
            }
            ViewfinderCorners(taskColor)
        }
    }
}

@Composable
private fun BoxScope.ViewfinderCorners(color: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(10.dp)
            .size(18.dp)
            .border(3.dp, color),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(10.dp)
            .size(18.dp)
            .border(3.dp, color),
    )
}

@Composable
private fun EmptyWorkspace(task: VisionTask) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("＋", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Light)
        Text(stringResource(R.string.choose_image_to_start, stringResource(task.titleRes)), color = Ink, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.local_processing), color = Slate, fontSize = 13.sp)
    }
}

@Composable
private fun BoxScope.SelectedWorkspace(task: VisionTask, imageUri: String) {
    ImagePreview(imageUri = imageUri, task = task)
    WorkspaceCaption(
        modifier = Modifier.align(Alignment.BottomStart),
        title = stringResource(R.string.image_ready, stringResource(task.titleRes)),
        detail = stringResource(R.string.no_image_upload),
        badge = stringResource(R.string.ready),
        badgeColor = SignalTeal,
    )
}

@Composable
private fun BoxScope.RunningWorkspace(task: VisionTask, imageUri: String) {
    ImagePreview(imageUri = imageUri, task = task, dimmed = true)
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(16.dp))
            .background(Paper.copy(alpha = 0.94f))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = Amber, modifier = Modifier.size(38.dp))
        Text(stringResource(R.string.analyzing_task, stringResource(task.titleRes)), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.on_device_model), color = Slate, fontSize = 13.sp)
    }
}

@Composable
private fun ImagePreview(imageUri: String, task: VisionTask, dimmed: Boolean = false) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(initialValue = null, imageUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, Uri.parse(imageUri))
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val largestSide = maxOf(info.size.width, info.size.height)
                    if (largestSide > 1200) {
                        val scale = 1200f / largestSide
                        decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                    }
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    if (preview != null) {
        androidx.compose.foundation.Image(
            bitmap = preview!!,
            contentDescription = stringResource(R.string.selected_image_preview, stringResource(task.titleRes)),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (dimmed) Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.32f)))
    } else {
        Box(Modifier.fillMaxSize().background(TealMist))
    }
}

@Composable
private fun WorkspaceCaption(
    modifier: Modifier = Modifier,
    title: String,
    detail: String,
    badge: String,
    badgeColor: Color,
) {
    Column(
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Paper.copy(alpha = 0.94f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Slate, fontSize = 11.sp)
    }
}

@Composable
private fun ActionArea(state: VisionUiState, onIntent: (VisionIntent) -> Unit) {
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
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) {
            Text(stringResource(if (state.imageUri == null) R.string.pick_image else R.string.change_image), fontWeight = FontWeight.Bold)
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
                containerColor = SignalTeal,
                disabledContainerColor = Line,
                disabledContentColor = Slate,
            ),
        ) {
            Text(if (state.isRunning) stringResource(R.string.analyzing) else runTaskText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultPanel(state: VisionUiState) {
    val message = localizedText(state.message)
    val resultDescription = stringResource(R.string.analysis_result_accessibility, message)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = resultDescription },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(stringResource(R.string.analysis_result))
            state.result?.let { result -> ResultMetrics(result) }
            Text(message, color = Ink, fontSize = 15.sp)
            if (state.result?.task == VisionTask.FACE || state.selectedTask == VisionTask.FACE) {
                Text(
                    stringResource(R.string.face_privacy_note),
                    color = Slate,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ResultMetrics(result: VisionInferenceResult) {
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
            Text(label, color = Slate, fontSize = 12.sp)
            Text(count.toString(), color = SignalTeal, fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.end_to_end_time), color = Slate, fontSize = 12.sp)
            Text(
                "${result.elapsedMillis} ms",
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun localizedText(text: UiText): String = stringResource(text.resourceId, *text.args.toTypedArray())

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Slate,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
    )
}
