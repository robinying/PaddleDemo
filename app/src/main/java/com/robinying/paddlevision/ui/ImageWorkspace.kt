package com.robinying.paddlevision.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.robinying.paddlevision.ImageDecoder
import com.robinying.paddlevision.R
import com.robinying.paddlevision.UiText
import com.robinying.paddlevision.VisionInferenceException
import com.robinying.paddlevision.VisionTask
import com.robinying.paddlevision.VisionUiState
import com.robinying.paddlevision.ui.theme.VisionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Accent colour that identifies the currently selected capability. */
@Composable
internal fun taskAccentColor(task: VisionTask): Color {
    val colors = VisionTheme.colors
    return when (task) {
        VisionTask.OCR -> colors.signalTeal
        VisionTask.OBJECT -> colors.amber
        VisionTask.FACE -> colors.faceAccent
    }
}

@Composable
internal fun ImageWorkspace(state: VisionUiState) {
    val colors = VisionTheme.colors
    val taskColor = taskAccentColor(state.selectedTask)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.paper),
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
            val imageUri = state.imageUri
            when {
                imageUri == null -> EmptyWorkspace(state.selectedTask)
                state.isRunning -> RunningWorkspace(state.selectedTask, imageUri)
                else -> SelectedWorkspace(state.selectedTask, imageUri)
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
    val colors = VisionTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("＋", color = colors.ink, fontSize = 34.sp, fontWeight = FontWeight.Light)
        Text(
            stringResource(R.string.choose_image_to_start, stringResource(task.titleRes)),
            color = colors.ink,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.local_processing), color = colors.slate, fontSize = 13.sp)
    }
}

@Composable
private fun BoxScope.SelectedWorkspace(task: VisionTask, imageUri: String) {
    val colors = VisionTheme.colors
    ImagePreview(imageUri = imageUri, task = task)
    WorkspaceCaption(
        modifier = Modifier.align(Alignment.BottomStart),
        title = stringResource(R.string.image_ready, stringResource(task.titleRes)),
        detail = stringResource(R.string.no_image_upload),
        badge = stringResource(R.string.ready),
        badgeColor = colors.signalTeal,
    )
}

@Composable
private fun BoxScope.RunningWorkspace(task: VisionTask, imageUri: String) {
    val colors = VisionTheme.colors
    ImagePreview(imageUri = imageUri, task = task, dimmed = true)
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.paper.copy(alpha = 0.94f))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = colors.amber, modifier = Modifier.size(38.dp))
        Text(
            stringResource(R.string.analyzing_task, stringResource(task.titleRes)),
            color = colors.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.on_device_model), color = colors.slate, fontSize = 13.sp)
    }
}

/**
 * Renders the selected image through the same decoding entry point the engine uses, so the
 * preview and the inference result always describe the same bounded bitmap. The bitmap is
 * recycled when the key changes or the composable leaves composition, and a decode failure
 * shows a message instead of an empty placeholder.
 */
@Composable
private fun ImagePreview(imageUri: String, task: VisionTask, dimmed: Boolean = false) {
    val colors = VisionTheme.colors
    val context = LocalContext.current
    var previewFailure by remember(imageUri) { mutableStateOf<UiText?>(null) }
    val preview by produceState<ImageBitmap?>(initialValue = null, imageUri) {
        value = withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodePreview(context, imageUri.toUri()).asImageBitmap()
            } catch (exception: VisionInferenceException) {
                previewFailure = exception.text
                null
            } catch (exception: Exception) {
                previewFailure = UiText(R.string.error_image_undecodable)
                null
            }
        }
        awaitDispose {
            value?.asAndroidBitmap()?.takeIf { bitmap -> !bitmap.isRecycled }?.recycle()
        }
    }
    val rendered = preview
    if (rendered != null) {
        Image(
            bitmap = rendered,
            contentDescription = stringResource(R.string.selected_image_preview, stringResource(task.titleRes)),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (dimmed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.ink.copy(alpha = 0.32f)),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.tealMist),
            contentAlignment = Alignment.Center,
        ) {
            previewFailure?.let { failure ->
                Text(
                    text = localizedText(failure),
                    modifier = Modifier.padding(16.dp),
                    color = colors.slate,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
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
    val colors = VisionTheme.colors
    Column(
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.paper.copy(alpha = 0.94f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(title, color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = colors.slate, fontSize = 11.sp)
    }
}
