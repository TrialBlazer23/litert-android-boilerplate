/**
 * build.gradle.kts — Root project build script for litert-boilerplate
 *
 * Applies all top-level plugins with `apply false` so they are available on
 * the classpath but not actually applied to the root project. Each module
 * opts in by calling `apply(plugin = "...")` or using the plugins {} block.
 *
 * The root project deliberately contains no source code or configuration
 * beyond plugin declarations and the shared clean task — all real config
 * lives in the per-module build files.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

plugins {
    // Android Gradle Plugin — applied to :app as "com.android.application"
    // and to :inference / :inference-lm as "com.android.library".
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false

    // Kotlin Android plugin — applied in every Android module.
    alias(libs.plugins.kotlin.android) apply false

    // Compose Compiler plugin — replaces the legacy composeOptions.kotlinCompilerExtensionVersion
    // mechanism introduced in Kotlin 2.0 / AGP 8.3+. Applied in :app only.
    alias(libs.plugins.kotlin.compose) apply false

    // Hilt — code-generates DI component scaffolding for :app.
    alias(libs.plugins.hilt.android) apply false

    // KSP — Kotlin Symbol Processing, required by Hilt 2.x to replace kapt.
    alias(libs.plugins.ksp) apply false
}

// ── Clean task ───────────────────────────────────────────────────────────────
// Provides a standard `./gradlew clean` that removes all build outputs across
// every included module.
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
