# QNN Setup Guide

This guide explains how to obtain, place, and verify the Qualcomm AI Engine Direct (QNN) native
libraries required for Hexagon NPU inference on Snapdragon devices.

---

## Supported Devices and HTP Versions

| Device | Snapdragon | HTP Version | Stub File | Skel File |
|---|---|---|---|---|
| Samsung Galaxy S22 Ultra | SM8450 (8 Gen 1) | v68 | libQnnHtpV68Stub.so | libQnnHtpV68Skel.so |
| Samsung Galaxy Z Fold4 | SM8475 (8 Gen 1+) | v69 | libQnnHtpV69Stub.so | libQnnHtpV69Skel.so |
| Samsung Galaxy S23 Ultra | SM8550 (8 Gen 2) | v73 | libQnnHtpV73Stub.so | libQnnHtpV73Skel.so |
| Samsung Galaxy S24 Ultra | SM8650 (8 Gen 3) | v75 | libQnnHtpV75Stub.so | libQnnHtpV75Skel.so |
| **Nothing Phone 3** | **SM8750 (8 Elite)** | **v79** | **libQnnHtpV79Stub.so** | **libQnnHtpV79Skel.so** |
| Samsung Galaxy S25 Ultra | SM8750 (8 Elite) | v79 | libQnnHtpV79Stub.so | libQnnHtpV79Skel.so |

---

## What Files Are Needed

Six files are shared across all HTP versions (same for every Snapdragon target):

| File | Purpose |
|---|---|
| `libQnnHtp.so` | Main HTP (Hexagon Tensor Processor) runtime |
| `libQnnHtpPrepare.so` | Model preparation / context binary compilation |
| `libQnnSystem.so` | QNN system utilities (logging, diagnostics) |
| `libQnnTFLiteDelegate.so` | TFLite/LiteRT delegate bridge (JNI entrypoint) |

Plus **one device-specific pair** from the table above. For the Nothing Phone 3 (SM8750 / HTP v79):

| File | Purpose |
|---|---|
| `libQnnHtpV79Stub.so` | User-space stub for HTP v79 (routes calls to DSP) |
| `libQnnHtpV79Skel.so` | DSP-side skeleton for HTP v79 (runs on Hexagon) |

All six files are required. If the skel/stub pair is missing or wrong for the device, the QNN
delegate will fail to initialize and inference will fall back to GPU. If you target multiple
Snapdragon generations from one APK, include all relevant stub/skel pairs — the QNN runtime
automatically loads the correct one for the device's DSP firmware.

---

## Nothing Phone 3 — Quick Setup (SM8750 / HTP v79)

1. Download QAIRT SDK 2.44 (or newer) from the Qualcomm developer portal.
2. Locate `{SDK_ROOT}/lib/aarch64-android/` — you will find all required `.so` files there.
3. Copy these six files to `libs/qnn/arm64-v8a/`:
   - `libQnnHtp.so`
   - `libQnnHtpPrepare.so`
   - `libQnnSystem.so`
   - `libQnnTFLiteDelegate.so`
   - `libQnnHtpV79Stub.so`
   - `libQnnHtpV79Skel.so`
4. Rebuild: `./gradlew assembleDebug`

To also support SM8550 (Galaxy S23 series) in the same APK, add the v73 pair alongside:
- `libQnnHtpV73Stub.so`
- `libQnnHtpV73Skel.so`

---

## Where to Get the Files

### Option A: QAIRT SDK (Recommended — Full Control)

The QAIRT SDK (Qualcomm AI Engine Direct Runtime) contains all QNN libraries.

1. Go to https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk
2. Create a free Qualcomm developer account if you don't have one
3. Download **QAIRT 2.44** (match the version in `STACK.md`)
4. Install the SDK or extract the archive
5. Navigate to: `{SDK_ROOT}/lib/aarch64-android/`
6. You will find all shared `.so` files and the device-specific stub/skel pairs

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

After obtaining the `.so` files, copy them to:

```
litert-android-boilerplate/
└── libs/
    └── qnn/
        └── arm64-v8a/
            ├── libQnnHtp.so                ← shared (all devices)
            ├── libQnnHtpPrepare.so         ← shared (all devices)
            ├── libQnnSystem.so             ← shared (all devices)
            ├── libQnnTFLiteDelegate.so     ← shared (all devices)
            ├── libQnnHtpV79Stub.so         ← Nothing Phone 3 / SM8750
            ├── libQnnHtpV79Skel.so         ← Nothing Phone 3 / SM8750
            ├── libQnnHtpV73Stub.so         ← Galaxy S23 / SM8550 (optional)
            └── libQnnHtpV73Skel.so         ← Galaxy S23 / SM8550 (optional)
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

On successful QNN initialization on Nothing Phone 3, you should see:
```
D/LiteRTDelegate: QNN delegate selected — backend: HTP v79 (SM8750)
D/LiteRTDelegate: Delegate latency (first inference): 600ms [context binary compilation]
D/LiteRTDelegate: Delegate latency (warm inference): 1.8ms
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

See `docs/BENCHMARKING.md` for expected numbers on SM8750 (Nothing Phone 3) and SM8550.

---

## Troubleshooting

### "Failed to load QNN delegate: UnsatisfiedLinkError"

**Cause:** The `.so` files are missing from `libs/qnn/arm64-v8a/`, or the APK was built without them.

**Fix:**
1. Verify the required files exist in `libs/qnn/arm64-v8a/` — `ls libs/qnn/arm64-v8a/`
2. Clean and rebuild: `./gradlew clean assembleDebug`
3. Check that `app/build.gradle.kts` has `jniLibs { useLegacyPackaging = true }`

### "QNN delegate loaded but falling back to GPU"

**Cause:** The device's HTP version does not match any skel `.so` in the library directory.

**Fix:**
1. Confirm your device's Snapdragon model: `adb shell getprop ro.product.model`
2. For Nothing Phone 3: you need `libQnnHtpV79Stub.so` and `libQnnHtpV79Skel.so`
3. For Galaxy S23 series: you need `libQnnHtpV73Stub.so` and `libQnnHtpV73Skel.so`
4. Check Logcat for skel `.so` load errors and confirm the correct pair is present

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

## Official Documentation

- [QNN SDK Overview](https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk)
- [LiteRT QNN Delegate Integration](https://ai.google.dev/edge/litert/inference/gpu) (GPU doc contains parallel QNN guidance)
- [Qualcomm AI Hub](https://aihub.qualcomm.com) — Pre-optimized models for Snapdragon devices
