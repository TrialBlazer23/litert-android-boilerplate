/**
 * LmBackendType.kt — Enumeration of supported LiteRT-LM inference backends.
 *
 * Maps to the concrete Backend types exposed by the LiteRT-LM SDK:
 *   NPU → Backend.NPU(nativeLibraryDir)   — Qualcomm Hexagon HTP via QNN
 *   GPU → Backend.GPU()                   — Adreno 740 via OpenCL/OpenGL ES
 *   CPU → Backend.CPU()                   — Kryo cores via XNNPACK
 *
 * Backend selection priority on SM8550 (Galaxy S23 Ultra):
 *   NPU achieves 50–80 tok/s on 4B Q4 models — always try first.
 *   GPU achieves 15–25 tok/s — good fallback when QNN libs are absent.
 *   CPU achieves 5–10 tok/s — universal fallback, always available.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.lm.backend

/**
 * The three hardware backends that LiteRT-LM can target.
 *
 * Use [LmBackendResolver] to convert these to concrete [com.google.ai.edge.litertlm.Backend]
 * instances before passing them to [com.google.ai.edge.litertlm.EngineConfig].
 */
enum class LmBackendType {

    /**
     * Qualcomm Hexagon Tensor Processor via the QNN delegate.
     *
     * Requires `libQnnHtp.so`, `libQnnSystem.so`, and `libQnnHtpPrepare.so`
     * to be present in the app's [android.content.pm.ApplicationInfo.nativeLibraryDir]
     * at runtime. [LmEngineConfig.nativeLibraryDir] must be supplied when this
     * backend is used.
     *
     * Performance target (SM8550): 50–80 tok/s on a 4B INT4 model.
     */
    NPU,

    /**
     * Adreno GPU via OpenCL (preferred) with OpenGL ES as fallback.
     *
     * Requires the following entries in AndroidManifest.xml:
     *   `<uses-native-library android:name="libvndksupport.so" android:required="false"/>`
     *   `<uses-native-library android:name="libOpenCL.so" android:required="false"/>`
     *
     * Performance target (SM8550 Adreno 740): 15–25 tok/s.
     */
    GPU,

    /**
     * CPU via XNNPACK — always available, no additional libraries required.
     *
     * Performance target (SM8550 Kryo): 5–10 tok/s.
     * Used as the final fallback when both NPU and GPU initialisation fail.
     */
    CPU,
}
