package com.viraplay.player

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val VpBg = Color(0xFF02040B)
val VpPanel = Color(0xFF08111F)
val VpPanelAlt = Color(0xFF0D1C31)
val VpCyan = Color(0xFF17D4FF)
val VpPurple = Color(0xFF7B2DFF)
val VpGreen = Color(0xFF42E79A)
val VpDanger = Color(0xFFFF607C)
val VpMuted = Color(0xFF9BA8BD)
val VpBorder = Color(0xFF213958)
val VpSoft = Color(0xFF101A2A)
val VpGold = Color(0xFFFFD166)

val VPlayoColors = darkColorScheme(
    primary = VpCyan,
    secondary = VpPurple,
    background = VpBg,
    surface = VpPanel,
    surfaceVariant = VpPanelAlt,
    error = VpDanger
)
