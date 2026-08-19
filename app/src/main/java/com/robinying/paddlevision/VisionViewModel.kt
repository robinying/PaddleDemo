package com.robinying.paddlevision

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Owns Vision screen state and applies the UDF intent-to-state transition. */
class VisionViewModel(
    private val inferenceUseCase: VisionInferenceUseCase,
    initialMessage: String = "请选择能力并从相册选择图片",
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(VisionUiState(message = initialMessage))
    private val effectChannel = Channel<VisionEffect>(Channel.BUFFERED)

    val uiState: StateFlow<VisionUiState> = mutableUiState.asStateFlow()
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: VisionIntent) {
        when (intent) {
            VisionIntent.PickImageClicked -> effectChannel.trySend(VisionEffect.OpenPhotoPicker)
            VisionIntent.RunRequested -> runInference()
            else -> mutableUiState.value = VisionUiReducer.reduce(mutableUiState.value, intent)
        }
    }

    private fun runInference() {
        val request = mutableUiState.value
        val imageUri = request.imageUri ?: return
        if (request.isRunning) {
            return
        }
        mutableUiState.value = VisionUiReducer.startRun(request)
        viewModelScope.launch {
            try {
                val result = inferenceUseCase.run(
                    task = request.selectedTask,
                    ocrLanguage = request.ocrLanguage,
                    imageUri = imageUri,
                )
                mutableUiState.value = VisionUiReducer.runSucceeded(mutableUiState.value, result)
            } catch (exception: VisionInferenceException) {
                mutableUiState.value = VisionUiReducer.runFailed(
                    mutableUiState.value,
                    "${exception.code}: ${exception.message}",
                )
            } catch (exception: Exception) {
                mutableUiState.value = VisionUiReducer.runFailed(
                    mutableUiState.value,
                    "${VisionErrorCode.INFERENCE_FAILED}: ${exception.message ?: "未知错误"}",
                )
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(VisionViewModel::class.java))
                return VisionViewModel(
                    inferenceUseCase = LocalVisionInferenceUseCase(context.applicationContext),
                    initialMessage = NativeBridge.runtimeInfo(),
                ) as T
            }
        }
    }
}
