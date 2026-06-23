# STACK.md — litert-android-boilerplate

Technology decisions log. Every dependency is listed with its exact version, why it was chosen,
and what alternatives were considered. Agents must not change versions without updating this file
and documenting the reason.

Last updated: 2025-04-28

---

## Language & Build

### Kotlin
- **Version:** 2.0.21
- **Why:** Primary Android language. 2.0.x is the current stable series with K2 compiler, which
  improves compilation speed and provides better type inference for Compose.
- **Alternatives considered:** None — Java is not appropriate for modern Android development with
  Compose.

### Android Gradle Plugin (AGP)
- **Version:** 8.7.3
- **Why:** Required for SDK 35 compile target and Kotlin 2.0.x compatibility. Matches Android
  Studio Ladybug.
- **Alternatives considered:** 8.6.x — avoided because it lacks some Kotlin 2.0 compatibility fixes.

### Gradle Wrapper
- **Version:** 8.9
- **Why:** Required by AGP 8.7.x.

### Kotlin DSL (build.gradle.kts)
- **Why:** Type-safe build files with IDE completion. Preferred over Groovy for new projects.
  Errors surface at compile time rather than runtime.

---

## Core Android

### Min SDK
- **Version:** 26 (Android 8.0)
- **Why:** LiteRT GPU delegate requires API 26+. Going lower would exclude GPU and force CPU-only
  inference on most relevant devices.

### Target SDK / Compile SDK
- **Version:** 35 (Android 15)
- **Why:** Current stable SDK. Required to comply with Google Play requirements for new app
  submissions as of 2025.

---

## Jetpack Compose

### Compose BOM
- **Version:** 2024.12.01
- **Why:** BOM ensures all Compose libraries are version-compatible. December 2024 BOM is the
  current stable series, compatible with Kotlin 2.0.21 and AGP 8.7.x.
- **Coordinate:** `androidx.compose:compose-bom:2024.12.01`
- **Alternatives considered:** Pinning individual Compose libraries — avoided because it requires
  manual version compatibility management.

---

## Inference

### LiteRT (Interpreter API)
- **Version:** 1.4.0
- **Coordinate:** `com.google.ai.edge.litert:litert:1.4.0` (Google Maven)
- **Why:** Stable Interpreter API for classical ML inference (.tflite models). Version 1.x has the
  most documentation, community support, and QNN delegate integration examples. The 2.x CompiledModel
  API exists but is less mature and substantially less documented.
- **Repository:** `google()` maven repository in settings.gradle.kts
- **Alternatives considered:** LiteRT 2.x (CompiledModel API) — deferred; LiteRT 1.x is more stable
  for production use as of April 2026.

### LiteRT GPU Delegate
- **Version:** 1.2.0
- **Coordinate:** `com.google.ai.edge.litert:litert-gpu:1.2.0` (Google Maven)
- **Why:** GPU delegate for Adreno GPU inference. 1.2.0 is the stable version paired with LiteRT
  1.4.0.
- **Note:** GPU version does not track LiteRT version 1:1. Always verify compatibility against the
  LiteRT release notes at https://ai.google.dev/edge/litert/inference/gpu

### LiteRT-LM
- **Version:** latest.release (tracks 0.10.x)
- **Coordinate:** `com.google.ai.edge.litertlm:litertlm-android:latest.release` (Google Maven)
- **Why:** Only first-party Google SDK for running `.litertlm` LLM models on Android. Supports
  streaming via Kotlin Flow and NPU/GPU/CPU backends.
- **Note:** Uses `latest.release` rather than a pinned version because the LiteRT-LM API is still
  evolving rapidly (0.x series). Pin to a specific version once the API stabilizes at 1.0.
- **Alternatives considered:** MediaPipe LLM Inference Task — uses `.task` format (same underlying
  LiteRT-LM but different API surface). Not chosen because LiteRT-LM API is more flexible for
  custom backends and streaming control.

---

## QNN (Qualcomm AI Engine)

### QNN Delegate
- **Version:** 2.44.0
- **Coordinate:** `com.qualcomm.qti:qnn-litert-delegate:2.44.0` (Maven Central)
- **Why:** Enables Hexagon NPU inference on Snapdragon devices. Version 2.44.0 supports both
  HTP v73 (SM8550 / Galaxy S23 series) and HTP v79 (SM8750 / Nothing Phone 3, Galaxy S25 series).
- **Repository:** `mavenCentral()`

### QNN Runtime
- **Version:** 2.44.0
- **Coordinate:** `com.qualcomm.qti:qnn-runtime:2.44.0` (Maven Central)
- **Why:** Runtime support library required alongside the delegate. Must match the delegate version.

### QNN Native Libraries
- **Shared files (all Snapdragon targets):**
  - `libQnnHtp.so`
  - `libQnnHtpPrepare.so`
  - `libQnnSystem.so`
  - `libQnnTFLiteDelegate.so`
- **Device-specific pairs (include all target generations):**
  - HTP v79 (SM8750 / Nothing Phone 3): `libQnnHtpV79Stub.so`, `libQnnHtpV79Skel.so`
  - HTP v73 (SM8550 / Galaxy S23 Ultra): `libQnnHtpV73Stub.so`, `libQnnHtpV73Skel.so`
- **Source:** QAIRT SDK 2.44 — https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk
- **Placement:** `libs/qnn/arm64-v8a/` (not committed to git)

---

## Dependency Injection

### Hilt
- **Version:** 2.51.1
- **Coordinates:**
  - `com.google.dagger:hilt-android:2.51.1`
  - `com.google.dagger:hilt-android-compiler:2.51.1`
  - `androidx.hilt:hilt-navigation-compose:1.2.0`
- **Why:** Compile-time DI verification catches wiring errors at build time, not at runtime. For a
  boilerplate that forks frequently, this is more valuable than Koin's simpler syntax.
- **Alternatives considered:** Koin 3.x — runtime DI, simpler setup, but less safety for large
  module graphs. Deferred for potential future "lite" variant of this boilerplate.

---

## Async & Reactive

### Kotlin Coroutines
- **Version:** 1.8.1
- **Coordinates:**
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1`
- **Why:** Standard Kotlin async mechanism. LiteRT-LM streaming uses `Flow<Message>`, which requires
  coroutines. Inference callbacks are wrapped in suspending functions.
- **Alternatives considered:** RxJava — not appropriate for new Kotlin-first code.

---

## Local Persistence

### Room
- **Version:** 2.6.1
- **Coordinates:**
  - `androidx.room:room-runtime:2.6.1`
  - `androidx.room:room-ktx:2.6.1`
  - `androidx.room:room-compiler:2.6.1`
- **Why:** SQLite persistence for benchmark history, conversation history, and model metadata.
  Room provides compile-time SQL verification and coroutine support.
- **Alternatives considered:** Realm, SQLDelight — Room was chosen for simplicity and first-party
  support.

---

## Testing

### JUnit 5
- **Version:** 5.10.2
- **Coordinates:**
  - `org.junit.jupiter:junit-jupiter:5.10.2`
  - `org.junit.jupiter:junit-jupiter-params:5.10.2`
- **Why:** Parameterized tests and better test lifecycle management than JUnit 4.

### Mockk
- **Version:** 1.13.12
- **Coordinate:** `io.mockk:mockk:1.13.12`
- **Why:** Kotlin-native mocking library. Mocks Kotlin objects and coroutines correctly. Mockito
  does not handle Kotlin-specific patterns (e.g. `object`, `final` by default) without additional
  setup.

### Robolectric
- **Version:** 4.12.2
- **Coordinate:** `org.robolectric:robolectric:4.12.2`
- **Why:** Allows Android framework tests (Context, AssetManager) to run on JVM without a device.
  Used for testing `ModelLoader` which requires a `Context`.

### Espresso
- **Version:** 3.6.1 (via AndroidX Test BOM)
- **Why:** UI integration tests for the `app/` module. Validates the sample UI wiring.

---

## Logging

### Timber
- **Version:** 5.0.1
- **Coordinate:** `com.jakewharton.timber:timber:5.0.1`
- **Why:** Structured logging with tag auto-detection and production no-op capability. All
  delegate selection, fallback events, and inference errors are logged through Timber.
- **Note:** Production builds use a no-op `Tree`. Debug builds use `DebugTree`.

---

## Version Catalog Location

All versions are declared in `gradle/libs.versions.toml`. Module `build.gradle.kts` files
reference the catalog via `libs.*` aliases. No version strings are hardcoded in build files.
