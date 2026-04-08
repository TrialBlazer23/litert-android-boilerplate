/**
 * LmBenchmarkTracker.kt — Thread-safe tracker for LLM inference performance metrics.
 *
 * Tracks per-message and aggregate statistics that are exposed to the UI layer
 * as [LmBenchmarkSnapshot] values. All mutation is guarded by a Mutex so this
 * class is safe to call from multiple coroutines on Dispatchers.Default.
 *
 * Metrics tracked:
 *   • First-token latency — time from sendMessage to first token arriving (ms)
 *   • Tokens per second   — streaming generation throughput
 *   • Total generation time per message (ms)
 *   • Active backend type (for display)
 *   • Total tokens generated across the session
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.lm.conversation

import com.necessitylabs.litert.lm.backend.LmBackendType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Immutable snapshot of all benchmark metrics collected at a point in time.
 *
 * @property activeBackend           Backend that generated these metrics.
 * @property lastFirstTokenLatencyMs Time from message send to receipt of first token (ms).
 *                                   -1 if no message has been sent yet.
 * @property lastTotalGenerationMs   Wall-clock time for the last full generation (ms).
 *                                   -1 if no message has been completed yet.
 * @property lastTokensPerSecond     Streaming throughput for the last generation.
 *                                   0.0 if fewer than 2 tokens were generated.
 * @property sessionTotalTokens      Cumulative count of tokens generated in this conversation.
 * @property messageCount            Number of model turns completed.
 */
data class LmBenchmarkSnapshot(
    val activeBackend: LmBackendType,
    val lastFirstTokenLatencyMs: Long = -1L,
    val lastTotalGenerationMs: Long = -1L,
    val lastTokensPerSecond: Double = 0.0,
    val sessionTotalTokens: Long = 0L,
    val messageCount: Int = 0,
)

/**
 * Mutable tracker that accumulates benchmark metrics from [LmConversation] streaming calls.
 *
 * Usage pattern:
 * ```kotlin
 * tracker.onMessageStart()
 * conversation.sendMessageAsync(text).collect { message ->
 *     tracker.onTokenReceived(message.text ?: "")
 * }
 * tracker.onMessageComplete()
 * val snapshot = tracker.snapshot()
 * ```
 *
 * @param initialBackend The backend active at the time this tracker is created.
 */
class LmBenchmarkTracker(initialBackend: LmBackendType) {

    private val mutex = Mutex()

    // Current backend — updated when LmEngineWrapper resolves the actual backend.
    private var activeBackend: LmBackendType = initialBackend

    // Per-message working state.
    private var messageStartMs: Long = 0L
    private var firstTokenReceivedMs: Long = -1L
    private var tokenCountForCurrentMessage: Long = 0L

    // Session aggregate state.
    private var sessionTotalTokens: Long = 0L
    private var messageCount: Int = 0

    // Persistent metric values from the last completed message.
    private var lastFirstTokenLatencyMs: Long = -1L
    private var lastTotalGenerationMs: Long = -1L
    private var lastTokensPerSecond: Double = 0.0

    /**
     * Records the moment a new message generation begins.
     * Must be called before [onTokenReceived] for each message.
     */
    suspend fun onMessageStart() = mutex.withLock {
        messageStartMs = System.currentTimeMillis()
        firstTokenReceivedMs = -1L
        tokenCountForCurrentMessage = 0L
    }

    /**
     * Records the receipt of one token (or token chunk) from the streaming Flow.
     *
     * @param tokenText The raw text of the token. May be empty for metadata-only messages;
     *                  empty strings are ignored for counting purposes.
     */
    suspend fun onTokenReceived(tokenText: String) = mutex.withLock {
        if (tokenText.isEmpty()) return@withLock

        val now = System.currentTimeMillis()

        // Capture first-token latency on the very first non-empty token.
        if (firstTokenReceivedMs < 0L) {
            firstTokenReceivedMs = now
        }

        tokenCountForCurrentMessage++
    }

    /**
     * Records the end of a generation and computes derived metrics.
     * Must be called once after the streaming Flow completes (or errors).
     */
    suspend fun onMessageComplete() = mutex.withLock {
        val endMs = System.currentTimeMillis()
        val generationMs = endMs - messageStartMs

        lastTotalGenerationMs = generationMs
        lastFirstTokenLatencyMs = if (firstTokenReceivedMs >= 0L) {
            firstTokenReceivedMs - messageStartMs
        } else {
            -1L
        }

        // Tokens per second: count tokens over the total generation window.
        // We use the full wall-clock time (not time-to-last-token) so the number
        // reflects the real user-perceived throughput including first-token overhead.
        lastTokensPerSecond = if (generationMs > 0L && tokenCountForCurrentMessage > 0L) {
            (tokenCountForCurrentMessage * 1_000.0) / generationMs
        } else {
            0.0
        }

        sessionTotalTokens += tokenCountForCurrentMessage
        messageCount++
    }

    /**
     * Updates the active backend. Called by [LmEngineWrapper] after backend negotiation.
     *
     * @param backend The backend that was successfully initialised.
     */
    suspend fun setBackend(backend: LmBackendType) = mutex.withLock {
        activeBackend = backend
    }

    /**
     * Returns an immutable snapshot of all current metrics.
     * Safe to call at any point — takes the mutex for a brief read.
     *
     * @return A [LmBenchmarkSnapshot] reflecting the latest state.
     */
    suspend fun snapshot(): LmBenchmarkSnapshot = mutex.withLock {
        LmBenchmarkSnapshot(
            activeBackend = activeBackend,
            lastFirstTokenLatencyMs = lastFirstTokenLatencyMs,
            lastTotalGenerationMs = lastTotalGenerationMs,
            lastTokensPerSecond = lastTokensPerSecond,
            sessionTotalTokens = sessionTotalTokens,
            messageCount = messageCount,
        )
    }
}
