package com.robinying.paddlevision

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

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
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Header()
        TaskSelector(selected = state.selectedTask, onIntent = onIntent)
        if (state.selectedTask == VisionTask.OCR) {
            LanguageSelector(selected = state.ocrLanguage, onIntent = onIntent)
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
                text = "PADDLE VISION",
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Text("端侧图像分析工作台", color = Slate, fontSize = 14.sp)
        }
        Text(
            text = "本地离线",
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
private fun TaskSelector(selected: VisionTask, onIntent: (VisionIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("分析模式")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VisionTask.entries.forEach { task ->
                val isSelected = task == selected
                OutlinedButton(
                    onClick = { onIntent(VisionIntent.SelectTask(task)) },
                    modifier = Modifier.semantics { contentDescription = "选择${task.title}" },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isSelected) Paper else Ink,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) SignalTeal else Line,
                    ),
                ) {
                    Text(
                        text = task.title,
                        modifier = if (isSelected) Modifier
                            .background(SignalTeal)
                            .padding(horizontal = 2.dp) else Modifier,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(selected.description, color = Slate, fontSize = 13.sp)
    }
}

@Composable
private fun LanguageSelector(selected: OcrLanguage, onIntent: (VisionIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("识别语言")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OcrLanguage.entries.forEach { language ->
                val selectedLanguage = language == selected
                OutlinedButton(
                    onClick = { onIntent(VisionIntent.SelectOcrLanguage(language)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (selectedLanguage) SignalTeal else Slate,
                    ),
                ) {
                    Text(if (selectedLanguage) "✓ ${language.title}" else language.title)
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
                .padding(16.dp)
                .border(2.dp, taskColor, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ViewfinderCorners(taskColor)
            when {
                state.isRunning -> RunningWorkspace(state.selectedTask)
                state.imageUri == null -> EmptyWorkspace(state.selectedTask)
                else -> SelectedWorkspace(state.selectedTask)
            }
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
        Text("选择一张图片开始${task.title}", color = Ink, fontWeight = FontWeight.Bold)
        Text("图片仅在此设备处理", color = Slate, fontSize = 13.sp)
    }
}

@Composable
private fun SelectedWorkspace(task: VisionTask) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已准备", color = SignalTeal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("图片已就绪，可运行${task.title}", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("不会上传或保存图片内容", color = Slate, fontSize = 13.sp)
    }
}

@Composable
private fun RunningWorkspace(task: VisionTask) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = Amber, modifier = Modifier.size(38.dp))
        Text("正在分析${task.title}", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("端侧模型运行中", color = Slate, fontSize = 13.sp)
    }
}

@Composable
private fun ActionArea(state: VisionUiState, onIntent: (VisionIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onIntent(VisionIntent.PickImageClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = "从相册选择图片" },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) {
            Text(if (state.imageUri == null) "选择图片" else "更换图片", fontWeight = FontWeight.Bold)
        }
        Button(
            enabled = state.canRun,
            onClick = { onIntent(VisionIntent.RunRequested) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = "运行${state.selectedTask.title}" },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SignalTeal,
                disabledContainerColor = Line,
                disabledContentColor = Slate,
            ),
        ) {
            Text(if (state.isRunning) "分析中…" else "运行 ${state.selectedTask.title}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultPanel(state: VisionUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "分析结果：${state.message}" },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("分析结果")
            state.result?.let { result -> ResultMetrics(result) }
            Text(state.message, color = Ink, fontSize = 15.sp)
            if (state.result?.task == VisionTask.FACE || state.selectedTask == VisionTask.FACE) {
                Text(
                    "人脸检测仅显示位置与置信度，不识别身份。",
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
        VisionTask.OCR -> "文本块" to result.textBlocks.size
        VisionTask.OBJECT -> "检测目标" to result.detections.size
        VisionTask.FACE -> "检测人脸" to result.faces.size
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
            Text("端到端耗时", color = Slate, fontSize = 12.sp)
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
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Slate,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
    )
}
