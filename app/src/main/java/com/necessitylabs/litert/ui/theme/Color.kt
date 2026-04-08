/**
 * Color.kt — Color palette for litert-android-boilerplate's dark OLED theme.
 *
 * Design intent:
 *   • Pure black (#000000) backgrounds for AMOLED power savings on S23 Ultra.
 *   • Blue (#2979FF) as the primary accent — visible on OLED, accessible contrast.
 *   • Neutral dark greys for surfaces and containers to create depth.
 *   • Error red follows Material3 conventions for consistent destructive-action UX.
 *
 * All colours referenced here are consumed by Theme.kt — do not reference raw
 * Color values from Compose screens; always go through MaterialTheme.colorScheme.
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand blue ────────────────────────────────────────────────────────────────
/** Primary accent: vivid blue that reads well on pure-black OLED. */
val Blue80 = Color(0xFF82B1FF)
/** Lighter variant for containers/chips in dark theme. */
val Blue40 = Color(0xFF2979FF)

// ── Surface neutrals ─────────────────────────────────────────────────────────
/** True black — AMOLED background. */
val Black = Color(0xFF000000)
/** Near-black: cards, input fields, bottom sheets — creates slight depth over Black. */
val Surface = Color(0xFF0D0D0D)
/** Elevated surface: dialogs, menus — one step above Surface. */
val SurfaceElevated = Color(0xFF1A1A1A)
/** Outline/divider: subtle separation between elements. */
val OutlineColor = Color(0xFF2E2E2E)

// ── Text ─────────────────────────────────────────────────────────────────────
/** Primary text on dark backgrounds. */
val OnSurface = Color(0xFFE8E8E8)
/** Secondary / caption text. */
val OnSurfaceVariant = Color(0xFF9E9E9E)

// ── Error ─────────────────────────────────────────────────────────────────────
/** Material3 error red — used for error states, destructive actions. */
val ErrorRed = Color(0xFFCF6679)
/** Background behind error content (dark container). */
val ErrorContainer = Color(0xFF8B1A26)

// ── Benchmark badge ──────────────────────────────────────────────────────────
/** NPU badge background — subtle purple hint to indicate hardware acceleration. */
val NpuBadge = Color(0xFF3D2B55)
/** GPU badge background — dark teal. */
val GpuBadge = Color(0xFF1B3A3A)
/** CPU badge background — dark amber. */
val CpuBadge = Color(0xFF3A2D0A)
