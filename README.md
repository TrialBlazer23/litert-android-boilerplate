# litert-android-boilerplate

Production-ready Android boilerplate for on-device AI inference using Google LiteRT, optimized for Samsung Galaxy S23 Ultra (Snapdragon SM8550 / Hexagon HTP v73).

---

## What This Is

This is a **reusable boilerplate**, not a tutorial. Fork it, drop in your model, swap the UI, and ship. The inference infrastructure is already built:

- **`inference/`** — Classical ML inference via the LiteRT Interpreter API (object detection, image classification, embeddings, custom models)
- **`inference-lm/`** — LLM inference via the LiteRT-LM API (chat, streaming, generative AI)
- **`app/`** — Sample Android app wiring everything together; replace with your own UI when forking

The delegate stack (QNN NPU → GPU → CPU) is implemented and falls back automatically. You do not need to write delegate management code.

---

## Module Overview

| Module | Purpose | API |
|---|---|---|
| `inference` | Classical ML: image, audio, structured data | LiteRT Interpreter API 1.4.0 |
| `inference-lm` | LLM / generative AI: chat, streaming | LiteRT-LM API 0.10.x |
| `app` | Sample UI, Hilt DI wiring, AppConfig | Jetpack Compose |

### inference module

Wraps the LiteRT `InterpreterApi` with:
- `InferenceEngine` interface — single entry point for running models
- `DelegateFactory` — tries QNN → GPU → CPU with structured fallback
- `ModelLoader` — loads `.tflite` files from assets or an absolute path
- `InferenceResult` — typed result with latency metadata
- `BenchmarkTracker` — captures per-inference timing and delegate used

### inference-lm module

Wraps the LiteRT-LM `Engine` with:
- `LmEngineManager` — lifecycle-aware engine init and teardown
- `ConversationSession` — manages context, sends messages, returns `Flow<Message>`
- `BackendResolver` — resolves the correct `Backend` type for the running device
- Streaming support via Kotlin coroutine `Flow`

---

## Quick Start

### Prerequisites

| Requirement | Version |
|---|---|
| Android Studio | Ladybug (2024.2) or later |
| Android SDK | 35 (compile + target) |
| Min device SDK | 26 (Android 8) |
| JDK | 17 |
| QNN SDK (for NPU) | QAIRT 2.44 |

### 1. Clone

```bash
git clone https://github.com/necessity-labs/litert-android-boilerplate.git
cd litert-android-boilerplate
```

### 2. Set Up QNN Binaries (for NPU support)

The QNN delegate `.so` files are device-specific and cannot be distributed in this repo. For SM8550 (Galaxy S23 Ultra):

```
libs/qnn/arm64-v8a/
├── libQnnHtp.so
├── libQnnHtpV73Stub.so
├── libQnnHtpV73Skel.so
├── libQnnHtpPrepare.so
├── libQnnSystem.so
└── libQnnTFLiteDelegate.so
```

See [docs/QNN-SETUP.md](docs/QNN-SETUP.md) for full acquisition and placement instructions.

If QNN binaries are not present, the engine falls back to GPU, then CPU — the build does not fail.

### 3. Place Your Model

**Classical ML (`.tflite`):**
```
app/src/main/assets/models/your_model.tflite
```
Models under 100 MB are fine in assets. For larger models, place them at an absolute path and configure `ModelConfig.modelPath` accordingly.

**LLM (`.litertlm`):**
```
/sdcard/models/your_model.litertlm   ← absolute device path
```
LiteRT-LM does **not** support the `assets://` URI. The model must be at an absolute path on device. Update `AppConfig.lmModelPath` with the correct path.

### 4. Configure AppConfig

Open `app/src/main/java/.../litert/config/AppConfig.kt` and set:

```kotlin
object AppConfig {
    const val MODEL_FILENAME = "your_model.tflite"      // classical ML model
    const val LM_MODEL_PATH = "/sdcard/models/your.litertlm"  // absolute path
    const val ENABLE_BENCHMARKING = true                // toggle benchmark overlay
}
```

### 5. Build and Run

```bash
./gradlew assembleDebug
```

Or use **Run** in Android Studio targeting a connected Galaxy S23 Ultra (or any ARM64 device).

---

## Delegate Support Matrix

| Delegate | Hardware Required | Device Example | Notes |
|---|---|---|---|
| QNN NPU (HTP v73) | Snapdragon 8 Gen 2 | Galaxy S23 Ultra | Requires QNN `.so` files; fastest |
| QNN NPU (HTP v75) | Snapdragon 8 Gen 3 | Galaxy S24 Ultra | Requires v75 `.so` files |
| GPU (Adreno) | Any Adreno GPU | Most Qualcomm phones | No extra files needed |
| CPU (XNNPACK) | Any ARM64 | Any Android 8+ device | Always available; slowest |

The delegate is selected at runtime in priority order. You can force a specific delegate in `AppConfig`.

---

## Where to Place Files

```
app/src/main/assets/models/     ← .tflite files < 100 MB
libs/qnn/arm64-v8a/             ← QNN .so files (not committed to git)
/sdcard/models/                 ← .litertlm files (on device, not in repo)
```

Neither QNN `.so` files nor model weights are committed to this repository. Both are in `.gitignore`.

---

## Running Benchmarks

Enable benchmarking in `AppConfig`:
```kotlin
const val ENABLE_BENCHMARKING = true
```

The `BenchmarkTracker` in the `inference` module records per-inference latency, memory delta, and the active delegate. Output appears in Logcat under the tag `LiteRTBenchmark` and optionally as an on-screen overlay.

See [docs/BENCHMARKING.md](docs/BENCHMARKING.md) for expected performance ranges and memory profiling tips.

---

## Forking for a New Project

1. Fork or clone this repo
2. Rename the package from `com.necessitylabs.litert` to your own
3. Replace the `app/` UI with your screens
4. Update `AppConfig` with your model paths
5. Drop your `.tflite` or `.litertlm` model in the right location
6. Build and run

See [docs/FORK-QUICKSTART.md](docs/FORK-QUICKSTART.md) for the full step-by-step guide.

---

## What Is Production-Ready vs Experimental

### Production-Ready

| Component | Status |
|---|---|
| QNN NPU delegate (SM8550 / HTP v73) | Ready |
| GPU delegate | Ready |
| CPU / XNNPACK fallback | Ready |
| LiteRT Interpreter API (classical ML) | Ready |
| LiteRT-LM streaming (LLM) | Ready |
| Hilt dependency injection | Ready |
| Jetpack Compose UI scaffold | Ready |

### Experimental (in `tools/conversion/experimental/`)

| Pipeline | Status | Risk |
|---|---|---|
| GGUF → safetensors → `.litertlm` | Experimental | Lossy dequantization; community hack |
| ONNX → onnx2tf → `.tflite` | Experimental | Community tool; not for LLMs |

### Working Conversion Pipelines (in `tools/conversion/working/`)

| Pipeline | Status |
|---|---|
| HuggingFace → litert-torch → `.tflite` / `.litertlm` | Working |
| TF SavedModel → TFLiteConverter → `.tflite` | Working |

---

## Known Limitations

- **LiteRT-LM absolute paths only** — `.litertlm` models cannot load from `assets://`. Place models on device storage and provide the absolute path.
- **QNN binaries are device-family specific** — HTP v73 `.so` files (SM8550) will not work on SM8650 (v75). You must match the `.so` files to the target Snapdragon generation.
- **NNAPI not supported** — NNAPI is deprecated in Android 15 and is not included in this boilerplate.
- **Large models need external storage** — Models above ~100 MB should not be in `app/src/main/assets/`. Use device storage with an absolute path.
- **QNN context binary caching** — First-run model compilation with QNN can take 10–60 seconds. Subsequent runs load from cached context binaries. This is expected behavior.

---

## Extended Documentation

| Document | Contents |
|---|---|
| [docs/QNN-SETUP.md](docs/QNN-SETUP.md) | Acquiring and placing QNN binaries, runtime verification, troubleshooting |
| [docs/BENCHMARKING.md](docs/BENCHMARKING.md) | Benchmark output, expected numbers, memory profiling |
| [docs/FORK-QUICKSTART.md](docs/FORK-QUICKSTART.md) | Step-by-step fork guide |
| [docs/MODEL-FORMATS.md](docs/MODEL-FORMATS.md) | .tflite vs .litertlm vs .task, how to get pre-built models |
| [docs/DECISION-LOG.md](docs/DECISION-LOG.md) | Why each architectural decision was made |

---

## Official References

- [LiteRT documentation](https://ai.google.dev/edge/litert)
- [LiteRT-LM documentation](https://ai.google.dev/edge/litert/inference/litert-lm)
- [LiteRT GitHub](https://github.com/google-ai-edge/LiteRT)
- [QNN SDK — Qualcomm AI Engine Direct](https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk)
- [Qualcomm AI Hub (pre-optimized models)](https://aihub.qualcomm.com/)
- [litert-community on HuggingFace](https://huggingface.co/litert-community)

---

## License

Apache 2.0. See [LICENSE](LICENSE).
