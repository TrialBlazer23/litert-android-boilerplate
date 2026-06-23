/**
 * app/build.gradle.kts — Build configuration for the :app module
 *
 * The :app module is the sample Android application that wires together the
 * :inference and :inference-lm library modules and exposes a Compose UI for
 * manual testing and demonstration.
 *
 * Key decisions documented inline:
 *  • ABI filter: arm64-v8a only in release — Samsung Galaxy S23 Ultra (SM8550)
 *    is arm64 only; stripping x86/x86_64 cuts APK size significantly.
 *  • jniLibs.srcDirs: points to the project-level libs/ directory where QNN
 *    shared objects (.so) must be placed before building.
 *  • useLegacyPackaging = true: required for QNN's dlopen() calls to locate
 *    the shared libraries at runtime on API 26+.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Compose Compiler plugin — handles the kotlinc compose extension
    // in Kotlin 2.0+ without the legacy composeOptions block.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace   = "com.necessitylabs.litert"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.necessitylabs.litert"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Restrict the release build to the single ABI present on the
        // Samsung Galaxy S23 Ultra (Snapdragon 8 Gen 2 / SM8550).
        // Debug keeps all ABIs so the app can run in the emulator.
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isDebuggable    = true
            isMinifyEnabled = false
            // Remove the ABI filter in debug so developers can test on x86_64
            // emulators without rebuilding.
            ndk {
                abiFilters.clear()
            }
        }
        release {
            isDebuggable    = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        // Enable the Jetpack Compose toolkit.
        compose = true
        // Disable BuildConfig generation — not needed for this sample.
        buildConfig = false
    }

    // ── JNI / native library configuration ───────────────────────────────────
    // QNN shared objects (.so files) must be placed in:
    //   <project-root>/libs/arm64-v8a/
    // before building. The libs/ directory is tracked in .gitignore since the
    // binaries are large; see docs/SETUP.md for download instructions.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(
                // Project-level libs/ for manually downloaded QNN .so files.
                listOf(rootProject.file("libs"))
            )
        }
    }

    packaging {
        jniLibs {
            // useLegacyPackaging = true keeps .so files uncompressed in the APK
            // so the dynamic linker can mmap them directly. QNN uses dlopen()
            // at runtime and requires uncompressed libraries to resolve paths.
            useLegacyPackaging = true
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
    // ── Module dependencies ──────────────────────────────────────────────────
    implementation(project(":inference"))
    implementation(project(":inference-lm"))

    // ── Compose BOM — aligns all androidx.compose.* versions ─────────────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // ── Compose UI ────────────────────────────────────────────────────────────
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)

    // Debug-only Compose tooling (Layout Inspector, Preview renderer).
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // ── Navigation ────────────────────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Unit tests ────────────────────────────────────────────────────────────
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)

    // ── Instrumented / UI tests ───────────────────────────────────────────────
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
}
