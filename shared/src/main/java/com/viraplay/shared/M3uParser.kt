package com.viraplay.shared

object M3uParser {

    private val attrRegex =
        Regex("""([\w-]+)="([^"]*)"""", RegexOption.IGNORE_CASE)

    fun parse(lines: Sequence<String>): ParsedPlaylist {
        val channels = mutableListOf<ChannelItem>()
        var epgUrl: String? = null
        var pendingAttrs: Map<String, String> = emptyMap()
        var pendingName: String? = null
        var firstMeaningfulLine = true

        lines.forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isBlank()) return@forEach

            if (firstMeaningfulLine) {
                firstMeaningfulLine = false

                if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                    val attrs = parseAttrs(line)
                    epgUrl =
                        attrs["x-tvg-url"]
                            ?: attrs["url-tvg"]
                    return@forEach
                }
            }

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingAttrs = parseAttrs(line)
                    pendingName =
                        line.substringAfterLast(',', "Conteúdo")
                            .trim()
                            .ifBlank { "Conteúdo" }
                }

                !line.startsWith("#") && pendingName != null -> {
                    val attrs = pendingAttrs

                    val name =
                        attrs["tvg-name"]
                            ?.takeIf { it.isNotBlank() }
                            ?: pendingName.orEmpty()

                    val group =
                        attrs["group-title"]
                            ?.takeIf { it.isNotBlank() }
                            ?: "Outros"

                    channels +=
                        ChannelItem(
                            name = name,
                            url = line,
                            logo = attrs["tvg-logo"],
                            group = group,
                            tvgId = attrs["tvg-id"],
                            type =
                                detectType(
                                    name = name,
                                    group = group,
                                    url = line
                                )
                        )

                    pendingAttrs = emptyMap()
                    pendingName = null
                }
            }
        }

        return ParsedPlaylist(
            channels = channels,
            epgUrl = epgUrl
        )
    }

    fun parse(text: String): ParsedPlaylist =
        parse(text.lineSequence())

    private fun parseAttrs(
        line: String
    ): Map<String, String> =
        attrRegex.findAll(line).associate {
            it.groupValues[1].lowercase() to
                it.groupValues[2]
        }

    private fun detectType(
        name: String,
        group: String,
        url: String
    ): ContentType {
        val value =
            "$group $name $url".lowercase()

        val seriesTokens =
            listOf(
                "/series/",
                " série",
                " series",
                "serie ",
                "temporada",
                "season "
            )

        if (seriesTokens.any(value::contains)) {
            return ContentType.SERIES
        }

        val movieTokens =
            listOf(
                "/movie/",
                "filme",
                "movie",
                "cinema",
                "vod"
            )

        if (movieTokens.any(value::contains)) {
            return ContentType.MOVIE
        }

        return ContentType.LIVE
    }
}
