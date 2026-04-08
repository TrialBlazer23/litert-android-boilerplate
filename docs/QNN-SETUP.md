# QNN Setup Guide

This guide explains how to obtain, place, and verify the Qualcomm AI Engine Direct (QNN) native
libraries required for Hexagon NPU inference on Snapdragon devices.

---

## What Files Are Needed

For Samsung Galaxy S23 Ultra (SM8550 / Snapdragon 8 Gen 2 / Hexagon HTP v73), you need six `.so` files:

| File | Purpose |
|---|---|
| `libQnnHtp.so` | Main HTP (Hexagon Tensor Processor) runtime |
| `libQnnHtpV73Stub.so` | User-space stub for HTP v73 (routes calls to DSP) |
| `libQnnHtpV73Skel.so` | DSP-side skeleton for HTP v73 (runs on Hexagon) |
| `libQnnHtpPrepare.so` | Model preparation / context binary compilation |
| `libQnnSystem.so` | QNN system utilities (logging, diagnostics) |
| `libQnnTFLiteDelegate.so` | TFLite/LiteRT delegate bridge (JNI entrypoint) |

All six are required. If any are missing, the QNN delegate will fail to initialize and inference
will fall back to GPU.

---

## Where to Get Them

### Option A: QAIRT SDK (Recommended — Full Control)

The QAIRT SDK (Qualcomm AI Engine Direct Runtime) contains all QNN libraries.

1. Go to https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk
2. Create a free Qualcomm developer account if you don't have one
3. Download **QAIRT 2.44** (match the version in `STACK.md`)
4. Install the SDK or extract the archive
5. Navigate to: `{SDK_ROOT}/lib/aarch64-android/`
6. You will find all six `.so` files listed above

### Option B: Qualcomm Package Manager (QPM)

QPM is Qualcomm's package manager CLI tool.

```bash
# Install QPM (macOS/Linux)
pip install qpm-cli

# Log in with your Qualcomm developer account
qpm-cli login

# Download QAIRT 2.44
qpm-cli download qairt --version 2.44

# Extract and locate the aarch64-android libs
find ./qairt-2.44 -name "libQnnHtp.so"
```

### Option C: Pre-Built from Qualcomm AI Hub

For quick testing, Qualcomm AI Hub sometimes distributes QNN runtime binaries alongside
optimized models. Check https://aihub.qualcomm.com for device-specific runtime packages.

**Important:** Ensure any binaries you download match version 2.44.0 — the version must match
the `qnn-litert-delegate:2.44.0` Maven artifact or the JNI bridge will fail to load.

---

## Placing the Files

After obtaining the six `.so` files, copy them to:

```
litert-android-boilerplate/
└── libs/
    └── qnn/
        └── arm64-v8a/
            ├── libQnnHtp.so
            ├── libQnnHtpV73Stub.so
            ├── libQnnHtpV73Skel.so
            ├── libQnnHtpPrepare.so
            ├── libQnnSystem.so
            └── libQnnTFLiteDelegate.so
```

The `app/build.gradle.kts` is configured to package everything in `libs/qnn/arm64-v8a/` as JNI
libraries. You do not need to modify any build files.

**These files are in `.gitignore` and will not be committed to git.** This is intentional — QNN
binaries have their own Qualcomm license and must be obtained by each developer independently.

---

## Verifying the Files Are Loaded at Runtime

### 1. Check Logcat After App Launch

Filter Logcat for the tag `LiteRTDelegate`:

```
adb logcat -s LiteRTDelegate
```

On successful QNN initialization, you should see:
```
D/LiteRTDelegate: QNN delegate selected — backend: HTP v73 (SM8550)
D/LiteRTDelegate: Delegate latency (first inference): 847ms [context binary compilation]
D/LiteRTDelegate: Delegate latency (warm inference): 3.2ms
```

The first inference is slow (up to 60 seconds for large models) because QNN compiles the model
into a hardware-specific context binary. Subsequent inferences use the cached binary and are fast.

### 2. Check the Active Delegate Programmatically

The `InferenceEngine` exposes the active delegate type after first inference:

```kotlin
val result = inferenceEngine.runInference(inputBuffer)
Log.i("App", "Delegate used: ${result.delegateType}")  // "QNN_HTP" | "GPU" | "CPU"
```

### 3. Run the Built-In Benchmark

Enable benchmarking in `AppConfig`:
```kotlin
const val ENABLE_BENCHMARKING = true
```

The benchmark overlay shows active delegate and per-inference latency. On SM8550 with QNN:
- MobileNetV3 classification (224×224): ~2ms
- Object detection (320×320): ~5ms
- See `docs/BENCHMARKING.md` for full reference numbers

---

## Troubleshooting

### "Failed to load QNN delegate: UnsatisfiedLinkError"

**Cause:** The `.so` files are missing from `libs/qnn/arm64-v8a/`, or the APK was built without them.

**Fix:**
1. Verify the six files exist in `libs/qnn/arm64-v8a/` — `ls libs/qnn/arm64-v8a/`
2. Clean and rebuild: `./gradlew clean assembleDebug`
3. Check that `app/build.gradle.kts` has `jniLibs { useLegacyPackaging = true }`

### "QNN delegate loaded but falling back to GPU"

**Cause:** The device does not have a Hexagon HTP DSP, or the skel `.so` version does not match
the device's DSP firmware.

**Fix:**
1. Confirm the device is SM8550 (Galaxy S23 Ultra / S23+) — `adb shell getprop ro.product.model`
2. Confirm you are using the v73 `.so` files (not v68, v69, v75)
3. Check Logcat for `libQnnHtpV73Skel.so` load errors

### "First inference takes 30–60 seconds"

This is **expected** behavior on first run with QNN. The Hexagon compiler is translating the model
into a context binary (`.bin` file) cached in the app's files directory. Subsequent launches use
the cached binary and start in milliseconds.

If this is unacceptable for your use case, implement a loading screen and warm up the engine on
app launch in a background coroutine. Cache the context binary location in `Room` so you can
detect whether warm-up has already been done.

### "SIGSEGV in libQnnHtp.so"

**Cause:** Version mismatch between the QNN Maven delegate artifact and the native `.so` files.

**Fix:**
1. Verify the QAIRT SDK version matches: `STACK.md` specifies 2.44.0
2. Do not mix `.so` files from different SDK versions

### "Model output is garbage / incorrect"

**Cause:** The model was not compiled with QNN-compatible operators, or quantization is wrong.

**Fix:**
1. Ensure the model was converted using the `litert-torch` pipeline (see `tools/conversion/working/`)
2. Verify quantization settings match the delegate's requirements (INT8 for QNN NPU)
3. Try the GPU delegate to confirm the model is functionally correct — if GPU output is correct
   and QNN output is not, the model needs recompilation with correct QNN operator set

---

## Other Snapdragon Chips — .so File Reference

| Snapdragon | HTP Version | Stub File | Skel File | Example Device |
|---|---|---|---|---|
| 8 Gen 1 (SM8450) | v68 | libQnnHtpV68Stub.so | libQnnHtpV68Skel.so | Galaxy S22 Ultra |
| 8 Gen 1+ (SM8475) | v69 | libQnnHtpV69Stub.so | libQnnHtpV69Skel.so | Galaxy Z Fold4 |
| 8 Gen 2 (SM8550) | v73 | libQnnHtpV73Stub.so | libQnnHtpV73Skel.so | Galaxy S23 Ultra |
| 8 Gen 3 (SM8650) | v75 | libQnnHtpV75Stub.so | libQnnHtpV75Skel.so | Galaxy S24 Ultra |
| 8 Elite (SM8750) | v79 | libQnnHtpV79Stub.so | libQnnHtpV79Skel.so | Galaxy S25 Ultra |

The other files (`libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnSystem.so`, `libQnnTFLiteDelegate.so`)
are the same across HTP versions and come from the same QAIRT SDK release.

To support multiple Snapdragon generations in one APK, place all version-specific stub/skel pairs
in `libs/qnn/arm64-v8a/`. The QNN runtime automatically loads the correct version for the device.

---

## Official Documentation

- [QNN SDK Overview](https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk)
- [LiteRT QNN Delegate Integration](https://ai.google.dev/edge/litert/inference/gpu) (GPU doc contains parallel QNN guidance)
- [Qualcomm AI Hub](https://aihub.qualcomm.com) — Pre-optimized models for Snapdragon devices
