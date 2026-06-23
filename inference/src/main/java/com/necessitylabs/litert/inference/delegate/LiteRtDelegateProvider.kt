/**
 * LiteRtDelegateProvider.kt — Production [DelegateProvider] implementation.
 *
 * Attempts to create three delegate tiers in order — QNN NPU, Adreno GPU, and
 * CPU (XNNPACK fallback).  Each creation is wrapped in a try-catch so that a
 * missing native library or unsupported device never crashes the app; the
 * failing candidate is simply omitted and the reason is logged.
 *
 * CPU is always included as a guaranteed fallback, so the returned list is
 * never empty.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.delegate

import android.util.Log
import org.tensorflow.lite.gpu.GpuDelegate
import com.qualcomm.qti.QnnDelegate

private const val TAG = "LiteRtDelegateProvider"

/**
 * Concrete [DelegateProvider] that creates LiteRT delegates for QNN NPU,
 * Adreno GPU, and XNNPACK CPU.
 *
 * - **QNN NPU** — Uses [QnnDelegate] with HTP_BACKEND.  Requires QAIRT native
 *   libraries in [DelegateConfig.nativeLibraryDir].  The QNN runtime selects the
 *   correct device-specific skel automatically (e.g. libQnnHtpV73Skel.so on SM8550,
 *   libQnnHtpV79Skel.so on SM8750 / Nothing Phone 3) as long as all supported
 *   stub/skel pairs are present. See docs/QNN-SETUP.md.
 * - **GPU** — Uses [GpuDelegate] with optional precision loss for throughput.
 *   Works on all Adreno GPUs (Adreno 740 on SM8550, Adreno 830 on SM8750, etc.).
 *   Requires libOpenCL.so / libvndksupport.so declared in the manifest.
 * - **CPU** — No delegate object needed; the LiteRT runtime runs XNNPACK
 *   automatically on the CPU when no delegate is added.  A sentinel
 *   A [CpuSentinelDelegate] is used to fit the [DelegateCandidate] contract.
 *
 * Usage: instantiate once; [createDelegates] may be called multiple times
 * (e.g., after a previous load fails and the engine retries).
 */
class LiteRtDelegateProvider : DelegateProvider {

    /**
     * Produces an ordered list of [DelegateCandidate]s filtered by [config].
     *
     * Priority assignments: QNN = 0, GPU = 1, CPU = 2.  The engine will pick
     * the candidate at priority 0 first, escalating on failure.
     *
     * @param config Controls which backends to attempt and their parameters.
     * @return List of ready-to-use candidates sorted by priority (ascending).
     *         Always contains at least the CPU candidate.
     */
    override fun createDelegates(config: DelegateConfig): List<DelegateCandidate> {
        val candidates = mutableListOf<DelegateCandidate>()

        if (config.enableQnn) {
            tryCreateQnnDelegate(config)?.let { candidates.add(it) }
        }

        if (config.enableGpu) {
            tryCreateGpuDelegate(config)?.let { candidates.add(it) }
        }

        // CPU/XNNPACK is always the last-resort fallback.
        candidates.add(createCpuCandidate())

        return candidates.sortedBy { it.priority }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Attempts to create a [QnnDelegate] targeting the Hexagon HTP backend.
     *
     * [QnnDelegate.Options.setSkelLibraryDir] is mandatory: it tells the QNN
     * runtime where to find the device-appropriate HTP skel library.  The
     * runtime matches the skel version to the device's DSP firmware automatically
     * (e.g. libQnnHtpV79Skel.so on SM8750 / Nothing Phone 3).
     *
     * @param config Delegate configuration containing nativeLibraryDir and
     *               the desired backend type string.
     * @return A [DelegateCandidate] at priority 0, or null if creation fails.
     */
    private fun tryCreateQnnDelegate(config: DelegateConfig): DelegateCandidate? {
        return try {
            val options = QnnDelegate.Options().apply {
                // HTP_BACKEND routes execution to the Hexagon Neural Processing
                // core (v73 on SM8550, v75 on SM8650, v79 on SM8750 / Nothing Phone 3).
                setBackendType(QnnDelegate.Options.BackendType.HTP_BACKEND)

                // Without setSkelLibraryDir the HTP runtime cannot locate the
                // device-appropriate skel .so and will throw UnsatisfiedLinkError.
                setSkelLibraryDir(config.nativeLibraryDir)
            }
            val delegate = QnnDelegate(options)
            Log.i(TAG, "QNN NPU delegate created (backend=${config.qnnBackendType}, " +
                    "skelDir=${config.nativeLibraryDir})")
            DelegateCandidate(type = DelegateType.QNN_NPU, delegate = delegate, priority = 0)
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "QNN delegate not supported on this device: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "QNN delegate creation failed: ${e.javaClass.simpleName} — ${e.message}")
            null
        }
    }

    /**
     * Attempts to create a [GpuDelegate] for the device's Adreno GPU.
     *
     * Precision loss is enabled when [DelegateConfig.gpuPrecisionLoss] is true,
     * which allows FP16 computation internally and significantly improves
     * throughput at the cost of minor accuracy reduction. Compatible with all
     * supported Adreno GPUs.
     *
     * @param config Delegate configuration.
     * @return A [DelegateCandidate] at priority 1, or null if creation fails.
     */
    private fun tryCreateGpuDelegate(config: DelegateConfig): DelegateCandidate? {
        return try {
            val gpuOptions = GpuDelegate.Options().apply {
                setPrecisionLossAllowed(config.gpuPrecisionLoss)
                // Prefer OpenCL for lower latency; GpuDelegate falls back to
                // OpenGL ES automatically if OpenCL is unavailable.
                setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_FAST_SINGLE_ANSWER)
            }
            val delegate = GpuDelegate(gpuOptions)
            Log.i(TAG, "GPU delegate created (precisionLoss=${config.gpuPrecisionLoss})")
            DelegateCandidate(type = DelegateType.GPU, delegate = delegate, priority = 1)
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate creation failed: ${e.javaClass.simpleName} — ${e.message}")
            null
        }
    }

    /**
     * Creates the CPU/XNNPACK fallback candidate.
     *
     * XNNPACK is built into the LiteRT runtime; no [Delegate] object is required.
     * [CpuSentinelDelegate] is a lightweight no-op that satisfies the
     * [DelegateCandidate.delegate] contract without touching the interpreter's
     * delegate chain — [LiteRtInferenceEngine] detects the sentinel type and
     * skips adding it to [com.google.ai.edge.litert.InterpreterApi.Options].
     *
     * @return [DelegateCandidate] at priority 2.
     */
    private fun createCpuCandidate(): DelegateCandidate {
        Log.d(TAG, "CPU/XNNPACK candidate registered as final fallback")
        return DelegateCandidate(
            type = DelegateType.CPU,
            delegate = CpuSentinelDelegate(),
            priority = 2,
        )
    }
}
