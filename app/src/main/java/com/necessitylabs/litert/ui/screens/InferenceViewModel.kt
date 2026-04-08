/**
 * InferenceViewModel.kt — ViewModel for InferenceScreen (classical ML demo).
 *
 * Drives model loading and inference via the InferenceEngine interface injected by Hilt.
 * All engine calls are dispatched to Dispatchers.Default — the ViewModel never blocks the
 * main thread. UI state is exposed as StateFlow and collected from Compose.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.necessitylabs.litert.config.AppConfig
import com.necessitylabs.litert.inference.core.InferenceEngine
import com.necessitylabs.litert.inference.model.InferenceEngineConfig
import com.necessitylabs.litert.inference.model.InferenceResult
import com.necessitylabs.litert.inference.model.InferenceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "InferenceViewModel"

/**
 * UI state for [InferenceScreen].
 *
 * @property engineState Current lifecycle state of the inference engine.
 * @property lastResult  The most recently completed inference result, or null if no
 *                       inference has been run yet.
 * @property errorMessage Human-readable error description, or null if no error is active.
 */
data class InferenceUiState(
    val engineState: InferenceState = InferenceState.Idle,
    val lastResult: InferenceResult? = null,
    val errorMessage: String? = null,
)

/**
 * ViewModel that drives the classical ML inference demo screen.
 *
 * @param inferenceEngine The LiteRT inference engine provided by Hilt.
 */
@HiltViewModel
class InferenceViewModel @Inject constructor(
    private val inferenceEngine: InferenceEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InferenceUiState())

    /**
     * Observable UI state. Collect in [InferenceScreen] using [androidx.lifecycle.compose.collectAsStateWithLifecycle].
     */
    val uiState: StateFlow<InferenceUiState> = _uiState.asStateFlow()

    init {
        // Forward engine state changes into the UI state flow.
        viewModelScope.launch {
            inferenceEngine.state.collect { engineState ->
                _uiState.value = _uiState.value.copy(
                    engineState = engineState,
                    errorMessage = if (engineState is InferenceState.Error) engineState.message else null,
                )
            }
        }
    }

    /**
     * Loads the TFLite model from [AppConfig.TFLITE_MODEL_PATH].
     *
     * The engine selects the best available delegate (NPU → GPU → CPU) automatically.
     * This is a no-op if the engine is already in the Ready state.
     */
    fun load() {
        if (inferenceEngine.state.value is InferenceState.Ready) {
            Log.d(TAG, "Engine already ready — skipping load.")
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val config = InferenceEngineConfig(
                    modelPath = AppConfig.TFLITE_MODEL_PATH,
                    numThreads = AppConfig.INTERPRETER_NUM_THREADS,
                )
                inferenceEngine.initialize(config)
            }
        }
    }

    /**
     * Runs a single inference pass with a synthetic input tensor.
     *
     * The dummy input is all-zeros of the shape expected by the loaded model.
     * In a real app, replace this with actual pre-processed sensor / image data.
     *
     * This function is a no-op if the engine is not in the Ready state.
     */
    fun runInference() {
        if (inferenceEngine.state.value !is InferenceState.Ready) {
            Log.w(TAG, "runInference() called before engine is ready — ignoring.")
            return
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                inferenceEngine.runInference()
            }
            when (result) {
                is InferenceResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        lastResult = result,
                        errorMessage = null,
                    )
                }
                is InferenceResult.Failure -> {
                    Log.e(TAG, "Inference failed: ${result.message}", result.cause)
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.message,
                    )
                }
            }
        }
    }
}
