/**
 * DelegateProvider.kt — Interfaces and data classes for delegate creation.
 *
 * Defines the contract ([DelegateProvider]) for producing an ordered list of
 * [DelegateCandidate] objects, along with the configuration ([DelegateConfig])
 * used to control which backends are enabled and how each is configured.
 *
 * [LiteRtInferenceEngine] iterates [DelegateCandidate]s sorted by [priority]
 * (ascending — lower number = higher priority) and loads the first one that
 * initialises without throwing.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.delegate

import com.google.ai.edge.litert.Delegate

/**
 * Factory interface for constructing [DelegateCandidate] lists.
 *
 * Implementations must be safe to call from any thread.  Each [Delegate]
 * returned is owned by the caller; the caller is responsible for closing it
 * when inference is finished.
 */
interface DelegateProvider {

    /**
     * Builds an ordered list of [DelegateCandidate]s based on [config].
     *
     * Candidates are returned in the preferred evaluation order — the engine
     * will try each in turn and use the first that succeeds.  Candidates whose
     * delegate creation throws internally are omitted from the returned list;
     * all logged errors are the provider's responsibility.
     *
     * @param config Runtime configuration controlling which backends to enable
     *               and their hardware-specific parameters.
     * @return Non-empty list of candidates sorted by [DelegateCandidate.priority]
     *         (ascending). Always includes at least the CPU candidate.
     */
    fun createDelegates(config: DelegateConfig): List<DelegateCandidate>
}

/**
 * A single acceleration backend candidate, wrapping the [Delegate] instance
 * together with its [type] and relative [priority].
 *
 * @property type     The backend this candidate represents.
 * @property delegate The initialised [Delegate] ready to be added to
 *                    [com.google.ai.edge.litert.Interpreter.Options].
 * @property priority Sort key — lower values are tried first (QNN = 0, GPU = 1, CPU = 2).
 */
data class DelegateCandidate(
    val type: DelegateType,
    val delegate: Delegate,
    val priority: Int,
)

/**
 * Configuration that governs which delegates [DelegateProvider] attempts to
 * create and how each is parameterised.
 *
 * @property enableQnn         Whether to attempt QNN NPU delegate creation.
 *                             Set [false] on non-Qualcomm devices or in unit tests.
 * @property enableGpu         Whether to attempt GPU delegate creation.
 * @property nativeLibraryDir  Absolute path to the app's native library directory.
 *                             Passed to QNN's [setSkelLibraryDir] so it can locate
 *                             the device-appropriate HTP skel library (e.g.
 *                             libQnnHtpV73Skel.so on SM8550, libQnnHtpV79Skel.so on
 *                             SM8750 / Nothing Phone 3). Obtain via
 *                             [android.content.pm.ApplicationInfo.nativeLibraryDir].
 * @property qnnBackendType    QNN backend identifier string. "HTP_BACKEND" targets the
 *                             Hexagon NPU on all supported Snapdragon devices.
 * @property gpuPrecisionLoss  When [true], allows the GPU delegate to use FP16
 *                             internally for ~2× throughput gain at minor accuracy
 *                             cost.  Mirrors [GpuDelegate.Options.setPrecisionLossAllowed].
 * @property cpuThreadCount    Number of threads for CPU XNNPACK execution.
 *                             4 is a safe default; on SM8750 (8 Oryon cores) set to
 *                             6 for better CPU throughput. Consider using
 *                             [Runtime.getRuntime().availableProcessors()] minus 2 to
 *                             avoid starving the UI thread.
 */
data class DelegateConfig(
    val enableQnn: Boolean = true,
    val enableGpu: Boolean = true,
    val nativeLibraryDir: String,
    val qnnBackendType: String = "HTP_BACKEND",
    val gpuPrecisionLoss: Boolean = true,
    val cpuThreadCount: Int = 4,
)
