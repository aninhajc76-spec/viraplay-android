package com.viraplay.player

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val VpBg = Color(0xFF020714)
val VpPanel = Color(0xFF07162A)
val VpPanelAlt = Color(0xFF0B2340)
val VpCyan = Color(0xFF12D8FF)
val VpPurple = Color(0xFF7357FF)
val VpGreen = Color(0xFF27E6A5)
val VpDanger = Color(0xFFFF607C)
val VpMuted = Color(0xFFA9B7CC)
val VpBorder = Color(0xFF24517A)
val VpSoft = Color(0xFF0A1A31)
val VpGold = Color(0xFFFFD166)

val VPlayoColors = darkColorScheme(
    primary = VpCyan,
    secondary = VpPurple,
    background = VpBg,
    surface = VpPanel,
    surfaceVariant = VpPanelAlt,
    error = VpDanger
)
