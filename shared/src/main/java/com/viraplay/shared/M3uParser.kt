package com.viraplay.shared

/** Parser legado mantido para compatibilidade. A VPlayo 3.0 usa M3uSync em streaming. */
object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""", RegexOption.IGNORE_CASE)

    fun parse(lines: Sequence<String>): ParsedPlaylist {
        val channels = mutableListOf<ChannelItem>()
        var epgUrl: String? = null
        var attrs: Map<String, String> = emptyMap()
        var pendingName: String? = null
        var first = true

        lines.forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isBlank()) return@forEach

            if (first) {
                first = false
                if (line.startsWith("#EXTM3U", true)) {
                    val h = parseAttrs(line)
                    epgUrl = h["x-tvg-url"] ?: h["url-tvg"]
                    return@forEach
                }
            }

            when {
                line.startsWith("#EXTINF", true) -> {
                    attrs = parseAttrs(line)
                    pendingName = line.substringAfterLast(',', "Conteúdo").trim().ifBlank { "Conteúdo" }
                }
                !line.startsWith("#") && pendingName != null -> {
                    channels += ChannelItem(
                        name = attrs["tvg-name"]?.takeIf { it.isNotBlank() } ?: pendingName.orEmpty(),
                        url = line,
                        logo = attrs["tvg-logo"],
                        group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Outros",
                        tvgId = attrs["tvg-id"],
                        type = ContentType.LIVE
                    )
                    attrs = emptyMap()
                    pendingName = null
                }
            }
        }
        return ParsedPlaylist(channels, epgUrl)
    }

    fun parse(text: String): ParsedPlaylist = parse(text.lineSequence())

    private fun parseAttrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
}
