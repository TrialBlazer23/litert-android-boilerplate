# Fork Quickstart Guide

This guide walks you through forking `litert-android-boilerplate` to start a new on-device AI
project. The process is designed to be completable in under two hours on a freshly cloned repo.

---

## Before You Start

Make sure you have:
- Android Studio Ladybug (2024.2) or later
- JDK 17 (bundled with recent Android Studio versions)
- Android SDK 35 installed via SDK Manager
- A Galaxy S23 Ultra (or any ARM64 Android device) for testing
- QNN binaries if you need NPU support (see `docs/QNN-SETUP.md`)

---

## Step 1: Fork or Clone

### If forking on GitHub:
1. Click **Fork** on the repository page
2. Give your fork a name relevant to your project (e.g. `my-vision-app`)
3. Clone your fork: `git clone https://github.com/your-org/my-vision-app.git`

### If starting fresh from a clone (no fork):
```bash
git clone https://github.com/necessity-labs/litert-android-boilerplate.git my-vision-app
cd my-vision-app
# Replace remote origin with your own repo
git remote set-url origin https://github.com/your-org/my-vision-app.git
git push -u origin main
```

---

## Step 2: Rename the Package

The boilerplate uses `com.necessitylabs.litert` as its package name. Replace it with your own.

### In Android Studio:
1. Open the project in Android Studio
2. In the **Project** panel, right-click on `com.necessitylabs.litert` in `app/src/main/java/`
3. Select **Refactor → Rename**
4. Enter your new package name (e.g. `com.mycompany.myapp`)
5. Click **Refactor** and let Android Studio update all references

### Then update these manually:

**`app/build.gradle.kts`** — `applicationId`:
```kotlin
android {
    defaultConfig {
        applicationId = "com.mycompany.myapp"   // your package
    }
}
```

**`app/src/main/AndroidManifest.xml`** — verify the `package` attribute is updated.

**`app/src/main/res/values/strings.xml`** — update the app name:
```xml
<string name="app_name">My App Name</string>
```

After renaming, do a project-wide **Find** (Ctrl+Shift+F / Cmd+Shift+F) for `necessitylabs` to
catch any remaining references.

---

## Step 3: Update AppConfig

Open `app/src/main/java/[your.package]/config/AppConfig.kt` and configure your project:

```kotlin
object AppConfig {
    // Classical ML model filename (in app/src/main/assets/models/)
    const val MODEL_FILENAME = "your_model.tflite"

    // LLM model absolute path on device (not in assets)
    const val LM_MODEL_PATH = "/sdcard/models/your_model.litertlm"

    // Force a specific delegate for testing (null = auto QNN → GPU → CPU)
    val FORCED_DELEGATE: DelegateType? = null

    // Enable performance benchmarking overlay (disable in release)
    const val ENABLE_BENCHMARKING = BuildConfig.DEBUG
    const val BENCHMARK_OVERLAY = BuildConfig.DEBUG

    // Maximum context length for LLM conversations
    const val LM_MAX_TOKENS = 1024
}
```

If you are only using classical ML (no LLM), you can leave `LM_MODEL_PATH` empty and skip
configuring the `inference-lm` module.

---

## Step 4: Replace the UI

The `app/` module contains a sample Compose UI. Replace it with your own.

### What to keep:
- `app/src/main/java/[package]/di/` — Hilt modules (keep and extend)
- `app/src/main/java/[package]/config/AppConfig.kt` — update but keep the structure
- `app/src/main/java/[package]/MainViewModel.kt` — update to expose your inference results

### What to replace:
- `app/src/main/java/[package]/ui/` — replace all screens with your own
- `app/src/main/res/` — replace drawable resources, colors, themes
- `app/src/main/res/values/strings.xml` — replace all strings

### Extending the ViewModel:

`MainViewModel` demonstrates how to call the inference engine. Adapt it for your use case:

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val lmEngineManager: LmEngineManager
) : ViewModel() {

    private val _result = MutableStateFlow<InferenceResult?>(null)
    val result: StateFlow<InferenceResult?> = _result.asStateFlow()

    fun runInference(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val inputBuffer = preprocessBitmap(bitmap)
            _result.value = inferenceEngine.runInference(inputBuffer)
        }
    }
}
```

---

## Step 5: Add Your Model

### Classical ML (.tflite)

1. Obtain a `.tflite` model. Sources:
   - [litert-community on HuggingFace](https://huggingface.co/litert-community) — pre-converted models
   - [Kaggle Models](https://www.kaggle.com/models) — filter by TFLite format
   - Convert your own — see `tools/conversion/working/`

2. Copy to `app/src/main/assets/models/your_model.tflite`

3. Update `AppConfig.MODEL_FILENAME`

4. Update the `ModelConfig` in your Hilt module to match your model's input shape:
   ```kotlin
   ModelConfig(
       modelFilename = AppConfig.MODEL_FILENAME,
       inputWidth = 224,
       inputHeight = 224,
       inputChannels = 3,
       isQuantized = true
   )
   ```

### LLM (.litertlm)

1. Obtain a `.litertlm` model (see `docs/MODEL-FORMATS.md`)

2. Push to device:
   ```bash
   adb push your_model.litertlm /sdcard/models/
   ```

3. Update `AppConfig.LM_MODEL_PATH = "/sdcard/models/your_model.litertlm"`

---

## Step 6: Set Up QNN Binaries (for NPU support)

If you want Hexagon NPU inference:

1. Follow the complete guide in `docs/QNN-SETUP.md`
2. Place the six `.so` files in `libs/qnn/arm64-v8a/`
3. Rebuild

If you skip this step, inference runs on GPU → CPU automatically. The build succeeds regardless.

---

## Step 7: Configure Delegates

The default delegate priority (QNN → GPU → CPU) is appropriate for most use cases.

To override for specific situations:

```kotlin
// Force GPU only (useful during development to avoid 60-second QNN compilation)
AppConfig.FORCED_DELEGATE = DelegateType.GPU

// Force CPU (useful for debugging model correctness without hardware-specific behavior)
AppConfig.FORCED_DELEGATE = DelegateType.CPU

// Auto (production default)
AppConfig.FORCED_DELEGATE = null
```

---

## Step 8: Build and Test

```bash
# Verify everything compiles
./gradlew assembleDebug

# Run all unit tests (no device required)
./gradlew :inference:test :inference-lm:test :app:test

# Install and run on connected device
./gradlew installDebug
```

If the build fails, the most common causes are:
- Package name not fully updated (run Find for `necessitylabs`)
- Model file missing from assets (check `MODEL_FILENAME` matches the actual file)
- Missing QNN `.so` files if QNN was configured as required rather than optional

---

## What to Keep, What to Replace

### Keep (infrastructure — do not modify without reason)

| Component | Location | Why keep |
|---|---|---|
| `InferenceEngine` interface | `inference/core/` | Stable API contract |
| `DelegateFactory` | `inference/delegate/` | Tested fallback logic |
| `ModelLoader` | `inference/model/` | Handles assets + absolute path |
| `LmEngineManager` | `inference-lm/engine/` | Lifecycle management |
| `ConversationSession` | `inference-lm/conversation/` | Streaming logic |
| Hilt module structure | `app/di/` | DI wiring pattern |

### Replace (app-specific — make it yours)

| Component | Location | What to do |
|---|---|---|
| Sample UI screens | `app/ui/` | Replace with your screens |
| `MainViewModel` | `app/` | Adapt to your data flow |
| App resources | `app/src/main/res/` | Replace with your brand |
| Model config | `app/di/InferenceModule.kt` | Set your model shape |
| `AppConfig` values | `app/config/AppConfig.kt` | Set your paths and flags |

### Update (documentation — keep accurate)

After forking, update:
- `README.md` — replace project description with your project
- `MEMORY.md` — update project purpose
- `STACK.md` — add any new dependencies you introduce
- `AGENTS.md` — update the project name reference

---

## Verifying Everything Works

Run this checklist after your first successful build:

- [ ] App launches without crash on target device
- [ ] Logcat shows the expected delegate: `adb logcat -s LiteRTDelegate`
- [ ] First inference completes (may be slow — see `docs/BENCHMARKING.md`)
- [ ] Benchmark overlay shows latency numbers
- [ ] All unit tests pass: `./gradlew test`
- [ ] No `necessitylabs` references remain: search project-wide

If LLM inference is in scope:
- [ ] Model file is on device at the configured path
- [ ] `LmEngineManager` initializes without exception
- [ ] First message produces a streaming response in the UI
