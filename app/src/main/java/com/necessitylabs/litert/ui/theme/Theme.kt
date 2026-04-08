/**
 * Theme.kt — Material3 dark OLED theme for litert-android-boilerplate.
 *
 * Provides a single [LiteRtTheme] composable that wraps the app in a
 * Material3 ColorScheme built from the palette defined in Color.kt.
 *
 * There is deliberately no light theme in this boilerplate — it targets
 * Samsung Galaxy S23 Ultra (OLED panel) where a true-black dark theme
 * yields meaningful battery savings and the best visual experience.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The complete dark color scheme for the app.
 *
 * Material3 role → our color mapping:
 *   primary            → Blue40  (actions, FABs, selected tabs)
 *   onPrimary          → Black   (text/icons on primary)
 *   primaryContainer   → Blue80  (selected chip backgrounds)
 *   onPrimaryContainer → Black
 *   secondary          → OnSurfaceVariant (secondary actions)
 *   background         → Black   (AMOLED true black)
 *   surface            → Surface (cards, input fields)
 *   surfaceVariant     → SurfaceElevated (dialogs, menus)
 *   onSurface          → OnSurface (primary text)
 *   onSurfaceVariant   → OnSurfaceVariant (secondary text)
 *   outline            → OutlineColor (dividers, borders)
 *   error              → ErrorRed
 *   errorContainer     → ErrorContainer
 */
private val LiteRtColorScheme = darkColorScheme(
    primary = Blue40,
    onPrimary = Black,
    primaryContainer = Blue80,
    onPrimaryContainer = Black,
    secondary = OnSurfaceVariant,
    onSecondary = Black,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = OnSurface,
    tertiary = Blue80,
    onTertiary = Black,
    background = Black,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = OnSurfaceVariant,
    outline = OutlineColor,
    error = ErrorRed,
    onError = Black,
    errorContainer = ErrorContainer,
    onErrorContainer = OnSurface,
)

/**
 * The root theme composable. Wrap every screen in this inside [MainActivity].
 *
 * Usage:
 * ```kotlin
 * LiteRtTheme {
 *     // your content
 * }
 * ```
 *
 * @param content The composable tree to theme.
 */
@Composable
fun LiteRtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiteRtColorScheme,
        content = content,
    )
}
