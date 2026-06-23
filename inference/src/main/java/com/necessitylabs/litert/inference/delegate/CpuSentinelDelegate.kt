/**
 * CpuSentinelDelegate.kt — Lightweight no-op delegate sentinel for CPU/XNNPACK.
 *
 * XNNPACK is built into the LiteRT runtime and requires no explicit Delegate
 * object.  [LiteRtDelegateProvider] still needs to return a [DelegateCandidate]
 * for the CPU tier to satisfy the [DelegateProvider] contract.
 *
 * [LiteRtInferenceEngine] checks for this type at load time and intentionally
 * skips adding it to [com.google.ai.edge.litert.InterpreterApi.Options], so the
 * interpreter's built-in XNNPACK acceleration is used automatically.
 *
 * This class must never be added to Interpreter.Options — doing so would throw
 * an IllegalArgumentException from the LiteRT runtime.
 *
 * Part of LiteRT Android Boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

package com.necessitylabs.litert.inference.delegate

import org.tensorflow.lite.Delegate

/**
 * Sentinel implementation of [Delegate] representing the CPU/XNNPACK backend.
 *
 * Holds no native resources and performs no operation on [close].  Its sole
 * purpose is to exist as a typed token inside [DelegateCandidate] so the
 * engine can carry CPU in the same candidate list as GPU/QNN without special-
 * casing null.
 */
class CpuSentinelDelegate : Delegate {

    /**
     * Returns 0 — this sentinel holds no native delegate handle.
     *
     * The LiteRT runtime must never receive this object via
     * [com.google.ai.edge.litert.InterpreterApi.Options.addDelegate].
     *
     * @return Always 0.
     */
    override fun getNativeHandle(): Long = 0L

    /**
     * No-op.  There are no native resources to release.
     */
    override fun close() {
        // Intentionally empty — no native allocation to free.
    }
}
