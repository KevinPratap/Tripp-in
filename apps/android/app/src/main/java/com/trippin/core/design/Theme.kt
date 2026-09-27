package com.trippin.core.design

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** How the person wants the app themed. Stored in settings and offered on the You tab. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalTrippinColors = staticCompositionLocalOf { LightTrippinColors }
val LocalTrippinShapes = staticCompositionLocalOf { TrippinShapes() }

/**
 * Read the current tokens as `TrippinTheme.colors.ink` or `TrippinTheme.shapes.card`. This is the
 * only way a screen names a colour or a shape: the raw palette in Color.kt is wired onto the locals
 * here and nowhere else reaches for it.
 */
object TrippinTheme {
    val colors: TrippinColors
        @Composable @ReadOnlyComposable get() = LocalTrippinColors.current

    val shapes: TrippinShapes
        @Composable @ReadOnlyComposable get() = LocalTrippinShapes.current
}

@Composable
fun TrippinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkTrippinColors else LightTrippinColors
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            tertiary = colors.accent,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.panel,
            onSurface = colors.ink,
            surfaceVariant = colors.panelAlt,
            onSurfaceVariant = colors.inkMuted,
            error = colors.danger,
            outline = colors.ink,
            outlineVariant = colors.inkMuted
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            tertiary = colors.accent,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.panel,
            onSurface = colors.ink,
            surfaceVariant = colors.panelAlt,
            onSurfaceVariant = colors.inkMuted,
            error = colors.danger,
            outline = colors.ink,
            outlineVariant = colors.inkMuted
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge to edge: the bars are transparent and their icons take a light or dark tint from
            // the page behind them, so a cream page gets dark icons and a carbon page gets light.
            val lightBars = colors.paper.luminance() > 0.5f
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightBars
            controller.isAppearanceLightNavigationBars = lightBars
        }
    }

    CompositionLocalProvider(
        LocalTrippinColors provides colors,
        LocalTrippinShapes provides TrippinShapes()
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = TrippinTypography,
            content = content
        )
    }
}
