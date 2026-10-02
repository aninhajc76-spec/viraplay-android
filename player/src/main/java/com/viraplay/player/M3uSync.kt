package com.viraplay.player

import com.viraplay.shared.ContentType
import com.viraplay.shared.Http
import java.security.MessageDigest

class M3uSync {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val seRegex = Regex("""(?i)^(.*?)(?:[\s._-]+)S(\d{1,2})E(\d{1,4})(?:\b|[\s._-])""")
    private val xRegex = Regex("""(?i)^(.*?)(?:[\s._-]+)(\d{1,2})x(\d{1,4})(?:\b|[\s._-])""")
    private val wordsRegex = Regex("""(?i)^(.*?)(?:[\s._-]+)(?:temporada|temp)\s*(\d{1,2}).*?(?:epis[oó]dio|ep)\s*(\d{1,4})""")

    fun sync(
        playlistUrl: String,
        db: CatalogDb,
        onProgress: (Int) -> Unit = {}
    ): SyncResult {
        val generation = db.newGeneration()
        val seenCategories = hashSetOf<String>()
        val seriesMap = linkedMapOf<String, CatalogSeed>()
        var categoryIndex = 0
        var itemIndex = 0

        try {
            val count = db.inTransaction {
                Http.withReader(playlistUrl) { reader ->
                var attrs: Map<String, String> = emptyMap()
                var pendingName: String? = null
                var inserted = 0

                reader.lineSequence().forEach { raw ->
                    val line = raw.trim().removePrefix("\uFEFF")
                    if (line.isBlank()) return@forEach

                    if (line.startsWith("#EXTINF", ignoreCase = true)) {
                        attrs = attrRegex.findAll(line).associate {
                            it.groupValues[1].lowercase() to it.groupValues[2]
                        }
                        pendingName = line.substringAfterLast(',', "Conteúdo")
                            .trim()
                            .ifBlank { "Conteúdo" }
                        return@forEach
                    }

                    if (!line.startsWith("#") && pendingName != null) {
                        val rawName = attrs["tvg-name"]?.takeIf { it.isNotBlank() } ?: pendingName.orEmpty()
                        val group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Outros"
                        val logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                        val type = classify(group, line, rawName)
                        val categoryId = "m3u:${stableKey("${type.name}:$group")}" 

                        if (seenCategories.add("${type.name}|$categoryId")) {
                            db.putCategory(generation, typeForCategory(type), categoryId, group, categoryIndex++)
                        }

                        when (type) {
                            ContentType.SERIES, ContentType.EPISODE -> {
                                val info = parseEpisode(rawName)
                                val seriesKey = info?.first ?: cleanupSeriesName(rawName)
                                val season = info?.second
                                val episode = info?.third
                                val episodeKey = "ME:${stableKey(line)}"
                                db.putItem(
                                    generation,
                                    CatalogSeed(
                                        itemKey = episodeKey,
                                        sourceId = null,
                                        name = rawName,
                                        url = line,
                                        image = logo,
                                        backdrop = null,
                                        categoryId = categoryId,
                                        categoryName = group,
                                        type = ContentType.EPISODE,
                                        seriesKey = seriesKey,
                                        season = season,
                                        episode = episode,
                                        sortIndex = itemIndex++
                                    )
                                )
                                val seriesMapKey = "$categoryId|$seriesKey"
                                if (!seriesMap.containsKey(seriesMapKey)) {
                                    seriesMap[seriesMapKey] = CatalogSeed(
                                        itemKey = "MS:${stableKey(seriesMapKey)}",
                                        sourceId = null,
                                        name = seriesKey,
                                        url = null,
                                        image = logo,
                                        backdrop = null,
                                        categoryId = categoryId,
                                        categoryName = group,
                                        type = ContentType.SERIES,
                                        seriesKey = seriesKey,
                                        sortIndex = itemIndex++
                                    )
                                }
                            }

                            else -> {
                                db.putItem(
                                    generation,
                                    CatalogSeed(
                                        itemKey = if (type == ContentType.LIVE) {
                                            "ML:${stableKey(line)}"
                                        } else {
                                            "MM:${stableKey(line)}"
                                        },
                                        sourceId = null,
                                        name = rawName,
                                        url = line,
                                        image = logo,
                                        backdrop = null,
                                        categoryId = categoryId,
                                        categoryName = group,
                                        type = type,
                                        sortIndex = itemIndex++
                                    )
                                )
                            }
                        }

                        inserted++
                        if (inserted % 2500 == 0) onProgress(inserted)
                        attrs = emptyMap()
                        pendingName = null
                    }
                }

                seriesMap.values.forEach { db.putItem(generation, it) }
                inserted + seriesMap.size
                }
            }

            val sourceName = runCatching {
                java.net.URL(playlistUrl).host
            }.getOrDefault("Lista M3U")

            db.completeGeneration(generation, playlistUrl, "M3U", sourceName)
            return SyncResult(count, "M3U", sourceName)
        } catch (e: Throwable) {
            db.cancelGeneration(generation)
            throw e
        }
    }

    private fun typeForCategory(type: ContentType): ContentType =
        if (type == ContentType.EPISODE) ContentType.SERIES else type

    private fun classify(group: String, url: String, name: String): ContentType {
        val g = group.lowercase()
        val u = url.lowercase()
        val n = name.lowercase()

        if (u.contains("/live/")) {
            return ContentType.LIVE
        }

        if (u.contains("/series/") ||
            g.startsWith("series") || g.startsWith("séries") ||
            g.contains("| series") || g.contains("| séries") ||
            g.contains("seriados") ||
            seRegex.containsMatchIn(name) || xRegex.containsMatchIn(name) ||
            n.contains(" temporada ") && n.contains(" ep")) {
            return ContentType.EPISODE
        }

        if (u.contains("/movie/") ||
            g.startsWith("filmes") || g.startsWith("filme") ||
            g.startsWith("movies") || g.contains("| filmes") ||
            g.contains("vod |") || g.startsWith("vod ")) {
            return ContentType.MOVIE
        }

        return ContentType.LIVE
    }

    private fun parseEpisode(name: String): Triple<String, Int, Int>? {
        fun toTriple(match: MatchResult?): Triple<String, Int, Int>? {
            if (match == null) return null
            val key = match.groupValues[1].trim().trimEnd('-', '_', '.', ' ')
            val season = match.groupValues[2].toIntOrNull() ?: return null
            val episode = match.groupValues[3].toIntOrNull() ?: return null
            return Triple(key.ifBlank { cleanupSeriesName(name) }, season, episode)
        }
        return toTriple(seRegex.find(name)) ?: toTriple(xRegex.find(name)) ?: toTriple(wordsRegex.find(name))
    }

    private fun cleanupSeriesName(name: String): String = name
        .replace(Regex("""(?i)[\s._-]+S\d{1,2}E\d{1,4}.*$"""), "")
        .replace(Regex("""(?i)[\s._-]+\d{1,2}x\d{1,4}.*$"""), "")
        .trim()
        .ifBlank { name }

    private fun stableKey(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
