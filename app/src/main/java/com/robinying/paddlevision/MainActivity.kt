package com.robinying.paddlevision

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    VisionScreen()
                }
            }
        }
    }
}

@Composable
private fun VisionScreen() {
    var state by remember { mutableStateOf(VisionUiState(message = NativeBridge.runtimeInfo())) }
    val activity = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        state = VisionUiReducer.reduce(state, VisionAction.ImageSelected(uri?.toString()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Paddle Vision", style = MaterialTheme.typography.headlineMedium)
        if (state.selectedTask == VisionTask.OCR) {
            Text("OCR 语言", style = MaterialTheme.typography.titleMedium)
            RowOfOcrLanguages(
                selected = state.ocrLanguage,
                onSelect = { language ->
                    state = VisionUiReducer.reduce(state, VisionAction.SelectOcrLanguage(language))
                },
            )
        }
        VisionTask.entries.forEach { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    Text(task.description)
                    OutlinedButton(
                        onClick = { state = VisionUiReducer.reduce(state, VisionAction.SelectTask(task)) },
                    ) {
                        Text(if (state.selectedTask == task) "已选择" else "选择")
                    }
                }
            }
        }
        Button(
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("从相册选择图片")
        }
        Button(
            enabled = state.canRun,
            onClick = {
                val requestState = state
                val imageUri = requestState.imageUri ?: return@Button
                state = VisionUiReducer.reduce(requestState, VisionAction.StartRun)
                CoroutineScope(Dispatchers.Main).launch {
                    val result = withContext(Dispatchers.Default) {
                        runInference(activity, requestState, Uri.parse(imageUri))
                    }
                    state = VisionUiReducer.reduce(state, VisionAction.RunFinished(result))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isRunning) "运行中" else "运行 ${state.selectedTask.title}")
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.message,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RowOfOcrLanguages(selected: OcrLanguage, onSelect: (OcrLanguage) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OcrLanguage.entries.forEach { language ->
            OutlinedButton(onClick = { onSelect(language) }) {
                Text(if (selected == language) "✓ ${language.title}" else language.title)
            }
        }
    }
}

private fun runInference(
    context: android.content.Context,
    state: VisionUiState,
    uri: Uri,
): String {
    return try {
        val decodedImage = ImageDecoder(context).decode(uri)
        try {
            val models = ModelStore(context).prepare()
            PaddleLiteEngine().run(
                task = state.selectedTask,
                bitmap = decodedImage.bitmap,
                modelDirectory = models.rootDirectory,
                ocrLanguage = state.ocrLanguage,
            ).summary()
        } finally {
            decodedImage.bitmap.recycle()
        }
    } catch (exception: VisionInferenceException) {
        "${exception.code}: ${exception.message}"
    } catch (exception: Exception) {
        "${VisionErrorCode.INFERENCE_FAILED}: ${exception.message ?: "未知错误"}"
    }
}
