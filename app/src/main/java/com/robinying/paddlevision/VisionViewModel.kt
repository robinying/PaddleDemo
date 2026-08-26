package com.robinying.paddlevision

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
    private var inferenceJob: Job? = null
    private var activeRequestId = 0L

    val uiState: StateFlow<VisionUiState> = mutableUiState.asStateFlow()
    val effects = effectChannel.receiveAsFlow()

    fun onIntent(intent: VisionIntent) {
        when (intent) {
            VisionIntent.PickImageClicked -> {
                invalidateActiveInference()
                mutableUiState.value = mutableUiState.value.copy(
                    isRunning = false,
                    result = null,
                    message = "请选择一张图片",
                )
                effectChannel.trySend(VisionEffect.OpenPhotoPicker)
            }
            VisionIntent.RunRequested -> runInference()
            else -> {
                invalidateActiveInference()
                mutableUiState.value = VisionUiReducer.reduce(mutableUiState.value, intent)
            }
        }
    }

    private fun runInference() {
        val request = mutableUiState.value
        val imageUri = request.imageUri ?: return
        if (request.isRunning) {
            return
        }
        val requestId = ++activeRequestId
        mutableUiState.value = VisionUiReducer.startRun(request)
        inferenceJob = viewModelScope.launch {
            try {
                val result = inferenceUseCase.run(
                    task = request.selectedTask,
                    ocrLanguage = request.ocrLanguage,
                    imageUri = imageUri,
                )
                if (isActiveRequest(requestId)) {
                    mutableUiState.value = VisionUiReducer.runSucceeded(mutableUiState.value, result)
                }
            } catch (exception: VisionInferenceException) {
                if (isActiveRequest(requestId)) {
                    mutableUiState.value = VisionUiReducer.runFailed(
                        mutableUiState.value,
                        "${exception.code}: ${exception.message}",
                    )
                }
            } catch (exception: Exception) {
                if (isActiveRequest(requestId)) {
                    mutableUiState.value = VisionUiReducer.runFailed(
                        mutableUiState.value,
                        "${VisionErrorCode.INFERENCE_FAILED}: ${exception.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    private fun invalidateActiveInference() {
        activeRequestId++
        inferenceJob?.cancel()
        inferenceJob = null
    }

    private fun isActiveRequest(requestId: Long): Boolean = requestId == activeRequestId

    override fun onCleared() {
        invalidateActiveInference()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(VisionViewModel::class.java))
                return VisionViewModel(
                    inferenceUseCase = LocalVisionInferenceUseCase(context.applicationContext),
                    initialMessage = NativeBridge.bridgeInfo(),
                ) as T
            }
        }
    }
}
