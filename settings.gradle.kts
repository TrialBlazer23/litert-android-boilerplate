/**
 * settings.gradle.kts — Root project settings for litert-boilerplate
 *
 * Declares the plugin repositories, dependency resolution strategy, and
 * all included sub-modules. Keeping pluginManagement and
 * dependencyResolutionManagement here (rather than in root build.gradle.kts)
 * ensures a single authoritative list of repositories for the entire build.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */

pluginManagement {
    repositories {
        // AGP and Android tooling live on the Google Maven repository.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Kotlin, KSP, and most JVM tooling plugins live on Maven Central.
        mavenCentral()
        // The Gradle Plugin Portal hosts community plugins such as Hilt
        // (published by Google but also mirrored here).
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS prevents modules from declaring their own
    // repositories, which would create an uncontrolled resolution order.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        // LiteRT artifacts (com.google.ai.edge.*) and LiteRT-LM
        // (com.google.ai.edge.litertlm.*) are published to Google Maven.
        // This repository must appear before Maven Central to avoid
        // resolving stale mirrors.
        google()
        // QNN artifacts (com.qualcomm.qti.*) are published to Maven Central.
        // Coroutines, Mockk, Turbine, and other JVM deps also live here.
        mavenCentral()
    }

    // Point at the shared version catalog so all modules can reference
    // libs.xxx without re-declaring the catalog in each module.
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "litert-boilerplate"

// ── Sub-modules ──────────────────────────────────────────────────────────────
// :app        — Sample Compose UI demonstrating inference and LLM modules
// :inference  — Classical ML inference using LiteRT + QNN delegate
// :inference-lm — LLM / GenAI inference using LiteRT-LM
include(":app")
include(":inference")
include(":inference-lm")
