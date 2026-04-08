/**
 * BenchmarkData.kt — Snapshot of inference performance metrics for one model session.
 *
 * Produced by [BenchmarkTracker.toBenchmarkData] and surfaced via
 * [InferenceEngine.benchmarkData].  Intended for display in developer overlays,
 * logcat reports, and telemetry pipelines.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.benchmark

import com.necessitylabs.litert.inference.delegate.DelegateType

/**
 * Immutable performance snapshot for a single model loaded by [InferenceEngine].
 *
 * All time values are in milliseconds.  Memory is in bytes.  Statistics are
 * computed over all [run] calls since the last [InferenceEngine.load].
 *
 * @property modelName          Human-readable model identifier from [ModelConfig.modelName].
 * @property delegateType       Backend that was ultimately selected after any fallback.
 * @property loadTimeMs         Time from [InferenceEngine.load] start to interpreter ready.
 * @property firstInferenceMs   Latency of the very first [run] call.  Often higher than
 *                              subsequent calls due to GPU shader compilation or
 *                              QNN context loading.
 * @property avgInferenceMs     Rolling average of all [run] call latencies.
 * @property minInferenceMs     Minimum observed [run] latency.
 * @property maxInferenceMs     Maximum observed [run] latency.
 * @property totalInferences    Total number of [run] calls recorded.
 * @property peakMemoryBytes    Highest [android.os.Debug.getNativeHeapAllocatedSize]
 *                              value observed across all [run] calls in this session.
 * @property fallbackOccurred   [true] if the preferred delegate failed and a lower-
 *                              priority backend was selected instead.
 * @property fallbackFrom       The delegate that was originally requested but failed.
 *                              Null when [fallbackOccurred] is [false].
 * @property fallbackTo         The delegate that was actually used after the fallback.
 *                              Null when [fallbackOccurred] is [false].
 */
data class BenchmarkData(
    val modelName: String,
    val delegateType: DelegateType,
    val loadTimeMs: Long,
    val firstInferenceMs: Long,
    val avgInferenceMs: Double,
    val minInferenceMs: Long,
    val maxInferenceMs: Long,
    val totalInferences: Long,
    val peakMemoryBytes: Long,
    val fallbackOccurred: Boolean,
    val fallbackFrom: DelegateType? = null,
    val fallbackTo: DelegateType? = null,
)
