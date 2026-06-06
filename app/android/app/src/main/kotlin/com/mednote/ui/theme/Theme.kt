package com.mednote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MedNoteColors = lightColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color.White,
    secondary = Color(0xFF16A34A),
    background = Color(0xFFF5F7F8),
    surface = Color.White
)

@Composable
fun MedNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MedNoteColors,
        content = content
    )
}
