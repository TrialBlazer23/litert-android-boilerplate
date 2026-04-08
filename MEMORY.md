# MEMORY.md — litert-android-boilerplate

Living project memory. Every agent that completes significant work must update the relevant sections.
Be specific — describe what was built, not just that "something was done."

Last updated: 2025-04-28

---

## Project Purpose

Reusable Android boilerplate for on-device AI inference using Google LiteRT.
Primary target device: Samsung Galaxy S23 Ultra (Snapdragon SM8550 / Hexagon HTP v73).

Intended use: Fork this repo, replace `app/` UI, drop in your model, and ship. The inference
infrastructure (delegate chain, model loading, benchmarking, LLM streaming) is already built.

Two separate inference modules:
- `inference/` — classical ML via LiteRT Interpreter API (`.tflite` models)
- `inference-lm/` — generative AI via LiteRT-LM API (`.litertlm` models)

---

## Architecture Decisions

### Dual-module inference split
`inference/` and `inference-lm/` are separate Gradle modules with separate APIs. Rationale: the
Interpreter API and the LiteRT-LM API are fundamentally different (synchronous tensor ops vs. async
streaming conversation). Keeping them in the same module would make the API surface confusing.
See `docs/DECISION-LOG.md` for full rationale.

### LiteRT Interpreter API version 1.4.0 (not CompiledModel 2.x)
The CompiledModel (LiteRT 2.x) API is newer but substantially less documented and has fewer
community examples. The Interpreter API at 1.4.0 is stable, well-tested, and supports all three
delegates (QNN, GPU, CPU). Revisit when LiteRT 2.x stabilizes. See `docs/DECISION-LOG.md`.

### QNN delegate via Maven Central (qnn-litert-delegate:2.44.0)
Using the Maven Central distribution of the QNN delegate rather than bundled `.so` files.
The SDK-level `.so` files (libQnnHtp.so etc.) must still be provided by the developer from QAIRT
SDK 2.44. The Maven artifact handles the Java/JNI bridge.

### NNAPI excluded
Android 15 (API 35) deprecates NNAPI. Since this boilerplate targets SDK 35, NNAPI is not included.
GPU and QNN provide better performance on target hardware. See `docs/DECISION-LOG.md`.

### Hilt for DI (not Koin)
Hilt is compile-time verified via annotation processing, producing better error messages than Koin's
runtime reflection. For a boilerplate that others will modify, compile-time safety is more valuable.

### useLegacyPackaging = true for QNN .so files
QNN shared libraries must not be compressed in the APK. When compressed, `System.loadLibrary` cannot
locate them at runtime. `useLegacyPackaging = true` ensures they are stored uncompressed.

### Absolute paths for LiteRT-LM models
The LiteRT-LM `Engine` does not support `assets://` URIs. Models must be at absolute device paths.
Large LLM weights (typically 1–8 GB) should not be in APK assets regardless. Decision is forced by
the API constraint and reinforced by practical file size limits.

### litert-torch conversion pipeline (working)
HuggingFace → litert-torch is the recommended path for both `.tflite` and `.litertlm` generation.
It is the officially supported pipeline and produces QNN-compatible outputs.

---

## Completed Features

- Initial repo structure scaffolded (app/, inference/, inference-lm/, libs/, tools/, docs/)
- All documentation files created (README.md, AGENTS.md, CLAUDE.md, .cursorrules, copilot-instructions.md, MEMORY.md, STACK.md)
- Extended docs created (docs/DECISION-LOG.md, docs/QNN-SETUP.md, docs/BENCHMARKING.md, docs/FORK-QUICKSTART.md, docs/MODEL-FORMATS.md)
- .gitignore configured for build artifacts, model weights, QNN binaries, IDE files, secrets
- .gitkeep files placed to preserve empty directories (libs/qnn/arm64-v8a/, app/src/main/assets/models/)

---

## In Progress

_(none — initial scaffolding complete)_

---

## Known Issues

_(none identified at initial setup)_

---

## Known Issues Resolved

_(none yet)_

---

## Next Priorities

1. Implement `InferenceEngine` interface and `LiteRtInferenceEngine` in `inference/core/`
2. Implement `DelegateFactory` with QNN → GPU → CPU fallback in `inference/delegate/`
3. Implement `ModelLoader` for assets-based and absolute-path loading in `inference/model/`
4. Implement `BenchmarkTracker` in `inference/benchmark/`
5. Implement `LmEngineManager` in `inference-lm/engine/`
6. Implement `ConversationSession` with `Flow<Message>` streaming in `inference-lm/conversation/`
7. Implement `BackendResolver` in `inference-lm/backend/`
8. Wire everything into `app/` with Hilt modules
9. Set up CI/CD workflows in `.github/workflows/`
10. Write unit and instrumented tests for all modules

---

## User Action Items Pending

### 1. QNN Binary Files
**File:** `libs/qnn/arm64-v8a/`
**What:** Six QNN shared library files required for NPU inference on SM8550.
**Steps:**
1. Download QAIRT SDK 2.44 from https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk
2. Extract and locate: `libQnnHtp.so`, `libQnnHtpV73Stub.so`, `libQnnHtpV73Skel.so`, `libQnnHtpPrepare.so`, `libQnnSystem.so`, `libQnnTFLiteDelegate.so`
3. Copy all six files to `libs/qnn/arm64-v8a/`
4. Rebuild the project
**Note:** Without these files, inference falls back to GPU → CPU. Build succeeds regardless.
**Full guide:** See `docs/QNN-SETUP.md`

### 2. Classical ML Model
**File:** `app/src/main/assets/models/`
**What:** A `.tflite` model file for the `inference` module to run.
**Steps:**
1. Obtain or convert a `.tflite` model (see `docs/MODEL-FORMATS.md`)
2. Copy to `app/src/main/assets/models/your_model.tflite`
3. Update `AppConfig.MODEL_FILENAME` with the filename

### 3. LLM Model
**File:** On device at absolute path (e.g. `/sdcard/models/`)
**What:** A `.litertlm` model for the `inference-lm` module.
**Steps:**
1. Obtain a `.litertlm` model (see `docs/MODEL-FORMATS.md` for sources)
2. Push to device: `adb push your_model.litertlm /sdcard/models/`
3. Update `AppConfig.LM_MODEL_PATH` with the absolute path
