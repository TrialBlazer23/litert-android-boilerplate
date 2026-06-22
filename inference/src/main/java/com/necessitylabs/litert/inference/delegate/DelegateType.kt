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
     * Requires [libQnnHtp.so], [libQnnHtpPrepare.so], [libQnnSystem.so],
     * [libQnnTFLiteDelegate.so], and a device-specific stub/skel pair in
     * [android.content.pm.ApplicationInfo.nativeLibraryDir] at runtime.
     *
     * HTP version by Snapdragon generation:
     *  - SM8550 (Snapdragon 8 Gen 2):            HTP v73 — libQnnHtpV73Stub.so / libQnnHtpV73Skel.so
     *  - SM8650 (Snapdragon 8 Gen 3):            HTP v75 — libQnnHtpV75Stub.so / libQnnHtpV75Skel.so
     *  - SM8750 (Snapdragon 8 Elite / NP3):      HTP v79 — libQnnHtpV79Stub.so / libQnnHtpV79Skel.so
     *
     * The QNN runtime selects the correct skel automatically when all supported
     * stub/skel pairs are present in the native library directory. Drop multiple
     * pairs into libs/qnn/arm64-v8a/ to support several Snapdragon generations
     * from a single APK. See docs/QNN-SETUP.md for the full file list per device.
     */
    QNN_NPU,

    /**
     * Adreno GPU via the LiteRT GPU delegate.
     *
     * Backed by OpenCL when available; falls back to OpenGL ES internally.
     * Requires the [com.google.ai.edge.litert:litert-gpu:1.2.0] artifact and
     * that [libvndksupport.so] / [libOpenCL.so] are declared in the manifest.
     * Compatible with all Adreno generations (Adreno 740 on SM8550, Adreno 830
     * on SM8750 / Nothing Phone 3, etc.).
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
