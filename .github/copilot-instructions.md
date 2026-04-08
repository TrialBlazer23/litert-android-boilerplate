# GitHub Copilot Instructions — litert-android-boilerplate

Read AGENTS.md before making any suggestions. These instructions summarize the most critical constraints.

## Tech Stack

- Kotlin 2.0.21 / AGP 8.7.3 / Compose BOM 2024.12.01
- LiteRT `com.google.ai.edge.litert:litert:1.4.0` (Interpreter API)
- LiteRT-LM `com.google.ai.edge.litertlm:litertlm-android:latest.release`
- QNN delegate `com.qualcomm.qti:qnn-litert-delegate:2.44.0`
- Hilt 2.51.1, Coroutines 1.8.1, Room 2.6.1
- Min SDK 26, Target SDK 35
- All versions in `gradle/libs.versions.toml`

## Hard Constraints

### No Placeholders
Never suggest `TODO()`, `NotImplementedError()`, empty function bodies, or stub implementations.
Every suggested function must be fully implemented.

### No Old TFLite Namespace
Never suggest `org.tensorflow.lite.*` imports. Always use `com.google.ai.edge.litert.*`.

### LiteRT-LM API Shape — Critical
The current LiteRT-LM API uses:
- `EngineConfig` as a **data class** (no Builder)
- `Engine(config)` constructor (not `Engine.createFromOptions`)
- `engine.createConversation()` (not `createSession`)
- `conversation.sendMessageAsync(prompt)` returning `Flow<Message>` (not `addQueryChunk`)
- Model paths must be **absolute device paths** — `assets://` is NOT supported

Any suggestion using the old API shape must be discarded.

### Delegate Priority
Suggestions must respect: QNN NPU → GPU → CPU (XNNPACK). NNAPI is excluded (deprecated, Android 15).

### QNN Packaging
The app `build.gradle.kts` must include:
```kotlin
packagingOptions { jniLibs { useLegacyPackaging = true } }
```
Without this, QNN `.so` files are compressed and unloadable at runtime.

## Module Boundaries

- `inference/` — Interpreter API only; no LLM, no UI
- `inference-lm/` — LiteRT-LM only; no classical inference, no UI
- `app/` — UI and DI wiring only; delegates to the two inference modules

Do not suggest code that crosses these boundaries.

## Tests

Every public function needs a unit test. Tests go in `src/test/` (JVM) or `src/androidTest/` (instrumented).
Test method names: `` `should [outcome] when [condition]`() ``
