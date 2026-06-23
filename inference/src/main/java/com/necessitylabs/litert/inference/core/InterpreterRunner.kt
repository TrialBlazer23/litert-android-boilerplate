/**
 * InterpreterRunner.kt — Low-level interpreter execution and construction helpers.
 *
 * Extracted from [LiteRtInferenceEngine] to keep that file within the 300-line
 * limit.  This file owns the two most verbose private operations:
 *
 *  - [buildInterpreter]: constructs an [InterpreterApi] from options and a candidate
 *  - [runInterpreterWithTiming]: executes a timed inference call and records metrics
 *
 * Neither function is part of the public API; they are internal to the
 * [com.necessitylabs.litert.inference.core] package.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.core

import android.os.Debug
import android.util.Log
import org.tensorflow.lite.Interpreter
import com.necessitylabs.litert.inference.benchmark.BenchmarkTracker
import com.necessitylabs.litert.inference.delegate.CpuSentinelDelegate
import com.necessitylabs.litert.inference.delegate.DelegateCandidate
import com.necessitylabs.litert.inference.delegate.DelegateType
import com.necessitylabs.litert.inference.model.InferenceResult
import com.necessitylabs.litert.inference.model.ModelConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.nio.ByteBuffer

private const val TAG = "InterpreterRunner"

/**
 * Constructs an [InterpreterApi] configured for [candidate] without calling
 * [InterpreterApi.allocateTensors].  The caller is responsible for calling
 * [allocateTensors] and handling any exceptions before marking the session ready.
 *
 * Key behaviour: [CpuSentinelDelegate] is intentionally NOT added to
 * [InterpreterApi.Options] — XNNPACK is activated by the runtime automatically
 * when no other delegate is present.
 *
 * @param modelBuffer Memory-mapped `.tflite` file (position must be 0).
 * @param candidate   Delegate candidate whose type and [Delegate] object to use.
 * @param config      Source of thread count, XNNPACK flag, and input shape override.
 * @return Configured but not yet tensor-allocated [InterpreterApi].
 */
internal fun buildInterpreter(
    modelBuffer: ByteBuffer,
    candidate: DelegateCandidate,
    config: ModelConfig,
): Interpreter {
    val options = Interpreter.Options().apply {
        setNumThreads(config.numThreads)
        setUseXNNPACK(config.useXnnpack)

        // CpuSentinelDelegate is a no-op token; adding it would crash the runtime.
        if (candidate.delegate !is CpuSentinelDelegate) {
            addDelegate(candidate.delegate)
        }
    }
    val interp = Interpreter(modelBuffer, options)
    // Resize input tensor 0 after construction when an override shape is provided.
    // Multi-input models that need per-tensor shape control can resize additional
    // tensors via the interpreter before allocateTensors is called by the engine.
    config.inputShape?.let { shape -> interp.resizeInput(0, shape) }
    return interp
}

/**
 * Executes one timed inference call and records the result in [tracker].
 *
 * The interpreter call is wrapped in [mutex] to guarantee that only one
 * thread at a time calls into the native runtime.  The suspend point is
 * preserved — the [Mutex] is a coroutine-friendly lock, not a JVM monitor.
 *
 * @param interp          Live [InterpreterApi] to invoke.
 * @param inputs          Input tensor index → data buffer/array.
 * @param outputs         Output tensor index → pre-allocated result buffer (modified in-place).
 * @param activeDelegate  The currently active [DelegateType], written into the result.
 * @param tracker         [BenchmarkTracker] that receives per-run timing.
 * @param mutex           Coroutine mutex guarding [interp] against concurrent access.
 * @return Populated [InferenceResult] with timing and memory fields set.
 * @throws IOException If the runtime throws during [runForMultipleInputsOutputs].
 */
internal suspend fun runInterpreterWithTiming(
    interp: Interpreter,
    inputs: Map<Int, Any>,
    outputs: Map<Int, Any>,
    activeDelegate: DelegateType,
    tracker: BenchmarkTracker,
    mutex: Mutex,
): InferenceResult {
    val startNs = System.nanoTime()

    try {
        mutex.withLock {
            interp.runForMultipleInputsOutputs(
                inputs.values.toTypedArray(),
                outputs,
            )
        }
    } catch (e: Exception) {
        throw IOException(
            "Interpreter.runForMultipleInputsOutputs failed " +
                "(delegate=$activeDelegate): ${e.message}",
            e,
        )
    }

    val inferenceMs = (System.nanoTime() - startNs) / 1_000_000L
    val memoryBytes = Debug.getNativeHeapAllocatedSize()

    tracker.recordRun(inferenceMs)

    val snapshot = tracker.toBenchmarkData()
    Log.v(TAG, "Run: ${inferenceMs}ms | mem=${memoryBytes / 1024}KB | delegate=$activeDelegate")

    return InferenceResult(
        outputs              = outputs,
        delegateUsed         = activeDelegate,
        loadTimeMs           = snapshot.loadTimeMs,
        inferenceTimeMs      = inferenceMs,
        firstInferenceTimeMs = snapshot.firstInferenceMs,
        memoryUsageBytes     = memoryBytes,
    )
}
