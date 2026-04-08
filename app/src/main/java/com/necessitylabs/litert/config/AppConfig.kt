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
     * On SM8550 (8 cores: 1 prime + 3 performance + 4 efficiency),
     * 4 threads typically gives the best CPU throughput without starving the UI.
     */
    const val INTERPRETER_NUM_THREADS = 4
}
