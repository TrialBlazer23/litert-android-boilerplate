/**
 * LiteRtApp.kt — Application class for litert-android-boilerplate.
 *
 * Annotated with @HiltAndroidApp so Hilt generates the application-level
 * DI component. This is the root of the dependency injection graph.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The application entry point. Hilt generates [LiteRtApp_HiltComponents] from this
 * class; all [dagger.hilt.android.AndroidEntryPoint]-annotated components
 * (activities, fragments, view models) are wired into the same component tree.
 */
@HiltAndroidApp
class LiteRtApp : Application()
