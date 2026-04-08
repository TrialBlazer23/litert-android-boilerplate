/**
 * LmBackendResolver.kt — Maps LmBackendType values to LiteRT-LM Backend objects.
 *
 * This object is the single translation layer between the module's own backend
 * abstraction and the concrete com.google.ai.edge.litertlm.Backend sealed class.
 * All code outside this file should work exclusively with [LmBackendType] so that
 * swapping or mocking backends in tests requires only a small change here.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.lm.backend

import com.google.ai.edge.litertlm.Backend

/**
 * Converts [LmBackendType] values to [Backend] instances expected by [com.google.ai.edge.litertlm.EngineConfig].
 *
 * Also provides the ordered fallback chain used by [com.necessitylabs.litert.lm.engine.LmEngineWrapper]
 * when the preferred backend fails to initialise.
 */
object LmBackendResolver {

    /**
     * Resolves [type] to the concrete [Backend] required by LiteRT-LM.
     *
     * @param type            The desired backend.
     * @param nativeLibraryDir Absolute path to the directory containing QNN `.so` files.
     *                        Required when [type] is [LmBackendType.NPU]; ignored otherwise.
     * @return A fully constructed [Backend] ready for use in [com.google.ai.edge.litertlm.EngineConfig].
     * @throws IllegalArgumentException if [type] is [LmBackendType.NPU] and [nativeLibraryDir] is null or blank.
     */
    fun resolve(type: LmBackendType, nativeLibraryDir: String? = null): Backend {
        return when (type) {
            LmBackendType.NPU -> {
                val libDir = nativeLibraryDir?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException(
                        "nativeLibraryDir is required for NPU backend. " +
                            "Provide context.applicationInfo.nativeLibraryDir."
                    )
                Backend.NPU(nativeLibraryDir = libDir)
            }

            LmBackendType.GPU -> Backend.GPU()

            LmBackendType.CPU -> Backend.CPU()
        }
    }

    /**
     * Returns the ordered list of backends to attempt, starting with [preferred].
     *
     * The list is used by [com.necessitylabs.litert.lm.engine.LmEngineWrapper] to walk
     * down the priority chain on initialisation failure:
     *   NPU preferred → [NPU, GPU, CPU]
     *   GPU preferred → [GPU, CPU]
     *   CPU preferred → [CPU]
     *
     * @param preferred The caller's first-choice backend.
     * @return An immutable list with [preferred] at index 0, followed by progressively
     *         less-capable fallbacks.
     */
    fun fallbackOrder(preferred: LmBackendType): List<LmBackendType> {
        return when (preferred) {
            LmBackendType.NPU -> listOf(LmBackendType.NPU, LmBackendType.GPU, LmBackendType.CPU)
            LmBackendType.GPU -> listOf(LmBackendType.GPU, LmBackendType.CPU)
            LmBackendType.CPU -> listOf(LmBackendType.CPU)
        }
    }
}
