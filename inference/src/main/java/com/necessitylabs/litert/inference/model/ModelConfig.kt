/**
 * ModelConfig.kt — Immutable configuration for a single model load request.
 *
 * Encapsulates every parameter that [InferenceEngine.load] needs to prepare an
 * interpreter: the model file path, the delegate preferences, optional input
 * shape overrides, and the thread budget for CPU fallback execution.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.model

import com.necessitylabs.litert.inference.delegate.DelegateConfig

/**
 * Full configuration for loading and running a single `.tflite` model.
 *
 * @property modelPath      Absolute filesystem path to the `.tflite` model
 *                          file.  Models must be pre-placed on the device —
 *                          either pushed via `adb push`, downloaded by
 *                          [DownloadManager], or copied from assets for small
 *                          models via [ModelLoader.copyModelFromAssets].
 *                          Asset URIs (e.g., `assets://`) are NOT supported by
 *                          the LiteRT Interpreter when loading from a path.
 * @property modelName      Human-readable name used in log messages and
 *                          [BenchmarkData.modelName].  Should be concise, e.g.
 *                          "MobileNetV3-Large" or "YOLOv8n-seg".
 * @property delegateConfig Configuration forwarded to [DelegateProvider] to
 *                          control which hardware backends are attempted and
 *                          how each is parameterised.
 * @property inputShape     When non-null, the engine calls
 *                          [Interpreter.resizeInput] before the first inference
 *                          to override the tensor shape baked into the model.
 *                          Useful for dynamic-batch models or when the model
 *                          was exported with a fixed shape that differs from
 *                          runtime needs.  Leave null to use the model's
 *                          default shape.
 * @property numThreads     Number of CPU threads handed to
 *                          [Interpreter.Options.setNumThreads].  Applies to
 *                          all execution paths including delegate fallback.
 *                          Typical range: 2–4 on mobile; 1 is useful for
 *                          latency-critical single-stream workloads.
 * @property useXnnpack     When [true], tells the runtime to use the XNNPACK
 *                          delegate for CPU-path ops, which typically adds
 *                          3–5× throughput versus the reference kernels.
 *                          Should only be set [false] for debugging or when
 *                          targeting ops not yet covered by XNNPACK.
 */
data class ModelConfig(
    val modelPath: String,
    val modelName: String,
    val delegateConfig: DelegateConfig,
    val inputShape: IntArray? = null,
    val numThreads: Int = 4,
    val useXnnpack: Boolean = true,
) {
    // IntArray does not participate in data-class structural equality by
    // default; override equals/hashCode to make comparisons predictable.

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelConfig) return false
        return modelPath == other.modelPath &&
            modelName == other.modelName &&
            delegateConfig == other.delegateConfig &&
            inputShape.contentEquals(other.inputShape) &&
            numThreads == other.numThreads &&
            useXnnpack == other.useXnnpack
    }

    override fun hashCode(): Int {
        var result = modelPath.hashCode()
        result = 31 * result + modelName.hashCode()
        result = 31 * result + delegateConfig.hashCode()
        result = 31 * result + (inputShape?.contentHashCode() ?: 0)
        result = 31 * result + numThreads
        result = 31 * result + useXnnpack.hashCode()
        return result
    }

    // Private extension to avoid null-unsafe call in equals.
    private fun IntArray?.contentEquals(other: IntArray?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return this.contentEquals(other)
    }
}
