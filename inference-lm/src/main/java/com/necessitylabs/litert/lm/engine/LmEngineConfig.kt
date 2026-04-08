/**
 * LmEngineConfig.kt — Configuration data class for LmEngineWrapper initialisation.
 *
 * Encapsulates every knob that controls how the LiteRT-LM Engine is constructed.
 * At runtime this is mapped to a [com.google.ai.edge.litertlm.EngineConfig] by
 * [com.necessitylabs.litert.lm.engine.LmEngineWrapper.initialize].
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.lm.engine

import com.necessitylabs.litert.lm.backend.LmBackendType

/**
 * All parameters required to initialise an [LmEngineWrapper] session.
 *
 * @property modelPath        Absolute filesystem path to the `.litertlm` model bundle.
 *                            Must not be an `assets://` URI — models are too large for
 *                            APK assets and must live on the device filesystem.
 *                            Typical paths:
 *                              `/data/data/<pkg>/files/models/model.litertlm`
 *                              `/storage/emulated/0/Download/model.litertlm`
 * @property modelName        Human-readable label used in log messages and benchmark output.
 * @property preferredBackend The first backend to attempt. Defaults to [LmBackendType.NPU]
 *                            to maximise throughput on SM8550.
 * @property enableFallback   When true, [LmEngineWrapper] will walk the fallback chain
 *                            ([LmBackendType.NPU] → [LmBackendType.GPU] → [LmBackendType.CPU])
 *                            if the preferred backend fails. When false, failure is terminal.
 * @property cacheDir         Optional absolute path to a writable directory. LiteRT-LM uses
 *                            this to store compiled artefacts that accelerate subsequent loads
 *                            (first load may still take 3–15 s for NPU compilation; second
 *                            load with a warm cache typically drops to < 1 s).
 * @property nativeLibraryDir Absolute path to the directory containing QNN native libraries
 *                            (`libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpPrepare.so`).
 *                            Required when [preferredBackend] is [LmBackendType.NPU] or when
 *                            the fallback chain might reach NPU.
 *                            Use `context.applicationInfo.nativeLibraryDir`.
 * @property visionBackend    Optional backend for the vision encoder of multimodal models.
 *                            Null for text-only models.
 * @property audioBackend     Optional backend for the audio encoder of multimodal models.
 *                            Null for text-only models.
 */
data class LmEngineConfig(
    val modelPath: String,
    val modelName: String,
    val preferredBackend: LmBackendType = LmBackendType.NPU,
    val enableFallback: Boolean = true,
    val cacheDir: String? = null,
    val nativeLibraryDir: String? = null,
    val visionBackend: LmBackendType? = null,
    val audioBackend: LmBackendType? = null,
) {
    /**
     * Validates that the required fields are non-blank before the config is used.
     *
     * @throws IllegalArgumentException if [modelPath] or [modelName] is blank.
     */
    fun validate() {
        require(modelPath.isNotBlank()) { "LmEngineConfig.modelPath must not be blank." }
        require(modelName.isNotBlank()) { "LmEngineConfig.modelName must not be blank." }
        require(!modelPath.startsWith("assets://")) {
            "LmEngineConfig.modelPath must be an absolute filesystem path, " +
                "not an assets:// URI. Models cannot be loaded from APK assets."
        }
    }
}
