package com.viraplay.shared

object M3uParser {
    private val attrRegex = Regex("""([\\w-]+)=\"([^\"]*)\"""")

    fun parse(text: String): ParsedPlaylist {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        var epgUrl: String? = null
        val first = lines.firstOrNull().orEmpty()
        if (first.startsWith("#EXTM3U", ignoreCase = true)) {
            val attrs = attrRegex.findAll(first).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            epgUrl = attrs["x-tvg-url"] ?: attrs["url-tvg"]
        }

        val channels = mutableListOf<ChannelItem>()
        var pending: Map<String, String>? = null
        var pendingName: String? = null

        for (line in lines) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pending = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                pendingName = line.substringAfterLast(',', missingDelimiterValue = "Canal").trim()
            } else if (!line.startsWith("#") && pendingName != null) {
                val attrs = pending.orEmpty()
                channels += ChannelItem(
                    name = attrs["tvg-name"].takeUnless { it.isNullOrBlank() } ?: pendingName!!,
                    url = line,
                    logo = attrs["tvg-logo"],
                    group = attrs["group-title"].takeUnless { it.isNullOrBlank() } ?: "Outros",
                    tvgId = attrs["tvg-id"]
                )
                pending = null
                pendingName = null
            }
        }
        return ParsedPlaylist(channels = channels, epgUrl = epgUrl)
    }
}
