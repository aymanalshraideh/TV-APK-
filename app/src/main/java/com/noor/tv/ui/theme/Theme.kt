package com.noor.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object NoorColors {
    val Bg = Color(0xFF07141A)
    val Panel = Color(0xFF0D2129)
    val Card = Color(0xFF12303A)
    val CardFocused = Color(0xFF1B4A55)
    val Green = Color(0xFF0F4C3A)
    val GreenLight = Color(0xFF2E8B6E)
    val Gold = Color(0xFFD4AF37)
    val Text = Color(0xFFF2EFE6)
    val TextDim = Color(0xFFA9B8B5)
    val Danger = Color(0xFFE57373)
    // Retro palette for the classic cartoons section
    val Retro = Color(0xFF2B1B3D)
    val RetroAccent = Color(0xFFFF8A3D)
}

@Composable
fun NoorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NoorColors.Gold,
            onPrimary = NoorColors.Bg,
            background = NoorColors.Bg,
            onBackground = NoorColors.Text,
            surface = NoorColors.Panel,
            onSurface = NoorColors.Text,
        ),
        content = content,
    )
}
