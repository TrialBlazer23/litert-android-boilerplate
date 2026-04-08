/**
 * InferenceModule.kt — Hilt module providing inference dependencies for the app.
 *
 * Binds:
 *   DelegateProvider  → LiteRtDelegateProvider   (classical ML delegate selection)
 *   InferenceEngine   → LiteRtInferenceEngine     (classical ML inference runner)
 *   LmEngineWrapper   singleton                   (LLM inference via LiteRT-LM)
 *
 * All inference components are provided as singletons so state (loaded model,
 * KV-cache) survives between screen navigations. The LmEngineWrapper is installed
 * in SingletonComponent so it outlives any Activity re-creation.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.di

import com.necessitylabs.litert.inference.core.InferenceEngine
import com.necessitylabs.litert.inference.core.LiteRtInferenceEngine
import com.necessitylabs.litert.inference.delegate.DelegateProvider
import com.necessitylabs.litert.inference.delegate.LiteRtDelegateProvider
import com.necessitylabs.litert.lm.engine.LmEngineWrapper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides and binds inference components into the Hilt SingletonComponent graph.
 *
 * Uses @Binds where we have an interface + concrete implementation pair (so Hilt
 * avoids the extra factory allocation), and @Provides for LmEngineWrapper whose
 * constructor takes no arguments yet still needs singleton scoping.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceModule {

    /**
     * Binds [LiteRtDelegateProvider] as the app-wide [DelegateProvider].
     *
     * LiteRtDelegateProvider selects the best available LiteRT delegate
     * (QNN NPU → GPU → CPU) and returns a fully-configured Options object
     * ready for the Interpreter or CompiledModel API.
     *
     * @param impl The concrete implementation created by Hilt via its own injected constructor.
     * @return [DelegateProvider] interface reference backed by [LiteRtDelegateProvider].
     */
    @Binds
    @Singleton
    abstract fun bindDelegateProvider(impl: LiteRtDelegateProvider): DelegateProvider

    /**
     * Binds [LiteRtInferenceEngine] as the app-wide [InferenceEngine].
     *
     * LiteRtInferenceEngine wraps the LiteRT CompiledModel / Interpreter runtime,
     * exposes a coroutine-friendly API, and publishes benchmark results.
     *
     * @param impl The concrete implementation created by Hilt via its own injected constructor.
     * @return [InferenceEngine] interface reference backed by [LiteRtInferenceEngine].
     */
    @Binds
    @Singleton
    abstract fun bindInferenceEngine(impl: LiteRtInferenceEngine): InferenceEngine

    companion object {

        /**
         * Provides the singleton [LmEngineWrapper] for LLM inference.
         *
         * LmEngineWrapper is not injected — it is constructed here because:
         *   1. It implements AutoCloseable and requires careful lifecycle control.
         *   2. Its [LmEngineWrapper.initialize] is a suspend function that cannot
         *      be called at provision time; the ViewModel calls it explicitly.
         *
         * @return A fresh, uninitialised [LmEngineWrapper] in [LmEngineState.Idle] state.
         */
        @Provides
        @Singleton
        fun provideLmEngineWrapper(): LmEngineWrapper = LmEngineWrapper()
    }
}
