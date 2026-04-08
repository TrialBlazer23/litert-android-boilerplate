# Benchmarking Guide

The `inference` module includes a `BenchmarkTracker` that captures per-inference performance
metrics. This guide explains what is measured, how to read the output, expected numbers on SM8550,
and how to profile memory.

---

## Enabling Benchmarking

Set the flag in `AppConfig`:

```kotlin
object AppConfig {
    const val ENABLE_BENCHMARKING = true
    const val BENCHMARK_OVERLAY = true   // shows on-screen stats (debug builds only)
}
```

Benchmarking is compiled out in release builds when `ENABLE_BENCHMARKING = false`. Leaving it
enabled in debug builds does not affect inference performance — the timer overhead is < 0.1ms.

---

## What Metrics Are Captured

`BenchmarkTracker` captures the following for every inference call:

| Metric | Type | Description |
|---|---|---|
| `inferenceLatencyMs` | `Double` | Wall-clock time from `interpreter.run()` call to return |
| `preprocessLatencyMs` | `Double` | Time to prepare input tensors (resize, normalize, convert to ByteBuffer) |
| `postprocessLatencyMs` | `Double` | Time to read and interpret output tensors |
| `totalLatencyMs` | `Double` | Sum of all three phases |
| `delegateType` | `DelegateType` | `QNN_HTP`, `GPU`, or `CPU` |
| `memoryDeltaKb` | `Long` | Approximate heap change during inference (via `Debug.getNativeHeapAllocatedSize`) |
| `isWarmRun` | `Boolean` | `false` for the first inference (model compilation), `true` thereafter |
| `timestamp` | `Long` | `System.nanoTime()` at inference start |

### Important Notes on Latency

**First-run QNN latency is high and expected.** When a model runs on the QNN HTP delegate for the
first time, the Hexagon compiler generates a device-optimized context binary. This compilation can
take 10–60 seconds depending on model size. `isWarmRun = false` identifies this run. Do not include
it in average latency calculations.

**`inferenceLatencyMs` measures the Interpreter API run time only.** It does not include data
loading, model file I/O, or delegate initialization.

---

## How to Read Benchmark Output

### Logcat

Filter: `adb logcat -s LiteRTBenchmark`

```
I/LiteRTBenchmark: --- Inference #1 (COLD) ---
I/LiteRTBenchmark:   Delegate:   QNN_HTP
I/LiteRTBenchmark:   Preprocess: 1.2ms
I/LiteRTBenchmark:   Inference:  34821.0ms   ← first run: QNN compilation
I/LiteRTBenchmark:   Postprocess: 0.4ms
I/LiteRTBenchmark:   Total:      34822.6ms
I/LiteRTBenchmark:   Memory ΔKB: +18240

I/LiteRTBenchmark: --- Inference #2 (WARM) ---
I/LiteRTBenchmark:   Delegate:   QNN_HTP
I/LiteRTBenchmark:   Preprocess: 1.1ms
I/LiteRTBenchmark:   Inference:  2.8ms       ← warm run: cached context binary
I/LiteRTBenchmark:   Postprocess: 0.3ms
I/LiteRTBenchmark:   Total:      4.2ms
I/LiteRTBenchmark:   Memory ΔKB: +0
```

### On-Screen Overlay

When `BENCHMARK_OVERLAY = true` in debug builds, a semi-transparent overlay appears in the bottom
corner of the screen showing:
- Active delegate
- Rolling average latency (last 10 warm inferences)
- Peak memory since app launch

---

## Expected Performance Ranges on SM8550

These numbers are for warm inferences with QNN context binaries already compiled.

### Classical ML (inference module — .tflite)

| Model | Delegate | Inference Latency | Notes |
|---|---|---|---|
| MobileNetV3-Small (224×224 INT8) | QNN HTP | 1.5–3ms | Image classification |
| MobileNetV3-Large (224×224 INT8) | QNN HTP | 2–5ms | Image classification |
| EfficientDet-Lite0 (320×320 INT8) | QNN HTP | 4–8ms | Object detection |
| EfficientDet-Lite2 (448×448 INT8) | QNN HTP | 8–15ms | Object detection |
| MobileNet-SSD (300×300 INT8) | QNN HTP | 3–6ms | Object detection |
| BERT-Base (128 token, INT8) | QNN HTP | 15–30ms | Text embedding |
| MobileNetV3-Large (224×224 INT8) | GPU | 5–10ms | Fallback |
| MobileNetV3-Large (224×224 INT8) | CPU | 25–45ms | Fallback |

**Why GPU is slower than QNN:** The Hexagon HTP is a purpose-built neural network accelerator.
Adreno GPU handles general SIMD compute — it is faster than CPU but slower than the DSP for
standard INT8 operations.

### LLM (inference-lm module — .litertlm)

| Model | Delegate | Tokens/sec (prefill) | Tokens/sec (decode) |
|---|---|---|---|
| Gemma 2B INT4 | QNN HTP | 80–120 t/s | 25–40 t/s |
| Gemma 2B INT4 | GPU | 30–50 t/s | 10–18 t/s |
| Phi-2 2.7B INT4 | QNN HTP | 60–90 t/s | 20–30 t/s |
| Llama 3.2 1B INT4 | QNN HTP | 120–180 t/s | 40–60 t/s |

**Prefill** = processing the input prompt (parallel — faster).
**Decode** = generating each output token (sequential — slower).

**First-run compilation times (approximate):**
- 1B INT4 model: 15–30 seconds
- 2B INT4 model: 30–60 seconds
- 7B INT4 model: 90–180 seconds

---

## Comparing Delegates

The `BenchmarkTracker` does not automatically run all three delegates. To compare:

1. In `AppConfig`, force a specific delegate:
   ```kotlin
   const val FORCED_DELEGATE: DelegateType? = DelegateType.GPU  // null = auto (QNN → GPU → CPU)
   ```

2. Run the same inference workload (same model, same input data, 50+ warm iterations).

3. The `BenchmarkRepository` stores all results in Room. Export with:
   ```kotlin
   benchmarkRepository.getWarmResults().collect { results ->
       results.groupBy { it.delegateType }
              .mapValues { (_, runs) -> runs.map { it.inferenceLatencyMs }.average() }
              .forEach { (delegate, avgMs) -> Log.i("Bench", "$delegate: ${"%.1f".format(avgMs)}ms") }
   }
   ```

4. Switch to the next delegate and repeat.

**Minimum warm run count:** Use at least 20 warm inferences for a representative average. Thermal
throttling can affect results after sustained inference — run at device ambient temperature.

---

## Memory Profiling Tips

### Heap Snapshot with Android Studio

1. In Android Studio, open **Profiler → Memory**
2. Trigger an inference
3. Capture a heap dump immediately after
4. Filter for `ByteBuffer` and `TensorBuffer` — these are your active tensor allocations

**What to look for:**
- Input tensor buffers should be allocated once and reused between inferences (pool them in `InferenceEngine`)
- A new `ByteBuffer` allocated per inference is a common memory leak pattern
- The LiteRT interpreter itself holds a large allocation (the model weights) — this is expected and stable after loading

### Native Heap

LiteRT and QNN operate partly in native memory (not JVM heap). `Debug.getNativeHeapAllocatedSize()`
gives the total native allocation. The model weights live here.

Typical native heap usage:
- MobileNetV3 INT8 (5 MB model): ~12 MB native heap (weights + workspace)
- Gemma 2B INT4: ~1.2 GB native heap (weights dominate)

### OOM Prevention

For LLM models:
- Check `ActivityManager.getMemoryInfo()` before loading — available native memory should be
  at least 1.5× the model size
- Call `engine.close()` explicitly when done; do not rely on GC
- The `LmEngineManager` in `inference-lm/` manages this lifecycle when used correctly

---

## Benchmark Data Persistence

`BenchmarkTracker` stores all results in a Room database (`BenchmarkDatabase`).

Query examples:
```kotlin
// Average warm latency for last 100 QNN runs
benchmarkDao.getAverageWarmLatency(DelegateType.QNN_HTP, limit = 100)

// All cold runs (model compilation events)
benchmarkDao.getColdRuns()

// Latency over time (for a chart)
benchmarkDao.getResultsSince(timestamp = System.currentTimeMillis() - 3_600_000)
```

Export to CSV for analysis outside the app:
```kotlin
val csv = benchmarkRepository.exportToCsv()
File(context.getExternalFilesDir(null), "benchmark_${System.currentTimeMillis()}.csv")
    .writeText(csv)
```

---

## Official Benchmarking References

- [LiteRT Benchmarking Tool](https://ai.google.dev/edge/litert/performance/measurement) — Google's official CLI benchmark tool
- [Qualcomm AI Hub — Model Performance](https://aihub.qualcomm.com) — Pre-measured performance for QNN-optimized models on real Snapdragon hardware
- [Android Memory Profiler](https://developer.android.com/studio/profile/memory-profiler) — Android Studio built-in memory tooling
