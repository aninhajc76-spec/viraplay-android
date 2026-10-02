package com.viraplay.player

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val VpBg = Color(0xFF020711)
val VpPanel = Color(0xFF081831)
val VpPanelAlt = Color(0xFF0D2345)
val VpCyan = Color(0xFF11C8F5)
val VpPurple = Color(0xFF8F3CFF)
val VpGreen = Color(0xFF46DB8B)
val VpDanger = Color(0xFFFF5E78)
val VpMuted = Color(0xFF98A3B8)
val VpBorder = Color(0xFF344564)

val ViraPlayColors = darkColorScheme(
    primary = VpCyan,
    secondary = VpPurple,
    background = VpBg,
    surface = VpPanel,
    error = VpDanger
)
