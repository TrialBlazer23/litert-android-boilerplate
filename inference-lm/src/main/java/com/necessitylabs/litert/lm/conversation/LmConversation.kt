/**
 * LmConversation.kt — Wraps com.google.ai.edge.litertlm.Conversation with Flow-based streaming.
 *
 * This class is the primary interface for sending messages to a loaded LLM. It adds:
 *   • Synchronous helper (sendMessage) — blocks until the response is complete.
 *   • Streaming Flow API (sendMessageStream) — emits tokens as they arrive; preferred for UI.
 *   • Multimodal overload accepting Contents objects for image/audio input.
 *   • Transparent integration with LmBenchmarkTracker for first-token and tok/s metrics.
 *
 * Instances are created by LmEngineWrapper.createConversation() and must be closed
 * after use to free the KV-cache allocated by the native engine.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.lm.conversation

import android.util.Log
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

private const val TAG = "LmConversation"

/**
 * A stateful conversation session backed by a LiteRT-LM [Conversation].
 *
 * Maintains chat history internally (the underlying [Conversation] manages the KV-cache).
 * Create via [com.necessitylabs.litert.lm.engine.LmEngineWrapper.createConversation] —
 * do not instantiate directly.
 *
 * @param conversation     The raw LiteRT-LM Conversation handle.
 * @param benchmarkTracker Shared tracker that records first-token latency and tok/s.
 */
class LmConversation(
    private val conversation: Conversation,
    private val benchmarkTracker: LmBenchmarkTracker,
) : AutoCloseable {

    /**
     * Sends [text] to the model and returns the full response as a single String.
     *
     * Blocks the calling coroutine until generation is complete. For UI use, prefer
     * [sendMessageStream] which lets you display tokens as they arrive.
     *
     * Must be called on [kotlinx.coroutines.Dispatchers.Default] — never on the main thread.
     *
     * @param text The user message to send.
     * @return The complete model response.
     * @throws Exception if the underlying engine returns an error.
     */
    suspend fun sendMessage(text: String): String {
        benchmarkTracker.onMessageStart()

        val responseBuilder = StringBuilder()
        try {
            conversation.sendMessageAsync(text).collect { message ->
                val chunk = message.text ?: ""
                benchmarkTracker.onTokenReceived(chunk)
                responseBuilder.append(chunk)
            }
        } finally {
            benchmarkTracker.onMessageComplete()
        }

        return responseBuilder.toString()
    }

    /**
     * Sends [text] to the model and returns a [Flow] that emits each text token as it arrives.
     *
     * This is the preferred path for chat UIs — collect this Flow and append each emission
     * to the displayed message. The Flow completes normally when the model finishes generating.
     *
     * Must be collected on [kotlinx.coroutines.Dispatchers.Default] — never on the main thread.
     *
     * @param text The user message to send.
     * @return A cold [Flow] of token strings. Each emission is a partial text fragment;
     *         concatenating all emissions yields the full response.
     * @throws Exception propagated through the Flow if the engine errors mid-generation.
     */
    fun sendMessageStream(text: String): Flow<String> {
        return buildStreamingFlow { conversation.sendMessageAsync(text) }
    }

    /**
     * Multimodal overload — sends a [Contents] object (which may include images or audio)
     * and returns a streaming [Flow] of token strings.
     *
     * Use this when the model is a multimodal variant and the user has attached media.
     * For text-only messages, prefer [sendMessageStream].
     *
     * @param contents A [Contents] wrapping one or more [com.google.ai.edge.litertlm.Content]
     *                 items (text, image bytes, audio bytes, etc.).
     * @return A cold [Flow] of token strings.
     */
    fun sendMessageStream(contents: Contents): Flow<String> {
        return buildStreamingFlow { conversation.sendMessageAsync(contents) }
    }

    /**
     * Releases the KV-cache and underlying native resources held by this conversation.
     *
     * After close() is called, any further calls to sendMessage or sendMessageStream
     * will throw. A new conversation must be obtained from LmEngineWrapper.
     */
    override fun close() {
        try {
            conversation.close()
            Log.d(TAG, "Conversation closed and KV-cache released.")
        } catch (e: Exception) {
            Log.w(TAG, "Exception while closing Conversation — native resources may leak.", e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a [Flow] that wraps a streaming LiteRT-LM call, wiring in benchmark tracking.
     *
     * @param startCall Lambda that starts the native streaming call and returns the
     *                  raw [Flow] from the LiteRT-LM Conversation.
     */
    private fun buildStreamingFlow(
        startCall: suspend () -> Flow<com.google.ai.edge.litertlm.Message>,
    ): Flow<String> = flow {
        val rawFlow = startCall()
        rawFlow.collect { message ->
            val chunk = message.text ?: ""
            benchmarkTracker.onTokenReceived(chunk)
            if (chunk.isNotEmpty()) {
                emit(chunk)
            }
        }
    }.onStart {
        benchmarkTracker.onMessageStart()
    }.onCompletion { cause ->
        benchmarkTracker.onMessageComplete()
        if (cause != null) {
            Log.e(TAG, "Streaming generation terminated with error.", cause)
        }
    }.catch { e ->
        Log.e(TAG, "Error during LiteRT-LM streaming — re-throwing.", e)
        throw e
    }
}
