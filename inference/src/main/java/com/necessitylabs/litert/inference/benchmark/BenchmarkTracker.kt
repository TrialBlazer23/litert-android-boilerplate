/**
 * BenchmarkTracker.kt — Thread-safe accumulator for per-session inference metrics.
 *
 * Records load time, per-run latencies, peak memory usage, and delegate fallback
 * events.  Uses [AtomicLong] and [AtomicReference] throughout so that the engine
 * can record metrics from [Dispatchers.Default] without external synchronisation.
 *
 * Call [reset] when the engine loads a new model to start a fresh session.
 * Call [recordRun] after every [Interpreter.runForMultipleInputsOutputs] call.
 * Retrieve results via [toBenchmarkData] or [toReport].
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.benchmark

import android.os.Debug
import android.util.Log
import com.necessitylabs.litert.inference.delegate.DelegateType
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "BenchmarkTracker"
private const val NO_TIME = Long.MAX_VALUE

/**
 * Accumulates inference performance data for a single engine session.
 *
 * One instance lives inside each [LiteRtInferenceEngine].  It is reset on
 * every [InferenceEngine.load] call so that metrics always reflect the current
 * model, not a previous load.
 */
class BenchmarkTracker {

    // ── Session-level atomics ─────────────────────────────────────────────────

    private val modelNameRef = AtomicReference("")
    private val delegateRef  = AtomicReference(DelegateType.CPU)

    private val loadTimeMs       = AtomicLong(0L)
    private val firstInferenceMs = AtomicLong(0L)

    private val totalInferences  = AtomicLong(0L)
    private val sumInferenceMs   = AtomicLong(0L)
    private val minInferenceMs   = AtomicLong(NO_TIME)
    private val maxInferenceMs   = AtomicLong(0L)
    private val peakMemoryBytes  = AtomicLong(0L)

    // Fallback tracking
    private val fallbackOccurred = AtomicReference(false)
    private val fallbackFromRef  = AtomicReference<DelegateType?>(null)
    private val fallbackToRef    = AtomicReference<DelegateType?>(null)

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /**
     * Resets all counters and begins a new tracking session for [modelName]
     * executing on [delegate].
     *
     * Must be called by the engine immediately after a successful model load,
     * before the first [recordRun].
     *
     * @param modelName Human-readable model identifier for reports.
     * @param delegate  Backend that was selected (after any fallback).
     * @param loadMs    Elapsed milliseconds during interpreter initialisation.
     */
    fun reset(modelName: String, delegate: DelegateType, loadMs: Long) {
        modelNameRef.set(modelName)
        delegateRef.set(delegate)
        loadTimeMs.set(loadMs)
        firstInferenceMs.set(0L)
        totalInferences.set(0L)
        sumInferenceMs.set(0L)
        minInferenceMs.set(NO_TIME)
        maxInferenceMs.set(0L)
        peakMemoryBytes.set(0L)
        fallbackOccurred.set(false)
        fallbackFromRef.set(null)
        fallbackToRef.set(null)
        Log.d(TAG, "Session reset — model=${modelName}, delegate=$delegate, loadMs=$loadMs")
    }

    /**
     * Records a delegate fallback event that occurred during model loading.
     *
     * [LiteRtInferenceEngine] calls this once after [reset] when it had to
     * downgrade from [from] to [to] because the preferred delegate initialised
     * successfully only on [to].
     *
     * @param from Original (preferred) delegate that failed.
     * @param to   Delegate that was ultimately selected.
     */
    fun recordFallback(from: DelegateType, to: DelegateType) {
        fallbackOccurred.set(true)
        fallbackFromRef.set(from)
        fallbackToRef.set(to)
        Log.w(TAG, "Delegate fallback: $from → $to")
    }

    /**
     * Accumulates timing and memory data for a single inference call.
     *
     * Thread-safe — may be called from any thread on [Dispatchers.Default].
     * Memory is sampled via [Debug.getNativeHeapAllocatedSize] immediately
     * after inference completes, before the result is returned.
     *
     * @param inferenceMs Wall-clock milliseconds for this [run] call.
     */
    fun recordRun(inferenceMs: Long) {
        val count = totalInferences.incrementAndGet()

        // First-inference tracking — only set once.
        if (count == 1L) {
            firstInferenceMs.set(inferenceMs)
        }

        sumInferenceMs.addAndGet(inferenceMs)

        // CAS loops to track min/max without a lock.
        updateMin(inferenceMs)
        updateMax(inferenceMs)

        // Peak memory — sample native heap after inference.
        val currentMemory = Debug.getNativeHeapAllocatedSize()
        updatePeakMemory(currentMemory)

        Log.v(TAG, "Run #$count — ${inferenceMs}ms | heap=${currentMemory / 1024}KB")
    }

    // ── Output methods ────────────────────────────────────────────────────────

    /**
     * Constructs an immutable [BenchmarkData] snapshot from the current counters.
     *
     * Safe to call at any time; values reflect the state at the moment of the call.
     *
     * @return Snapshot of all accumulated metrics for this session.
     */
    fun toBenchmarkData(): BenchmarkData {
        val count = totalInferences.get()
        val avg = if (count > 0) sumInferenceMs.get().toDouble() / count else 0.0
        val rawMin = minInferenceMs.get()
        return BenchmarkData(
            modelName        = modelNameRef.get(),
            delegateType     = delegateRef.get(),
            loadTimeMs       = loadTimeMs.get(),
            firstInferenceMs = firstInferenceMs.get(),
            avgInferenceMs   = avg,
            minInferenceMs   = if (rawMin == NO_TIME) 0L else rawMin,
            maxInferenceMs   = maxInferenceMs.get(),
            totalInferences  = count,
            peakMemoryBytes  = peakMemoryBytes.get(),
            fallbackOccurred = fallbackOccurred.get(),
            fallbackFrom     = fallbackFromRef.get(),
            fallbackTo       = fallbackToRef.get(),
        )
    }

    /**
     * Formats accumulated metrics as a human-readable logcat string.
     *
     * Intended for debug overlays or `adb logcat` monitoring during development.
     *
     * @return Multiline report string.
     */
    fun toReport(): String {
        val data = toBenchmarkData()
        val rawMin = if (data.minInferenceMs == 0L && data.totalInferences == 0L) "—" else "${data.minInferenceMs}ms"
        val fallbackLine = if (data.fallbackOccurred) {
            "  Fallback: ${data.fallbackFrom} → ${data.fallbackTo}\n"
        } else ""
        return buildString {
            append("=== Benchmark: ${data.modelName} ===\n")
            append("  Delegate   : ${data.delegateType}\n")
            append("  Load time  : ${data.loadTimeMs}ms\n")
            append("  1st infer  : ${data.firstInferenceMs}ms\n")
            append("  Avg infer  : ${"%.1f".format(data.avgInferenceMs)}ms\n")
            append("  Min/Max    : $rawMin / ${data.maxInferenceMs}ms\n")
            append("  Runs       : ${data.totalInferences}\n")
            append("  Peak mem   : ${data.peakMemoryBytes / 1024}KB\n")
            append(fallbackLine)
            append("=================================")
        }
    }

    // ── CAS helpers ───────────────────────────────────────────────────────────

    private fun updateMin(value: Long) {
        while (true) {
            val cur = minInferenceMs.get()
            if (value >= cur || minInferenceMs.compareAndSet(cur, value)) return
        }
    }

    private fun updateMax(value: Long) {
        while (true) {
            val cur = maxInferenceMs.get()
            if (value <= cur || maxInferenceMs.compareAndSet(cur, value)) return
        }
    }

    private fun updatePeakMemory(value: Long) {
        while (true) {
            val cur = peakMemoryBytes.get()
            if (value <= cur || peakMemoryBytes.compareAndSet(cur, value)) return
        }
    }
}
