/**
 * inference/build.gradle.kts — Build configuration for the :inference module
 *
 * The :inference module provides classical on-device ML inference using the
 * LiteRT (formerly TensorFlow Lite) Interpreter API alongside the QNN
 * (Qualcomm Neural Networks) delegate for hardware-accelerated execution on
 * Snapdragon devices (SM8550 / HTP v73, SM8750 / HTP v79, etc.).
 *
 * LiteRT version notes:
 *  • 1.4.0 is used for the core Interpreter track, not the 2.x CompiledModel
 *    API, because qnn-litert-delegate 2.44.0 only documents integration with
 *    the Interpreter path.
 *  • litert-gpu 1.2.0 provides GPU delegate support as a fallback when the
 *    NPU/QNN path is unavailable (e.g., emulator, unsupported model ops).
 *  • litert-support 1.2.0 provides TensorBuffer and ImageProcessor utilities
 *    for pre/post-processing without writing boilerplate.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "com.necessitylabs.litert.inference"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        // Library modules do not have an applicationId or versionCode.
        // Tests within this module run against an automatically generated
        // test APK with the default instrumentation runner.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Library modules never shrink — the consuming :app module
            // performs shrinking on the merged dex output.
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
    // ── LiteRT core — 1.x Interpreter API ────────────────────────────────────
    // Provides the Interpreter class and associated TFLite runtime. Must be
    // the 1.x track to match the QNN delegate integration surface.
    api(libs.litert.core)

    // ── LiteRT GPU delegate ───────────────────────────────────────────────────
    // Fallback accelerator when QNN/NPU is unavailable. Exposed as `api`
    // so the :app module can reference GpuDelegate directly if needed.
    api(libs.litert.gpu)

    // ── LiteRT Support Library ────────────────────────────────────────────────
    // TensorBuffer, ImageProcessor, and DataType helpers. `implementation`
    // since consumers interact through the inference API, not support types.
    implementation(libs.litert.support)

    // ── QNN delegate + runtime ────────────────────────────────────────────────
    // qnn-litert-delegate wires the LiteRT Interpreter to the Qualcomm AI
    // Engine Direct (QNN) runtime for NPU execution on Snapdragon devices.
    // qnn-runtime contains the underlying QNN shared libraries.
    api(libs.qnn.litert.delegate)
    implementation(libs.qnn.runtime)

    // ── Coroutines ────────────────────────────────────────────────────────────
    // Provides CoroutineDispatcher and Flow support for async inference tasks.
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
