# AGENTS.md — litert-android-boilerplate

Every AI agent that opens this repository must read this file in full before writing any code.
This file is the single source of truth for all development rules, API shapes, and architectural decisions.

---

## Project Overview

**litert-android-boilerplate** is a production-ready Android boilerplate for on-device AI inference
using Google LiteRT. It is optimized for Samsung Galaxy S23 Ultra (Snapdragon SM8550 / Hexagon HTP v73)
but is portable to any ARM64 Android device.

Primary target: Android engineers who want to ship on-device AI features without building inference
infrastructure from scratch. They fork this repo, replace the `app/` module UI, drop in their model,
and ship.

**This is not a tutorial codebase.** Every function must be complete and production-ready.

---

## Tech Stack (Pinned Versions — Do Not Change Without Updating STACK.md)

| Component | Version / Coordinates |
|---|---|
| Kotlin | 2.0.21 |
| Android Gradle Plugin (AGP) | 8.7.3 |
| Compose BOM | 2024.12.01 |
| Min SDK | 26 |
| Target SDK | 35 |
| Compile SDK | 35 |
| LiteRT (Interpreter API) | `com.google.ai.edge.litert:litert:1.4.0` |
| LiteRT GPU delegate | `com.google.ai.edge.litert:litert-gpu:1.2.0` |
| LiteRT-LM | `com.google.ai.edge.litertlm:litertlm-android:latest.release` (tracks 0.10.x) |
| QNN delegate | `com.qualcomm.qti:qnn-litert-delegate:2.44.0` |
| QNN runtime | `com.qualcomm.qti:qnn-runtime:2.44.0` |
| Hilt | 2.51.1 |
| Room | 2.6.1 |
| Coroutines | 1.8.1 |
| JUnit5 | 5.10.2 |

All versions are declared in `gradle/libs.versions.toml`. Do not hardcode version strings in module
`build.gradle.kts` files — reference the version catalog.

---

## Module Descriptions and Boundaries

### `inference/` — Classical ML Inference

**Responsibility:** Run `.tflite` models on device using the LiteRT `InterpreterApi`.

**Contains:**
- `core/` — `InferenceEngine` interface and `LiteRtInferenceEngine` implementation
- `delegate/` — `DelegateFactory`, `DelegateType` enum, fallback logic
- `model/` — `ModelConfig` data class, `ModelLoader`, `InferenceResult` data class
- `benchmark/` — `BenchmarkTracker` for per-inference timing

**Does not contain:** Any UI, ViewModel, or LLM/generative AI code. UI and LLM concerns live in
`app/` and `inference-lm/` respectively.

**API surface:** `InferenceEngine` is the only public interface consumers use. Everything else is
`internal`.

### `inference-lm/` — LLM Inference

**Responsibility:** Run `.litertlm` models using the LiteRT-LM `Engine` API.

**Contains:**
- `engine/` — `LmEngineManager` (lifecycle-aware init/teardown)
- `conversation/` — `ConversationSession`, message streaming via `Flow<Message>`
- `backend/` — `BackendResolver` (resolves `Backend.NPU`, `Backend.GPU`, or `Backend.CPU`)

**Does not contain:** Any classical `.tflite` inference, UI, or ViewModel. Keep LLM concerns isolated.

**API surface:** `LmEngineManager` and `ConversationSession` are the public interfaces.

### `app/` — Sample Application

**Responsibility:** Wire `inference` and `inference-lm` into a runnable Android app.
Demonstrates Hilt DI wiring, basic Compose UI, and `AppConfig`.

**Replace this module** when forking. The inference modules are stable; only `app/` changes per project.

### `tools/` — Conversion Scripts

**Responsibility:** Python scripts for converting models to `.tflite` or `.litertlm` formats.

- `tools/conversion/working/` — Proven pipelines (HuggingFace → litert-torch, TF SavedModel → TFLiteConverter)
- `tools/conversion/experimental/` — Community hacks (GGUF → .litertlm, ONNX → .tflite). Clearly labeled unstable.

---

## Code Quality Rules

### No Stubs or Placeholders

Never write any of the following:
```kotlin
TODO("implement this")
throw NotImplementedError()
// placeholder
```

Every function must be fully implemented. Every data value must be realistic.

### File Size Limit

No file exceeds 300 lines. If a file approaches this limit, split it into focused modules.

### Naming

- Kotlin: `PascalCase` classes, `camelCase` functions and variables, `SCREAMING_SNAKE_CASE` constants
- Files: one top-level class or interface per file, named identically to the type
- Test files: `[ClassName]Test.kt` in `src/test/` or `src/androidTest/`

### Documentation

Every file starts with:
```kotlin
/**
 * [FileName] — [What this file does in one sentence]
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: [Date]
 */
```

Every public function has a KDoc with: what it does, parameters, return value, exceptions thrown.

### 300-Line Enforcement

If adding code would push a file past 300 lines, extract a new focused class before adding.

---

## LiteRT Interpreter API Rules (inference module)

### Correct Import

```kotlin
import com.google.ai.edge.litert.InterpreterApi
import com.google.ai.edge.litert.InterpreterApi.Options
```

### Correct Initialization

```kotlin
val options = InterpreterApi.Options().apply {
    addDelegateFactory(delegateFactory.build())
}
val interpreter = InterpreterApi.create(modelBuffer, options)
```

### Forbidden Patterns — Will Cause Build or Runtime Failures

```kotlin
// WRONG: Old TFLite group ID — package no longer exists
import org.tensorflow.lite.Interpreter

// WRONG: Deprecated delegate construction
import org.tensorflow.lite.gpu.CompatibilityList

// WRONG: Deprecated support library
import org.tensorflow.lite.support.common.FileUtil
```

Use `com.google.ai.edge.litert.*` for all LiteRT imports. The `org.tensorflow.lite` namespace is
the old TFLite library and is not a dependency in this project.

---

## LiteRT-LM API Rules (inference-lm module)

The LiteRT-LM API changed significantly from early previews. Use only the shapes documented here.

### Correct API Shape

```kotlin
// EngineConfig is a DATA CLASS — not a Builder
val config = EngineConfig(
    modelPath = "/absolute/path/to/model.litertlm",
    backend = BackendResolver.resolve(context)
)

// Engine constructor — NOT Engine.createFromOptions(...)
val engine = Engine(config)

// Conversation — NOT createSession(...)
val conversation = engine.createConversation()

// Sending messages — returns Flow<Message>
val responseFlow: Flow<Message> = conversation.sendMessageAsync("Hello")
responseFlow.collect { message ->
    // handle streaming token
}
```

### Backend Construction

```kotlin
// NPU (Qualcomm Hexagon) — requires nativeLibraryDir for QNN .so resolution
Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)

// GPU
Backend.GPU()

// CPU
Backend.CPU()
```

### Absolute Paths Only

LiteRT-LM does **not** support `assets://` URIs. Model paths must be absolute device paths.

```kotlin
// CORRECT
modelPath = "/data/user/0/com.example.app/files/model.litertlm"

// WRONG — will throw at runtime
modelPath = "assets://models/model.litertlm"
```

### Forbidden Patterns

```kotlin
// WRONG: Old builder API — does not exist in current SDK
Engine.createFromOptions(...)
EngineConfig.Builder().setModelPath(...).build()

// WRONG: Deprecated session API
engine.createSession(...)

// WRONG: Deprecated query method
conversation.addQueryChunk(...)

// WRONG: Old sync response (no streaming)
conversation.generateResponse()
```

---

## QNN Integration Rules

### Gradle Coordinates

```kotlin
// In gradle/libs.versions.toml:
qnn-delegate = "com.qualcomm.qti:qnn-litert-delegate:2.44.0"
qnn-runtime  = "com.qualcomm.qti:qnn-runtime:2.44.0"
```

### Native Library Packaging

QNN `.so` files must be packaged with `useLegacyPackaging = true` or they will be compressed
in the APK and cannot be opened by `System.loadLibrary`:

```kotlin
// app/build.gradle.kts
android {
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

### .so File Placement

```
libs/qnn/arm64-v8a/
├── libQnnHtp.so
├── libQnnHtpV73Stub.so    ← SM8550 / Snapdragon 8 Gen 2
├── libQnnHtpV73Skel.so
├── libQnnHtpPrepare.so
├── libQnnSystem.so
└── libQnnTFLiteDelegate.so
```

These files are not committed to git (`.gitignore` excludes `libs/qnn/`).
The developer must obtain them from QAIRT SDK 2.44. See `docs/QNN-SETUP.md`.

### Runtime Verification

Always verify QNN is actually active before reporting it as selected:

```kotlin
fun isDelegateActive(interpreter: InterpreterApi): Boolean {
    // Run a trivial inference and check timing; QNN is consistently < 5ms on SM8550
    // for single-token operations. Log the active delegate name from interpreter.
}
```

---

## Delegate Priority — Enforce in This Exact Order

```
QNN NPU → GPU → CPU (XNNPACK)
```

**NNAPI is not included.** NNAPI is deprecated in Android 15 (API 35) and was removed from
this boilerplate intentionally. Do not add NNAPI support.

Implementation pattern in `DelegateFactory`:

```kotlin
fun build(): List<DelegateFactory> {
    val delegates = mutableListOf<DelegateFactory>()
    try {
        delegates.add(buildQnnDelegate(context))
    } catch (e: Exception) {
        Timber.w(e, "QNN delegate unavailable, skipping")
    }
    try {
        delegates.add(GpuDelegateFactory())
    } catch (e: Exception) {
        Timber.w(e, "GPU delegate unavailable, skipping")
    }
    // CPU/XNNPACK is always available — no try/catch needed; it's the built-in fallback
    return delegates
}
```

Log which delegate was ultimately selected at INFO level.

---

## Error Handling Policy

Every function that can fail must handle failure explicitly:

- **Delegate initialization failures** — catch, log with context, continue to next delegate
- **Model loading failures** — throw `ModelLoadException` with the path and cause; never return null
- **Inference failures** — catch `IllegalArgumentException` (shape mismatch), `RuntimeException` (OOM); wrap in `InferenceException`
- **LiteRT-LM engine failures** — catch during `Engine(config)` construction; this is where NPU compilation happens; may take 10–60s on first run
- **File I/O** — catch `IOException`, `SecurityException`; never silently swallow

Never return `null` to signal failure. Use `Result<T>`, throw a typed exception, or return a sealed class. The choice must be consistent within a module.

---

## Test Requirements

### What Must Be Tested

Every public function in `inference/` and `inference-lm/` must have:
- A unit test for the happy path
- A unit test for each error condition
- Edge case tests (empty input, null model buffer, wrong tensor shape)

### Test Location

```
inference/src/test/java/.../inference/      ← JVM unit tests (no device needed)
inference/src/androidTest/java/...          ← instrumented tests (device/emulator)
inference-lm/src/test/java/.../lm/
app/src/androidTest/java/...
```

### Running Tests

```bash
# JVM unit tests
./gradlew :inference:test :inference-lm:test :app:test

# Instrumented tests (connected device required)
./gradlew :inference:connectedAndroidTest :app:connectedAndroidTest
```

Both commands must pass before any commit is considered done.

### Test Naming

```kotlin
// Kotlin test function naming convention
@Test
fun `should fall back to GPU when QNN delegate throws`() { ... }

@Test
fun `should throw ModelLoadException when file does not exist`() { ... }
```

---

## File Structure Reference

```
litert-android-boilerplate/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml              ← Test + lint on every push/PR
│   │   └── release.yml         ← Build release APK on version tag
│   └── copilot-instructions.md
├── app/                        ← Replace when forking
│   └── src/main/java/.../litert/
│       ├── MainActivity.kt
│       ├── MainViewModel.kt
│       ├── di/                 ← Hilt modules
│       ├── ui/                 ← Compose screens and components
│       └── config/AppConfig.kt
├── inference/
│   └── src/main/java/.../inference/
│       ├── core/               ← InferenceEngine interface + LiteRtInferenceEngine
│       ├── delegate/           ← DelegateFactory, DelegateType
│       ├── model/              ← ModelConfig, ModelLoader, InferenceResult
│       └── benchmark/          ← BenchmarkTracker
├── inference-lm/
│   └── src/main/java/.../lm/
│       ├── engine/             ← LmEngineManager
│       ├── conversation/       ← ConversationSession
│       └── backend/            ← BackendResolver
├── libs/
│   └── qnn/arm64-v8a/         ← Drop QNN .so files here (not in git)
├── tools/
│   ├── conversion/
│   │   ├── working/            ← Proven conversion pipelines
│   │   └── experimental/       ← Risky pipelines — clearly marked
│   └── scripts/
├── docs/
│   ├── DECISION-LOG.md
│   ├── QNN-SETUP.md
│   ├── BENCHMARKING.md
│   ├── FORK-QUICKSTART.md
│   └── MODEL-FORMATS.md
├── gradle/
│   ├── libs.versions.toml      ← Version catalog — all deps declared here
│   └── wrapper/
├── AGENTS.md                   ← This file
├── CLAUDE.md
├── .cursorrules
├── MEMORY.md
├── STACK.md
└── README.md
```

---

## Commit Message Format

```
type(scope): short description

Types: feat, fix, test, docs, chore, refactor, perf
Scopes: inference, inference-lm, app, tools, ci, deps

Examples:
  feat(inference): implement QNN delegate fallback chain
  fix(inference-lm): handle model load timeout on first QNN compilation
  test(inference): add edge case tests for shape mismatch
  docs: update QNN-SETUP with HTP v75 instructions
  chore(deps): pin LiteRT-LM to 0.10.2
```

---

## Agent Workflow

1. Read `AGENTS.md` (this file), `STACK.md`, and `MEMORY.md` before starting any work.
2. Understand the full scope of the task before writing a single line of code.
3. Implement fully — no stubs, no `TODO`, no empty functions.
4. Write tests for every public function before marking work done.
5. Run `./gradlew test` and verify it passes.
6. Update `MEMORY.md` — add completed work to "Completed Features", update "Known Issues" if any found.
7. Suggest a commit message.

If a genuine ambiguity exists (two valid architectural approaches, unclear business logic), stop and ask. Do not make a guess that will require a rewrite.
