/**
 * DelegateType.kt — Enumeration of supported inference acceleration backends.
 *
 * Defines the three delegate tiers that the inference module can target, in
 * descending priority order: QNN NPU (Hexagon HTP), Adreno GPU, and the
 * always-available XNNPACK CPU fallback.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.delegate

/**
 * Identifies which hardware backend is (or was) used for a given inference run.
 *
 * Priority order when creating candidates in [LiteRtDelegateProvider]:
 *  1. [QNN_NPU]  — highest performance; requires Qualcomm QAIRT native libs
 *  2. [GPU]      — mid-tier; requires OpenCL or OpenGL ES on the device
 *  3. [CPU]      — always available; XNNPACK is built into the LiteRT runtime
 */
enum class DelegateType {

    /**
     * Qualcomm Hexagon NPU via the QNN HTP backend.
     *
     * Requires [libQnnHtp.so], [libQnnHtpV73Stub.so], [libQnnSystem.so],
     * [libQnnHtpPrepare.so], and [libQnnHtpV73Skel.so] to be present in
     * [android.content.pm.ApplicationInfo.nativeLibraryDir] at runtime.
     * Targets Hexagon HTP v73 on the SM8550 (Snapdragon 8 Gen 2).
     */
    QNN_NPU,

    /**
     * Adreno GPU via the LiteRT GPU delegate.
     *
     * Backed by OpenCL when available; falls back to OpenGL ES internally.
     * Requires the [com.google.ai.edge.litert:litert-gpu:1.2.0] artifact and
     * that [libvndksupport.so] / [libOpenCL.so] are declared in the manifest.
     */
    GPU,

    /**
     * XNNPACK-accelerated CPU execution.
     *
     * Built into the LiteRT runtime — no additional artifacts or native
     * libraries required. Used as the final fallback when both QNN and GPU
     * delegate creation fail.
     */
    CPU,
}
