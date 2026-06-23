/**
 * inference-lm/build.gradle.kts — Build configuration for the :inference-lm module
 *
 * The :inference-lm module provides LLM / GenAI on-device inference using
 * LiteRT-LM (com.google.ai.edge.litertlm:litertlm-android). LiteRT-LM is
 * a higher-level library built on top of LiteRT that handles model loading,
 * tokenisation, KV-cache management, and streaming text generation for
 * language models (e.g., Gemma 2B/7B, Phi-2).
 *
 * This module is kept separate from :inference because:
 *  • LiteRT-LM brings its own transitive LiteRT dependency which may differ
 *    from the pinned 1.4.0 core version in :inference.
 *  • LLM inference workflows (streaming, context windows) differ significantly
 *    from classical ML inference and warrant a distinct API surface.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "com.necessitylabs.litert.lm"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    // ── LiteRT-LM ─────────────────────────────────────────────────────────────
    // Provides LlmInference and LlmInference.Session for streaming text
    // generation from on-device language models. Published by Google to the
    // Google Maven repository under com.google.ai.edge.litertlm.
    //
    // latest.release is resolved at sync time by the Google Maven metadata
    // endpoint. Pin to a concrete version once the project reaches production.
    api(libs.litert.lm)

    // ── Coroutines ────────────────────────────────────────────────────────────
    // Required for wrapping LiteRT-LM's listener-based streaming API into
    // Kotlin Flow for idiomatic consumption from Compose ViewModels.
    implementation(libs.coroutines.core)

    // ── Unit tests ────────────────────────────────────────────────────────────
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)

    // ── Instrumented tests ────────────────────────────────────────────────────
    androidTestImplementation(libs.mockk.android)
}
