package com.robinying.paddlevision

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Paddle Vision", style = MaterialTheme.typography.headlineMedium)
        if (state.selectedTask == VisionTask.OCR) {
            Text("OCR 语言", style = MaterialTheme.typography.titleMedium)
            RowOfOcrLanguages(
                selected = state.ocrLanguage,
                onSelect = { language -> onIntent(VisionIntent.SelectOcrLanguage(language)) },
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
                    OutlinedButton(onClick = { onIntent(VisionIntent.SelectTask(task)) }) {
                        Text(if (state.selectedTask == task) "已选择" else "选择")
                    }
                }
            }
        }
        Button(
            onClick = { onIntent(VisionIntent.PickImageClicked) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("从相册选择图片")
        }
        Button(
            enabled = state.canRun,
            onClick = { onIntent(VisionIntent.RunRequested) },
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
