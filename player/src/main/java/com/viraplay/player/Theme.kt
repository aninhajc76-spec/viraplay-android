package com.viraplay.player

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val VpBg = Color(0xFF010611)
val VpPanel = Color(0xFF07172F)
val VpPanelAlt = Color(0xFF0D2850)
val VpCyan = Color(0xFF13D2FF)
val VpPurple = Color(0xFF9B42FF)
val VpGreen = Color(0xFF48DF91)
val VpDanger = Color(0xFFFF617C)
val VpMuted = Color(0xFF9AA8C0)
val VpBorder = Color(0xFF334D72)
val VpSoft = Color(0xFF12203A)

val VPlayoColors = darkColorScheme(
    primary = VpCyan,
    secondary = VpPurple,
    background = VpBg,
    surface = VpPanel,
    surfaceVariant = VpPanelAlt,
    error = VpDanger
)
