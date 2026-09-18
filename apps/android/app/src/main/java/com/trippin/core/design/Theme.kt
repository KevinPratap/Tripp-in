package com.trippin.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = OceanBlueDark,
    secondary = EmeraldTealDark,
    tertiary = SunsetCoral,
    background = CharcoalDark,
    surface = SurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = OceanBlue,
    secondary = EmeraldTeal,
    tertiary = SunsetCoral,
    background = OffWhite,
    surface = PureWhite
)

@Composable
fun TrippinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrippinTypography,
        content = content
    )
}
