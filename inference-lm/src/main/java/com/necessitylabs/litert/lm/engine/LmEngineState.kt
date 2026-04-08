/**
 * LmEngineState.kt — Sealed interface representing every lifecycle state of LmEngineWrapper.
 *
 * Consumed by UI layers via StateFlow<LmEngineState> exposed on LmEngineWrapper.
 * The state machine follows a strict linear progression:
 *
 *   Idle ──initialize()──► Loading ──success──► Ready
 *                                  └──failure──► Error
 *
 * An engine in the Error state can be retried by calling initialize() again, which
 * transitions it back through Loading.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.lm.engine

import com.necessitylabs.litert.lm.backend.LmBackendType

/**
 * Lifecycle state for [LmEngineWrapper].
 *
 * All states are exposed as a [kotlinx.coroutines.flow.StateFlow] so Compose
 * collectors automatically receive the latest value on resubscription.
 */
sealed interface LmEngineState {

    /**
     * The engine has not been initialised yet. This is the state immediately
     * after construction, or after a successful [LmEngineWrapper.close] call.
     */
    data object Idle : LmEngineState

    /**
     * The engine is currently loading the model on [kotlinx.coroutines.Dispatchers.Default].
     *
     * @property backend The backend currently being attempted. May change if fallback
     *                   kicks in — the StateFlow will emit a new [Loading] value for
     *                   each backend attempt so the UI can display which one is active.
     */
    data class Loading(val backend: LmBackendType) : LmEngineState

    /**
     * The model is loaded and the engine is ready to create conversations.
     *
     * @property backend    The backend that was successfully initialised.
     * @property loadTimeMs Wall-clock milliseconds from the start of [LmEngineWrapper.initialize]
     *                      to the moment the engine signalled readiness.
     */
    data class Ready(
        val backend: LmBackendType,
        val loadTimeMs: Long,
    ) : LmEngineState

    /**
     * Initialisation failed on all attempted backends (or on the preferred backend
     * when fallback is disabled).
     *
     * @property message A human-readable description of the failure suitable for display.
     * @property cause   The underlying exception, if available.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : LmEngineState
}
