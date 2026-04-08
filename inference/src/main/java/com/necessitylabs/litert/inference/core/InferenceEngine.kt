/**
 * InferenceEngine.kt — Public contract for the classical-ML inference abstraction layer.
 *
 * All callers in the :app module depend on this interface, not on concrete
 * implementations.  This keeps the ViewModel and UI layers testable via fakes
 * without requiring real LiteRT or QNN libraries in unit tests.
 *
 * Lifecycle:
 *   1. Inject or create an [InferenceEngine] instance (via Hilt).
 *   2. Call [load] with a [ModelConfig] — this may take 1–10 seconds on NPU.
 *   3. Call [run] as many times as needed.
 *   4. Call [close] (or let the Hilt scope dispose) when done.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.core

import com.necessitylabs.litert.inference.benchmark.BenchmarkData
import com.necessitylabs.litert.inference.delegate.DelegateType
import com.necessitylabs.litert.inference.model.InferenceResult
import com.necessitylabs.litert.inference.model.ModelConfig
import java.nio.ByteBuffer

/**
 * Abstraction over a single LiteRT Interpreter instance for classical ML models.
 *
 * All [suspend] functions must be called from a coroutine; they internally
 * switch to [kotlinx.coroutines.Dispatchers.Default] for compute-bound work
 * and are safe to call from the main thread.
 *
 * Implementations are expected to be [AutoCloseable]: releasing the
 * interpreter, all delegates, and any allocated memory on [close].
 */
interface InferenceEngine : AutoCloseable {

    /**
     * Whether the engine has been successfully initialised by a [load] call
     * and is ready to accept [run] calls.
     *
     * Transitions to [true] after a successful [load]; resets to [false] when
     * [close] is called or if an unrecoverable error occurs mid-session.
     */
    val isReady: Boolean

    /**
     * The hardware backend that was successfully activated during the last
     * [load] call.  Reflects actual selection after any delegate fallback.
     *
     * Returns [DelegateType.CPU] before the first successful [load].
     */
    val activeDelegate: DelegateType

    /**
     * Current performance snapshot for the active session.
     *
     * Updated atomically after every [run] call.  Returns a zeroed snapshot
     * before the first [load].
     */
    val benchmarkData: BenchmarkData

    /**
     * Loads a `.tflite` model and initialises the interpreter with the best
     * available hardware delegate.
     *
     * The function tries delegates in priority order (QNN NPU → GPU → CPU).
     * On success [isReady] becomes [true] and [activeDelegate] is set.  On
     * total failure (all delegates exhausted), an [IOException] is thrown and
     * [isReady] remains [false].
     *
     * This call may take 1–15 seconds on first load (QNN AOT compilation).
     * Subsequent loads of the same model are faster due to QNN context caching.
     *
     * @param config Full model configuration including the absolute file path,
     *               delegate preferences, and thread budget.
     * @throws java.io.IOException If the model file cannot be mapped or if all
     *                             delegate initialisation attempts fail.
     * @throws IllegalStateException If called while the engine is already loading.
     */
    suspend fun load(config: ModelConfig)

    /**
     * Runs a single-input inference on the loaded model.
     *
     * The [input] buffer position must be set to 0 before passing — the
     * interpreter reads from the current position.
     *
     * @param input Raw bytes to write into input tensor 0.  The caller is
     *              responsible for encoding data to match the model's expected
     *              dtype (float32, int8, uint8, etc.).
     * @return [InferenceResult] containing all output tensors and timing data.
     * @throws IllegalStateException If [isReady] is [false].
     * @throws java.io.IOException   If the underlying interpreter throws during run.
     */
    suspend fun run(input: ByteBuffer): InferenceResult

    /**
     * Runs inference with arbitrary multi-input / multi-output tensor maps.
     *
     * Keys in [inputs] and [outputs] correspond to tensor indices as defined
     * by the `.tflite` model's signature.  The output map values should be
     * pre-allocated arrays or [ByteBuffer]s matching the tensor shapes.
     *
     * @param inputs  Map of input tensor index → data (ByteBuffer, FloatArray,
     *                IntArray, ByteArray, or nested arrays of primitives).
     * @param outputs Map of output tensor index → pre-allocated buffer that the
     *                interpreter will populate in-place.
     * @return [InferenceResult] containing the populated [outputs] map and timing data.
     * @throws IllegalStateException If [isReady] is [false].
     * @throws java.io.IOException   If the underlying interpreter throws during run.
     */
    suspend fun run(inputs: Map<Int, Any>, outputs: Map<Int, Any>): InferenceResult

    /**
     * Releases the interpreter, all delegates, and any cached QNN contexts.
     *
     * After calling [close], [isReady] is [false] and subsequent [run] calls
     * will throw [IllegalStateException].  Re-loading is possible by calling
     * [load] again.
     *
     * Safe to call multiple times — subsequent calls after the first are no-ops.
     */
    override fun close()
}
