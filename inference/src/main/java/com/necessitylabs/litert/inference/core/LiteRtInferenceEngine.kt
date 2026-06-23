/**
 * LiteRtInferenceEngine.kt — Production [InferenceEngine] using the LiteRT Interpreter API.
 *
 * Concrete implementation that:
 *  - Tries delegates in priority order (QNN NPU → Adreno GPU → XNNPACK CPU)
 *  - Falls through automatically if a higher-priority delegate fails at init
 *  - Wraps every inference call with nanosecond-precision timing (via [InterpreterRunner])
 *  - Guards the interpreter with a [Mutex] for thread-safe concurrent callers
 *  - Always dispatches work to [Dispatchers.Default], never the main thread
 *  - Closes all delegates and the [Interpreter] cleanly on [close]
 *
 * Heavy helpers ([buildInterpreter], [runInterpreterWithTiming]) live in
 * [InterpreterRunner.kt] to keep this file within the 300-line limit.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.core

import android.util.Log
import org.tensorflow.lite.Interpreter
import com.necessitylabs.litert.inference.benchmark.BenchmarkData
import com.necessitylabs.litert.inference.benchmark.BenchmarkTracker
import com.necessitylabs.litert.inference.delegate.DelegateCandidate
import com.necessitylabs.litert.inference.delegate.DelegateProvider
import com.necessitylabs.litert.inference.delegate.DelegateType
import com.necessitylabs.litert.inference.model.InferenceResult
import com.necessitylabs.litert.inference.model.ModelConfig
import com.necessitylabs.litert.inference.model.ModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer

private const val TAG = "LiteRtInferenceEngine"

/**
 * LiteRT Interpreter-based inference engine with automatic delegate fallback.
 *
 * @param delegateProvider Factory that produces the ordered list of delegate
 *                         candidates for each [load] call.  Inject a fake in
 *                         unit tests to avoid touching real QNN libraries.
 */
class LiteRtInferenceEngine(
    private val delegateProvider: DelegateProvider,
) : InferenceEngine {

    // ── Observable state ──────────────────────────────────────────────────────

    @Volatile override var isReady: Boolean = false
        private set

    @Volatile override var activeDelegate: DelegateType = DelegateType.CPU
        private set

    override val benchmarkData: BenchmarkData
        get() = tracker.toBenchmarkData()

    // ── Internal state ────────────────────────────────────────────────────────

    private val tracker   = BenchmarkTracker()
    private val mutex     = Mutex()

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var activeCandidates: List<DelegateCandidate> = emptyList()
    @Volatile private var isClosing: Boolean = false

    // ── Load ──────────────────────────────────────────────────────────────────

    /**
     * Loads [config.modelPath] and initialises the interpreter using the
     * best available hardware delegate on [Dispatchers.Default].
     *
     * Delegates are tried in priority order (lowest integer first).  If a
     * delegate throws during [Interpreter.allocateTensors], it is closed and
     * the next candidate is attempted.  All delegate failures result in an
     * [IOException] from this function.
     *
     * @param config Full model + delegate configuration.
     * @throws IOException       If the model file cannot be mapped or all delegates fail.
     * @throws IllegalStateException If the engine has already been [close]d.
     */
    override suspend fun load(config: ModelConfig): Unit = withContext(Dispatchers.Default) {
        check(!isClosing) { "Engine has been closed; create a new instance to reload." }

        val startMs = System.currentTimeMillis()
        ModelLoader.verifyModelFile(config.modelPath)
        val modelBuffer = ModelLoader.mapModelFile(config.modelPath)
        Log.i(TAG, "Mapped '${config.modelName}' " +
                "(${ModelLoader.getModelFileSize(config.modelPath) / 1024} KB)")

        val candidates = delegateProvider.createDelegates(config.delegateConfig)
        var lastError: Exception? = null
        var selectedCandidate: DelegateCandidate? = null
        var newInterpreter: Interpreter? = null

        for (candidate in candidates) {
            try {
                val interp = buildInterpreter(modelBuffer, candidate, config)
                interp.allocateTensors()          // eagerly validate shape + delegate
                selectedCandidate = candidate
                newInterpreter = interp
                Log.i(TAG, "Interpreter initialised with delegate: ${candidate.type}")
                break
            } catch (e: Exception) {
                Log.w(TAG, "Delegate ${candidate.type} failed: ${e.javaClass.simpleName} — ${e.message}")
                candidate.delegate.close()
                lastError = e
            }
        }

        if (newInterpreter == null || selectedCandidate == null) {
            throw IOException(
                "All delegates exhausted for '${config.modelName}'. " +
                    "Last error: ${lastError?.message}",
                lastError,
            )
        }

        val loadMs = System.currentTimeMillis() - startMs

        mutex.withLock {
            releaseCurrentSession()
            interpreter      = newInterpreter
            activeCandidates = candidates
            activeDelegate   = selectedCandidate.type
            isReady          = true
        }

        // Record fallback if a higher-priority delegate was skipped.
        val topType = candidates.minByOrNull { it.priority }?.type
        tracker.reset(config.modelName, selectedCandidate.type, loadMs)
        if (topType != null && topType != selectedCandidate.type) {
            tracker.recordFallback(from = topType, to = selectedCandidate.type)
        }

        Log.i(TAG, "Load complete — delegate=${selectedCandidate.type}, loadMs=$loadMs")
    }

    // ── Run: single ByteBuffer ────────────────────────────────────────────────

    /**
     * Runs inference with a single [ByteBuffer] on input tensor 0.
     *
     * The output for tensor 0 is allocated as a [FloatArray] sized to match
     * the model's first output shape.  Use the [Map]-based overload for
     * multi-output models or non-float output types.
     *
     * @param input [ByteBuffer] for input tensor 0 (rewound to position 0).
     * @return [InferenceResult] with output tensor 0 at key 0.
     * @throws IllegalStateException If [isReady] is false.
     * @throws IOException           If the runtime throws during inference.
     */
    override suspend fun run(input: ByteBuffer): InferenceResult =
        withContext(Dispatchers.Default) {
            val interp = requireReadyInterpreter()
            input.rewind()

            val outputShape  = interp.getOutputTensor(0).shape()
            val outputSize   = outputShape.fold(1) { acc, dim -> acc * dim }
            val outputBuffer = FloatArray(outputSize)

            runInterpreterWithTiming(
                interp        = interp,
                inputs        = mapOf(0 to input),
                outputs       = mapOf(0 to outputBuffer),
                activeDelegate = activeDelegate,
                tracker       = tracker,
                mutex         = mutex,
            )
        }

    // ── Run: multi-tensor maps ────────────────────────────────────────────────

    /**
     * Runs inference with arbitrary multi-input / multi-output tensor maps.
     *
     * @param inputs  Tensor index → input data (ByteBuffer, FloatArray, etc.).
     * @param outputs Tensor index → pre-allocated output buffer (populated in-place).
     * @return [InferenceResult] with the populated [outputs] map.
     * @throws IllegalStateException If [isReady] is false.
     * @throws IOException           If the runtime throws during inference.
     */
    override suspend fun run(
        inputs: Map<Int, Any>,
        outputs: Map<Int, Any>,
    ): InferenceResult = withContext(Dispatchers.Default) {
        val interp = requireReadyInterpreter()
        runInterpreterWithTiming(
            interp         = interp,
            inputs         = inputs,
            outputs        = outputs,
            activeDelegate = activeDelegate,
            tracker        = tracker,
            mutex          = mutex,
        )
    }

    // ── Close ─────────────────────────────────────────────────────────────────

    /**
     * Releases the interpreter and all delegates.  Safe to call multiple times.
     */
    override fun close() {
        isClosing = true
        isReady   = false
        releaseCurrentSession()
        Log.i(TAG, "Engine closed")
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the live [Interpreter] or throws if the engine is not ready.
     *
     * Does NOT acquire the mutex — [runInterpreterWithTiming] handles locking.
     *
     * @throws IllegalStateException If [isReady] is false or [interpreter] is null.
     */
    private fun requireReadyInterpreter(): Interpreter {
        check(isReady) { "Engine is not ready. Call load() before run()." }
        return interpreter ?: error("Interpreter is null despite isReady=true")
    }

    /**
     * Closes the current interpreter and all cached delegate candidates.
     *
     * Must be called while holding [mutex] or from [close] after [isReady]
     * is set to false.  Errors are logged and swallowed to prevent a partially
     * cleaned-up state from masking the original failure.
     */
    private fun releaseCurrentSession() {
        interpreter?.let { interp ->
            runCatching { interp.close() }.onFailure { e ->
                Log.e(TAG, "Error closing interpreter: ${e.message}")
            }
            interpreter = null
        }
        activeCandidates.forEach { candidate ->
            runCatching { candidate.delegate.close() }.onFailure { e ->
                Log.e(TAG, "Error closing delegate ${candidate.type}: ${e.message}")
            }
        }
        activeCandidates = emptyList()
    }
}
