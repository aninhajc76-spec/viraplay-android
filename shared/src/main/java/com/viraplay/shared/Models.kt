package com.viraplay.shared

enum class ContentType {
    LIVE,
    MOVIE,
    SERIES,
    EPISODE
}

data class ChannelItem(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = "Outros",
    val tvgId: String? = null,
    val type: ContentType = ContentType.LIVE
)

data class ParsedPlaylist(
    val channels: List<ChannelItem>,
    val epgUrl: String? = null
)

data class DeviceConfig(
    val enabled: Boolean,
    val playlistUrl: String?,
    val playlistName: String? = null
)

data class AdminDevice(
    val deviceId: String,
    val pairingCode: String,
    val label: String?,
    val platform: String?,
    val enabled: Boolean,
    val playlistUrl: String?
)
