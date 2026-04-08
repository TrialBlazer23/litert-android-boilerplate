/**
 * LmEngineWrapper.kt — Manages the LiteRT-LM Engine lifecycle with backend fallback and benchmarking.
 *
 * This is the top-level entry point for the inference-lm module. It owns the Engine instance,
 * negotiates the backend (NPU → GPU → CPU), drives state transitions, and creates Conversation
 * sessions on demand.
 *
 * Usage:
 *   val wrapper = LmEngineWrapper()
 *   wrapper.initialize(config)          // suspend; call on Dispatchers.Default
 *   val convo = wrapper.createConversation()
 *   convo.sendMessageStream("Hello").collect { token -> ... }
 *   convo.close()
 *   wrapper.close()
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.lm.engine

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.necessitylabs.litert.lm.backend.LmBackendResolver
import com.necessitylabs.litert.lm.backend.LmBackendType
import com.necessitylabs.litert.lm.conversation.LmBenchmarkTracker
import com.necessitylabs.litert.lm.conversation.LmConversation
import com.necessitylabs.litert.lm.conversation.LmConversationConfig
import com.necessitylabs.litert.lm.conversation.LmMessage
import com.necessitylabs.litert.lm.conversation.LmRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "LmEngineWrapper"

/**
 * Wraps the LiteRT-LM [Engine] with automatic backend fallback, lifecycle management,
 * and observable state.
 *
 * Must be closed via [close] when no longer needed to release native resources.
 * Implements [AutoCloseable] for use in try-with-resources patterns.
 */
class LmEngineWrapper : AutoCloseable {

    // ── State ─────────────────────────────────────────────────────────────────

    private var engine: Engine? = null
    private var activeBackend: LmBackendType = LmBackendType.CPU
    private var activeConfig: LmEngineConfig? = null

    private val _state = MutableStateFlow<LmEngineState>(LmEngineState.Idle)

    /**
     * Observable lifecycle state. Collect this on the main thread to drive UI.
     * Transitions: Idle → Loading → Ready, or Idle → Loading → Error.
     */
    val state: StateFlow<LmEngineState> = _state.asStateFlow()

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Loads the model and initialises the Engine, walking the backend fallback chain.
     *
     * This suspend function must be called from [Dispatchers.Default] — it can block
     * for 3–15 seconds during first NPU compilation. The [state] Flow emits:
     *   [LmEngineState.Loading] (with the backend being attempted) for each attempt.
     *   [LmEngineState.Ready]   on success.
     *   [LmEngineState.Error]   if all attempts fail (or fallback is disabled).
     *
     * Calling initialize() while the engine is already [LmEngineState.Ready] will
     * close the existing engine first and re-initialise with the new [config].
     *
     * @param config All parameters for model loading and backend selection.
     * @throws Nothing — errors are communicated via the [state] Flow.
     */
    suspend fun initialize(config: LmEngineConfig) {
        withContext(Dispatchers.Default) {
            // Validate config before touching any native resources.
            try {
                config.validate()
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid LmEngineConfig: ${e.message}")
                _state.value = LmEngineState.Error(
                    message = "Invalid configuration: ${e.message}",
                    cause = e,
                )
                return@withContext
            }

            // Close any previously loaded engine.
            closeEngineInternal()
            activeConfig = config

            val startMs = System.currentTimeMillis()
            val backendsToTry = if (config.enableFallback) {
                LmBackendResolver.fallbackOrder(config.preferredBackend)
            } else {
                listOf(config.preferredBackend)
            }

            var lastError: Throwable? = null

            for (backendType in backendsToTry) {
                _state.value = LmEngineState.Loading(backendType)
                Log.i(TAG, "Attempting ${config.modelName} on $backendType backend…")

                try {
                    val backend: Backend = LmBackendResolver.resolve(
                        type = backendType,
                        nativeLibraryDir = config.nativeLibraryDir,
                    )

                    val engineConfig = buildEngineConfig(config, backend)
                    val newEngine = Engine(engineConfig)
                    newEngine.initialize()

                    // Success — record and exit the loop.
                    engine = newEngine
                    activeBackend = backendType
                    val loadTimeMs = System.currentTimeMillis() - startMs

                    Log.i(TAG, "Engine ready: ${config.modelName} on $backendType in ${loadTimeMs}ms.")
                    _state.value = LmEngineState.Ready(
                        backend = backendType,
                        loadTimeMs = loadTimeMs,
                    )
                    return@withContext

                } catch (e: Exception) {
                    lastError = e
                    Log.w(
                        TAG,
                        "Backend $backendType failed for ${config.modelName}: ${e.message}. " +
                            if (backendsToTry.last() != backendType) "Trying next…" else "No more fallbacks.",
                    )
                }
            }

            // All backends exhausted.
            val errorMessage = "Failed to initialise ${config.modelName} on any backend " +
                "(tried: ${backendsToTry.joinToString()}). Last error: ${lastError?.message}"
            Log.e(TAG, errorMessage, lastError)
            _state.value = LmEngineState.Error(message = errorMessage, cause = lastError)
        }
    }

    // ── Conversation factory ──────────────────────────────────────────────────

    /**
     * Creates a new [LmConversation] session. The engine must be in [LmEngineState.Ready]
     * before calling this function.
     *
     * Each conversation maintains its own KV-cache; close it after use to free memory.
     *
     * @param config Optional conversation parameters. Null uses the module defaults
     *               (topP=0.95, temperature=0.8, topK=40, no system prompt).
     * @return A ready-to-use [LmConversation].
     * @throws IllegalStateException if the engine is not in the [LmEngineState.Ready] state.
     */
    fun createConversation(config: LmConversationConfig? = null): LmConversation {
        val readyEngine = engine
            ?: error(
                "Cannot create a conversation — engine is not ready. " +
                    "Current state: ${_state.value}. Call initialize() first."
            )

        val effectiveConfig = config ?: LmConversationConfig()
        effectiveConfig.validate()

        val samplerConfig = SamplerConfig(
            topK = effectiveConfig.topK,
            topP = effectiveConfig.topP,
            temperature = effectiveConfig.temperature,
        )

        val conversationConfig = ConversationConfig(
            systemInstruction = effectiveConfig.systemInstruction?.let { Contents.of(it) },
            samplerConfig = samplerConfig,
            initialMessages = effectiveConfig.initialMessages.map { msg ->
                when (msg.role) {
                    LmRole.USER -> Message.user(msg.text)
                    LmRole.MODEL -> Message.model(msg.text)
                }
            },
        )

        val rawConversation = readyEngine.createConversation(conversationConfig)
        val tracker = LmBenchmarkTracker(initialBackend = activeBackend)

        return LmConversation(
            conversation = rawConversation,
            benchmarkTracker = tracker,
        )
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Closes the Engine and releases all native resources.
     *
     * After close(), call [initialize] again if the engine is needed.
     * Swallows exceptions from the native layer but logs them as warnings.
     */
    override fun close() {
        closeEngineInternal()
        _state.value = LmEngineState.Idle
        Log.i(TAG, "LmEngineWrapper closed.")
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the LiteRT-LM EngineConfig from our own config + resolved backend.
     *
     * @param config  The caller-supplied [LmEngineConfig].
     * @param backend The concrete [Backend] to embed in the config.
     * @return A ready-to-use [EngineConfig] data class.
     */
    private fun buildEngineConfig(config: LmEngineConfig, backend: Backend): EngineConfig {
        return EngineConfig(
            modelPath = config.modelPath,
            backend = backend,
            cacheDir = config.cacheDir,
            visionBackend = config.visionBackend?.let { vb ->
                LmBackendResolver.resolve(vb, config.nativeLibraryDir)
            },
            audioBackend = config.audioBackend?.let { ab ->
                LmBackendResolver.resolve(ab, config.nativeLibraryDir)
            },
        )
    }

    /**
     * Closes the native Engine without touching the state flow.
     * Safe to call when engine is null.
     */
    private fun closeEngineInternal() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Exception while closing Engine — native resources may leak.", e)
        } finally {
            engine = null
        }
    }
}
