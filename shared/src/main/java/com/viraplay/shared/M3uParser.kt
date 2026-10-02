package com.viraplay.shared

object M3uParser {

    private val attrRegex =
        Regex("([\\w-]+)=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedPlaylist {
        val lines =
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()

        var epgUrl: String? = null
        val first = lines.firstOrNull().orEmpty()

        if (first.startsWith("#EXTM3U", ignoreCase = true)) {
            val attrs =
                attrRegex.findAll(first)
                    .associate {
                        it.groupValues[1].lowercase() to it.groupValues[2]
                    }

            epgUrl = attrs["x-tvg-url"] ?: attrs["url-tvg"]
        }

        val channels = mutableListOf<ChannelItem>()
        var pendingAttrs: Map<String, String>? = null
        var pendingName: String? = null

        for (line in lines) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingAttrs =
                    attrRegex.findAll(line)
                        .associate {
                            it.groupValues[1].lowercase() to it.groupValues[2]
                        }

                pendingName =
                    line.substringAfterLast(',', missingDelimiterValue = "Conteúdo").trim()
            } else if (!line.startsWith("#") && pendingName != null) {
                val attrs = pendingAttrs.orEmpty()

                val name =
                    attrs["tvg-name"]
                        .takeUnless { it.isNullOrBlank() }
                        ?: pendingName!!

                val group =
                    attrs["group-title"]
                        .takeUnless { it.isNullOrBlank() }
                        ?: "Outros"

                channels +=
                    ChannelItem(
                        name = name,
                        url = line,
                        logo = attrs["tvg-logo"],
                        group = group,
                        tvgId = attrs["tvg-id"],
                        type = detectType(name = name, group = group, url = line)
                    )

                pendingAttrs = null
                pendingName = null
            }
        }

        return ParsedPlaylist(channels = channels, epgUrl = epgUrl)
    }

    private fun detectType(
        name: String,
        group: String,
        url: String
    ): ContentType {
        val normalized = "$group $name $url".lowercase()

        val seriesWords =
            listOf(
                "série",
                "serie",
                "series",
                "temporada",
                "season",
                "/series/"
            )

        if (seriesWords.any { normalized.contains(it) }) {
            return ContentType.SERIES
        }

        val movieWords =
            listOf(
                "filme",
                "filmes",
                "movie",
                "movies",
                "cinema",
                "vod",
                "/movie/"
            )

        if (movieWords.any { normalized.contains(it) }) {
            return ContentType.MOVIE
        }

        return ContentType.LIVE
    }
}
