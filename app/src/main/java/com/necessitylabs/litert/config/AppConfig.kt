/**
 * AppConfig.kt — Central configuration constants for litert-android-boilerplate.
 *
 * Change the model paths here when forking this boilerplate for a new project.
 * All other code references these constants — there is no other place to update.
 *
 * Path conventions:
 *   /data/local/tmp/models/  — writable via `adb push` without root (for dev/testing)
 *   /data/data/<pkg>/files/  — private app storage (for distribution)
 *   /storage/emulated/0/     — external shared storage (requires READ_EXTERNAL_STORAGE
 *                              on API ≤ 32 or READ_MEDIA_* on API 33+)
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.config

/**
 * Application-wide constants. Intentionally a Kotlin object (not a class) so every
 * reference is resolved at compile time with no allocation overhead.
 */
object AppConfig {

    // ── Classical ML (inference module) ──────────────────────────────────────

    /**
     * Absolute path to the TFLite / LiteRT model used by InferenceScreen.
     *
     * Push a model during development:
     *   adb push mymodel.tflite /data/local/tmp/models/model.tflite
     *
     * Update this constant (or supply it via InferenceEngineConfig) when you
     * replace the placeholder model with a real one.
     */
    const val TFLITE_MODEL_PATH = "/data/local/tmp/models/model.tflite"

    // ── LLM / GenAI (inference-lm module) ────────────────────────────────────

    /**
     * Absolute path to the LiteRT-LM model bundle used by ChatScreen.
     *
     * Pre-bundled models (.litertlm) are available at:
     *   https://huggingface.co/litert-community
     *
     * Push a model during development:
     *   adb push model.litertlm /data/local/tmp/models/model.litertlm
     *   # or use the Models tab in the app → Scan → Load
     *
     * IMPORTANT: Models must be on the filesystem — not in APK assets.
     * The .litertlm format bundles the .tflite weights, tokenizer, and
     * chat template in a single file (2–8 GB).
     *
     * Nothing Phone 3 (SM8750, 16 GB RAM) can comfortably run 7B INT4
     * models (~4 GB) and likely 13B INT4 models (~7 GB). Update this path
     * to a larger model to take advantage of the extra RAM headroom.
     */
    const val LITERTLM_MODEL_PATH = "/data/local/tmp/models/model.litertlm"

    /**
     * Human-readable label for the LLM used in logs and benchmark output.
     * Update this when swapping in a different model.
     */
    const val MODEL_NAME = "sample-model"

    // ── Inference tuning ─────────────────────────────────────────────────────

    /**
     * Default system instruction injected at the start of every LLM conversation.
     * Null means the model uses its built-in default (typically a generic helpful-
     * assistant persona).
     */
    val DEFAULT_SYSTEM_INSTRUCTION: String? = null

    /**
     * Number of threads used by the CPU LiteRT interpreter.
     *
     * Tuning guide by device:
     *  - SM8550 (Snapdragon 8 Gen 2, 8 cores: 1+3+4): 4 threads is optimal
     *  - SM8650 (Snapdragon 8 Gen 3, 8 cores: 1+5+2): 5–6 threads
     *  - SM8750 (Snapdragon 8 Elite / Nothing Phone 3, 8 Oryon cores): 6 threads
     *
     * The default of 4 is conservative and safe across all devices. Override in
     * DelegateConfig.cpuThreadCount at runtime after detecting the device, or set
     * it to (Runtime.getRuntime().availableProcessors() - 2) for a device-adaptive
     * value that keeps at least two cores free for the UI thread.
     */
    const val INTERPRETER_NUM_THREADS = 4
}
