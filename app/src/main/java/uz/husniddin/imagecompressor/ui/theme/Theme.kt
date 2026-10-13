package uz.husniddin.imagecompressor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import uz.husniddin.imagecompressor.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Palette.Blue600,
    onPrimary = Palette.White,
    primaryContainer = Palette.Blue100,
    onPrimaryContainer = Palette.Blue900,
    secondary = Palette.Teal700,
    onSecondary = Palette.White,
    secondaryContainer = Palette.Blue100,
    onSecondaryContainer = Palette.Blue900,
    tertiary = Palette.Amber700,
    onTertiary = Palette.White,
    tertiaryContainer = Palette.Amber100,
    onTertiaryContainer = Palette.Amber900,
    error = Palette.Red700,
    onError = Palette.White,
    errorContainer = Palette.Red100,
    onErrorContainer = Palette.Red950,
    background = Palette.Slate50,
    onBackground = Palette.Slate900,
    surface = Palette.Slate50,
    onSurface = Palette.Slate900,
    surfaceVariant = Palette.Slate100,
    onSurfaceVariant = Palette.Slate600,
    surfaceContainerLowest = Palette.White,
    surfaceContainerLow = Palette.White,
    surfaceContainer = Palette.Slate100,
    surfaceContainerHigh = Palette.Slate200,
    surfaceContainerHighest = Palette.Slate300,
    surfaceBright = Palette.White,
    surfaceDim = Palette.Slate200,
    outline = Palette.Slate400,
    outlineVariant = Palette.Slate200,
    inverseSurface = Palette.Slate800,
    inverseOnSurface = Palette.Slate100,
    inversePrimary = Palette.Blue400,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Palette.Blue400,
    onPrimary = Palette.Blue950,
    primaryContainer = Palette.Blue900,
    onPrimaryContainer = Palette.Blue100,
    secondary = Palette.Teal400,
    onSecondary = Palette.Teal950,
    secondaryContainer = Palette.Blue900,
    onSecondaryContainer = Palette.Blue100,
    tertiary = Palette.Amber400,
    onTertiary = Palette.Amber900,
    tertiaryContainer = Palette.Amber900,
    onTertiaryContainer = Palette.Amber100,
    error = Palette.Red400,
    onError = Palette.Red950,
    errorContainer = Palette.Red950,
    onErrorContainer = Palette.Red100,
    background = Palette.Slate900,
    onBackground = Palette.Slate100,
    surface = Palette.Slate900,
    onSurface = Palette.Slate100,
    surfaceVariant = Palette.Slate750,
    onSurfaceVariant = Palette.Slate400,
    surfaceContainerLowest = Palette.Slate950,
    surfaceContainerLow = Palette.Slate800,
    surfaceContainer = Palette.Slate850,
    surfaceContainerHigh = Palette.Slate750,
    surfaceContainerHighest = Palette.Slate700,
    surfaceBright = Palette.Slate750,
    surfaceDim = Palette.Slate950,
    outline = Palette.Slate500,
    outlineVariant = Palette.Slate700,
    inverseSurface = Palette.Slate100,
    inverseOnSurface = Palette.Slate800,
    inversePrimary = Palette.Blue600,
    scrim = Color.Black,
)

/** Semantic colors Material 3 has no slot for. */
@Immutable
data class StatusColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val LightStatus = StatusColors(
    success = Palette.Teal700,
    successContainer = Palette.Teal100,
    onSuccessContainer = Palette.Teal900,
    warning = Palette.Amber700,
    warningContainer = Palette.Amber100,
    onWarningContainer = Palette.Amber900,
)

private val DarkStatus = StatusColors(
    success = Palette.Teal400,
    successContainer = Palette.Teal900,
    onSuccessContainer = Palette.Teal100,
    warning = Palette.Amber400,
    warningContainer = Palette.Amber900,
    onWarningContainer = Palette.Amber100,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ImageCompressorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object AppTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
