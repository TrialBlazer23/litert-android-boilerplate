# DECISION-LOG.md — litert-android-boilerplate

Every major technical decision is documented here with its rationale and alternatives considered.
This is a reference document — read it before proposing architectural changes.

---

## 1. Why LiteRT Interpreter API (1.x) Over CompiledModel (2.x) for the inference module

### Decision
Use `com.google.ai.edge.litert:litert:1.4.0` with the `InterpreterApi` for classical ML inference.

### Rationale
The LiteRT 2.x `CompiledModel` API was introduced as a higher-level abstraction, but as of April
2026 it has significantly less documentation, fewer working community examples, and its QNN delegate
integration is less battle-tested than the 1.x `InterpreterApi` path.

The `InterpreterApi` in 1.4.0:
- Has well-documented QNN, GPU, and CPU delegate integration
- Has mature support for tensor input/output management
- Is supported by the official LiteRT benchmarking tools
- Has thousands of production deployments to reference

The `CompiledModel` API:
- Is the direction Google is moving toward
- Has significantly less third-party documentation as of this writing
- Delegate support for QNN is not as clearly documented

### When to Revisit
Reconsider upgrading to the CompiledModel API when:
1. LiteRT 2.x reaches a stable 2.0.0 release (currently 2.x is pre-release)
2. Official QNN integration examples exist for CompiledModel
3. The litert-community on HuggingFace publishes verified models for CompiledModel

### Impact
The `inference/` module is isolated behind the `InferenceEngine` interface. When the time comes
to migrate, only the implementation (`LiteRtInferenceEngine`) needs to change, not any callers.

---

## 2. Why LiteRT-LM for the LLM Track

### Decision
Use `com.google.ai.edge.litertlm:litertlm-android:latest.release` for generative AI inference.

### Rationale
LiteRT-LM is Google's first-party Android SDK for running `.litertlm` format LLMs on device.
It provides:
- NPU (QNN Hexagon), GPU (Adreno), and CPU backends
- Native Kotlin coroutine / Flow integration for streaming
- Context window management
- Multi-turn conversation state

### Alternatives Considered

**MediaPipe LLM Inference Task**
MediaPipe uses the same underlying LiteRT-LM runtime but exposes a `.task` model format with a
higher-level API. It is simpler to set up but less flexible — you cannot directly control backend
selection or implement custom streaming behavior.
Decision: Not chosen because this boilerplate prioritizes control and production flexibility over
simplicity. See `docs/MODEL-FORMATS.md` for the `.task` format overview.

**Ollama on Android (hypothetical)**
There is no official Ollama Android runtime. Third-party ports exist but are experimental.

**llama.cpp via JNI**
Viable for GGUF models but requires native build toolchain setup, loses NPU acceleration (llama.cpp
does not speak to the Hexagon DSP), and adds significant build complexity. Not appropriate for a
boilerplate targeting QNN NPU performance.

---

## 3. Why Hilt Over Koin

### Decision
Use Hilt 2.51.1 for dependency injection.

### Rationale
Hilt uses annotation processing (`kapt` or `ksp`) to generate DI code at compile time. This means:
- Misconfigured bindings are caught at build time, not at runtime in production
- The generated code is inspectable
- Android Lifecycle integration is built-in (ViewModel, Fragment scoping)

For a boilerplate that developers fork and modify, compile-time safety is the right trade-off.
A developer who removes a module or changes a binding gets an error during `./gradlew build`, not
a `NullPointerException` on a user's device.

Koin (3.x) is runtime reflection-based. It is simpler to configure but errors surface later.
For projects where developer ergonomics are the priority and the module graph is small, Koin is
reasonable. That is not the design goal of this boilerplate.

---

## 4. Why NNAPI Is Excluded

### Decision
NNAPI is not included in the delegate fallback chain.

### Rationale
Android 15 (API 35) deprecates the Android Neural Networks API (NNAPI). The Android developer
documentation at https://developer.android.com/ndk/guides/neuralnetworks states that NNAPI is
deprecated and subject to removal in a future Android release.

This boilerplate targets SDK 35. Including a deprecated API in production code creates a future
maintenance burden and may cause runtime warnings or failures on newer devices.

The QNN delegate provides Hexagon NPU access on Snapdragon devices far more efficiently than NNAPI
would, and GPU provides a better fallback than NNAPI on non-Snapdragon devices.

---

## 5. Why Two Separate Modules (inference vs inference-lm)

### Decision
Classical ML and LLM inference are in separate Gradle modules with separate APIs.

### Rationale

**API incompatibility:** The LiteRT Interpreter API works with synchronous tensor operations —
you write input tensors, run inference, read output tensors. The LiteRT-LM API is fundamentally
different — it manages conversation context, accepts string prompts, and returns streaming token
responses via `Flow<Message>`. These two models of computation cannot share a sensible API surface.

**Dependency isolation:** A project using only classical ML inference (e.g. object detection) does
not need the LiteRT-LM dependency (~20 MB). Separate modules allow consumers to add only what they
need.

**Replaceability:** If a better LLM runtime emerges, `inference-lm/` can be replaced without
touching `inference/`. The interfaces are the boundaries.

**Test isolation:** JVM unit tests for the Interpreter API path do not need LiteRT-LM's native
library initialized. Separate modules make test setup simpler.

---

## 6. Why These Specific Version Pins

### LiteRT 1.4.0
The most recent stable 1.x release as of April 2026. Pinned rather than using `latest.release`
because the API surface must remain stable for consumers who fork this boilerplate.

### QNN Delegate 2.44.0
Matches QAIRT SDK 2.44, which provides HTP v73 `.so` files for the SM8550 target. The delegate
version and SDK version must match for the JNI bridge to function correctly.

### LiteRT-LM `latest.release`
The 0.x API is still evolving. Pinning to a specific 0.x patch version risks the pin becoming
stale within weeks. Using `latest.release` keeps the LM module on the most recent stable release.
This is an acceptable trade-off while the API is pre-1.0. Pin to a specific version once 1.0 ships.

### Compose BOM 2024.12.01
BOM-based versioning is the recommended approach. The December 2024 BOM is stable with Kotlin 2.0.21.
Individual Compose library versions are not pinned in `build.gradle.kts` — they all come from the BOM.

---

## 7. Why Absolute Paths for LiteRT-LM Models

### Decision
`.litertlm` model files are loaded from absolute device paths, not from `assets://`.

### Rationale
This is not a choice — it is an API constraint. The LiteRT-LM `EngineConfig` accepts a `modelPath`
string that must be an absolute filesystem path. The `Engine` implementation does not support
Android content URIs or `assets://` URIs.

Furthermore, even if the API supported assets, a typical `.litertlm` LLM model is 1–8 GB. APK
assets are not an appropriate delivery mechanism for files of this size.

### Consequence
Developers must push `.litertlm` models to device storage and configure `AppConfig.LM_MODEL_PATH`
with the absolute path. This is documented in README.md, MEMORY.md, and docs/MODEL-FORMATS.md.

---

## 8. Why useLegacyPackaging = true for QNN

### Decision
The `app/build.gradle.kts` includes `jniLibs { useLegacyPackaging = true }`.

### Rationale
When `useLegacyPackaging` is `false` (the default in AGP 8.x), native `.so` files are compressed
inside the APK. At runtime, `System.loadLibrary("QnnHtp")` fails because the compressed library
cannot be opened via a file descriptor — it must be uncompressed on disk.

QNN's `libQnnHtp.so` and related files are particularly sensitive to this because the Hexagon HTP
architecture loads the skel `.so` via a side-loading mechanism that requires uncompressed storage.

With `useLegacyPackaging = true`, `.so` files are stored uncompressed in the APK (increasing APK
size by 5–15 MB) but are loadable directly at runtime.

This is a known requirement documented in the QNN SDK documentation:
https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk

---

## 9. Why litert-torch Is the Recommended Conversion Pipeline

### Decision
`tools/conversion/working/` contains the HuggingFace → litert-torch pipeline as the primary
supported conversion path.

### Rationale
The `litert-torch` Python package (from the Google AI Edge team) is the officially supported tool
for converting HuggingFace models to both `.tflite` and `.litertlm` formats. It:
- Is maintained by the same team that builds LiteRT
- Produces models with correct operator coverage for QNN compilation
- Supports quantization configurations compatible with the HTP delegate

ONNX → onnx2tf is a community tool and does not work for LLMs. GGUF → `.litertlm` requires
dequantization (lossy) and reassembly — it is placed in `tools/conversion/experimental/` because
it can produce usable models but the quality degradation is not predictable.

The TF SavedModel → TFLiteConverter path is also working and stable for TensorFlow-native models.
