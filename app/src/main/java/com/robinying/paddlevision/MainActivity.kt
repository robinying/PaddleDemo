package com.robinying.paddlevision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robinying.paddlevision.ui.VisionScreen
import com.robinying.paddlevision.ui.theme.VisionTheme

/** Hosts the vision screen. Screen composition lives under `ui/`. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisionTheme {
                VisionRoute()
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
