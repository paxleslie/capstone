package com.group5.corkboardApp.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Custom theme attributes for the hand-drawn look
data class HandDrawnStyle(
    val border: BorderStroke = BorderStroke(2.dp, Color.Black),
    val shape: RoundedCornerShape = RoundedCornerShape(0.dp) // Sharp corners like the drawing
)

val LocalHandDrawnStyle = staticCompositionLocalOf { HandDrawnStyle() }

object CorkboardTheme {
    val style: HandDrawnStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalHandDrawnStyle.current
}

/**
 * Global helper for the hand-drawn look.
 * This applies a thick black border and sharp corners.
 */
fun Modifier.handDrawnBorder() = this
    .border(width = 2.dp, color = Color.Black, shape = RoundedCornerShape(0.dp))

private val CorkboardColorScheme = lightColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = CorkboardGrid,
    onSecondary = Color.Black,
    tertiary = PostItMagenta,
    background = CorkboardBackground,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    outline = Color.Black,
    error = Color.Red,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, 
    dynamicColor: Boolean = false, 
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalHandDrawnStyle provides HandDrawnStyle()) {
        MaterialTheme(
            colorScheme = CorkboardColorScheme,
            typography = Typography,
            content = content
        )
    }
}
