/**
 * InferenceResult.kt — Structured result returned by [InferenceEngine.run].
 *
 * Carries both the raw output tensors and the latency / memory metrics
 * captured during the call.  Callers can use [outputs] for downstream
 * processing and feed the timing fields into [BenchmarkTracker].
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.model

import com.necessitylabs.litert.inference.delegate.DelegateType

/**
 * Immutable result of a single [InferenceEngine.run] call.
 *
 * @property outputs            Map of output tensor index → raw buffer or array,
 *                              matching the shape passed in via [InferenceEngine.run]'s
 *                              [outputs] parameter.  The concrete type of each
 *                              value depends on the model: typically
 *                              [FloatArray], [ByteArray], or a nested array.
 * @property delegateUsed       Which hardware backend actually executed the
 *                              model.  May differ from the preferred backend
 *                              if a fallback occurred during [InferenceEngine.load].
 * @property loadTimeMs         Milliseconds elapsed from calling [InferenceEngine.load]
 *                              to the interpreter being fully ready.  Zero for
 *                              subsequent [run] calls after the first load.
 * @property inferenceTimeMs    Wall-clock milliseconds for this individual
 *                              [run] call, measured around [Interpreter.runForMultipleInputsOutputs].
 * @property firstInferenceTimeMs
 *                              Milliseconds for the very first [run] call after
 *                              load (may be higher due to JIT / GPU shader
 *                              compilation).  Equals [inferenceTimeMs] for the
 *                              first call; 0 for subsequent calls.
 * @property memoryUsageBytes   Native heap allocated size captured via
 *                              [android.os.Debug.getNativeHeapAllocatedSize]
 *                              immediately after inference completes.
 */
data class InferenceResult(
    val outputs: Map<Int, Any>,
    val delegateUsed: DelegateType,
    val loadTimeMs: Long,
    val inferenceTimeMs: Long,
    val firstInferenceTimeMs: Long,
    val memoryUsageBytes: Long,
)
