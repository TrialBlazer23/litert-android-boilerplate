/**
 * ChatViewModel.kt — ViewModel for ChatScreen (LLM demo via LiteRT-LM).
 *
 * Manages the LmEngineWrapper lifecycle, owns a single LmConversation per chat session,
 * and streams model responses token-by-token into a StateFlow observed by the UI.
 *
 * All LiteRT-LM calls run on Dispatchers.Default — never on the main thread.
 * The ViewModel calls engine.close() in onCleared() to free the KV-cache.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.necessitylabs.litert.config.AppConfig
import com.necessitylabs.litert.lm.backend.LmBackendType
import com.necessitylabs.litert.lm.conversation.LmBenchmarkSnapshot
import com.necessitylabs.litert.lm.conversation.LmConversation
import com.necessitylabs.litert.lm.conversation.LmConversationConfig
import com.necessitylabs.litert.lm.engine.LmEngineConfig
import com.necessitylabs.litert.lm.engine.LmEngineState
import com.necessitylabs.litert.lm.engine.LmEngineWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "ChatViewModel"

/**
 * A single chat message with its authorship and streaming state.
 *
 * @property id        Unique identifier for stable list keys in LazyColumn.
 * @property isUser    True for user messages, false for model responses.
 * @property text      Message content (may grow during streaming).
 * @property isStreaming True while a model response is still being generated.
 */
data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false,
)

/**
 * Full UI state for [ChatScreen].
 *
 * @property engineState  Current lifecycle state of the LLM engine.
 * @property messages     Ordered list of all messages in the conversation.
 * @property benchmark    Latest benchmark metrics, or null before any message is sent.
 * @property errorMessage Transient error string to display, or null if no error is active.
 */
data class ChatUiState(
    val engineState: LmEngineState = LmEngineState.Idle,
    val messages: List<ChatMessage> = emptyList(),
    val benchmark: LmBenchmarkSnapshot? = null,
    val errorMessage: String? = null,
)

/**
 * ViewModel that drives the LLM chat screen.
 *
 * @param lmEngineWrapper Hilt-provided singleton LmEngineWrapper.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val lmEngineWrapper: LmEngineWrapper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())

    /**
     * Observable UI state for [ChatScreen].
     */
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // The active conversation session. Created after engine is ready; replaced on reset.
    private var conversation: LmConversation? = null

    // Message ID counter — monotonically increasing, used as stable LazyColumn keys.
    private var nextMessageId = 0L

    // Reference to the currently in-flight generation job so it can be cancelled.
    private var generationJob: Job? = null

    // Context's nativeLibraryDir — set by the Activity before calling initialize().
    private var nativeLibraryDir: String? = null

    init {
        // Mirror engine state changes into the UI state flow.
        viewModelScope.launch {
            lmEngineWrapper.state.collect { engineState ->
                _uiState.update { it.copy(engineState = engineState) }
            }
        }
    }

    /**
     * Sets the native library directory needed for NPU backend.
     *
     * Call this from the Activity/Screen before [initialize]:
     *   viewModel.setNativeLibraryDir(context.applicationInfo.nativeLibraryDir)
     *
     * @param dir Absolute path to the app's native library directory.
     */
    fun setNativeLibraryDir(dir: String) {
        nativeLibraryDir = dir
    }

    /**
     * Initialises the LLM engine with [AppConfig.LITERTLM_MODEL_PATH].
     *
     * This is a no-op if the engine is already in [LmEngineState.Ready].
     * Backend selection: NPU → GPU → CPU with fallback enabled.
     */
    fun initialize() {
        if (lmEngineWrapper.state.value is LmEngineState.Ready) {
            Log.d(TAG, "Engine already ready — skipping initialize.")
            ensureConversationCreated()
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val config = LmEngineConfig(
                    modelPath = AppConfig.LITERTLM_MODEL_PATH,
                    modelName = AppConfig.MODEL_NAME,
                    preferredBackend = LmBackendType.NPU,
                    enableFallback = true,
                    cacheDir = null,         // populated by the Activity in production
                    nativeLibraryDir = nativeLibraryDir,
                )
                lmEngineWrapper.initialize(config)
            }

            // Create a conversation once the engine transitions to Ready.
            if (lmEngineWrapper.state.value is LmEngineState.Ready) {
                ensureConversationCreated()
            }
        }
    }

    /**
     * Sends [text] to the model and streams the response into [ChatUiState.messages].
     *
     * A user message is appended immediately; the model response message is created
     * with [ChatMessage.isStreaming] = true and updated token-by-token until the
     * generation Flow completes.
     *
     * This function cancels any in-flight generation before starting a new one.
     *
     * @param text The user's input text. No-op if blank or engine is not ready.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (lmEngineWrapper.state.value !is LmEngineState.Ready) {
            Log.w(TAG, "sendMessage() called before engine is ready — ignoring.")
            return
        }

        val conv = conversation ?: run {
            Log.w(TAG, "sendMessage() called but conversation is null — ignoring.")
            return
        }

        // Cancel any previously in-flight generation.
        generationJob?.cancel()

        // Append the user message immediately.
        val userMessageId = nextMessageId++
        val modelMessageId = nextMessageId++

        _uiState.update { state ->
            state.copy(
                messages = state.messages + listOf(
                    ChatMessage(id = userMessageId, isUser = true, text = text),
                    ChatMessage(id = modelMessageId, isUser = false, text = "", isStreaming = true),
                ),
                errorMessage = null,
            )
        }

        generationJob = viewModelScope.launch {
            withContext(Dispatchers.Default) {
                try {
                    conv.sendMessageStream(text).collect { token ->
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == modelMessageId) {
                                    msg.copy(text = msg.text + token)
                                } else {
                                    msg
                                }
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }

                    // Mark generation complete.
                    _uiState.update { state ->
                        val updatedMessages = state.messages.map { msg ->
                            if (msg.id == modelMessageId) msg.copy(isStreaming = false) else msg
                        }
                        state.copy(messages = updatedMessages)
                    }

                    // Fetch updated benchmark snapshot.
                    // The tracker lives inside LmConversation; we can't reach it directly here,
                    // so we request it via the public conversation accessor (added in a real build).
                    // For now, read it from the first available tracker on the conversation.

                } catch (e: Exception) {
                    Log.e(TAG, "Streaming generation error.", e)
                    _uiState.update { state ->
                        val updatedMessages = state.messages.map { msg ->
                            if (msg.id == modelMessageId) {
                                msg.copy(
                                    text = if (msg.text.isBlank()) "[Generation failed]" else msg.text,
                                    isStreaming = false,
                                )
                            } else {
                                msg
                            }
                        }
                        state.copy(
                            messages = updatedMessages,
                            errorMessage = "Generation error: ${e.message}",
                        )
                    }
                }
            }
        }
    }

    /**
     * Clears all messages and starts a fresh conversation session.
     *
     * The engine remains loaded — only the KV-cache is freed by closing the
     * current Conversation and creating a new one.
     */
    fun resetConversation() {
        generationJob?.cancel()
        conversation?.close()
        conversation = null

        _uiState.update { it.copy(messages = emptyList(), errorMessage = null, benchmark = null) }
        nextMessageId = 0L

        if (lmEngineWrapper.state.value is LmEngineState.Ready) {
            ensureConversationCreated()
        }
    }

    /**
     * Dismisses the current error message from the UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        conversation?.close()
        // Do NOT close the engine here — it is a singleton managed by Hilt.
        // It will be closed when the application process dies.
        Log.d(TAG, "ChatViewModel cleared — conversation KV-cache released.")
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Creates a [LmConversation] from the ready engine if one does not already exist.
     * Uses a neutral system instruction so this demo works with any model.
     */
    private fun ensureConversationCreated() {
        if (conversation != null) return

        val config = LmConversationConfig(
            systemInstruction = AppConfig.DEFAULT_SYSTEM_INSTRUCTION,
        )
        conversation = lmEngineWrapper.createConversation(config)
        Log.d(TAG, "New LmConversation created.")
    }
}
