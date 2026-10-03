package com.viraplay.player

private val qualityTokens = listOf(
    Regex("(?i)\\bUHD\\b|\\b4K\\b"),
    Regex("(?i)\\bFHD\\b|\\bFULL\\s*HD\\b|\\b1080P?\\b"),
    Regex("(?i)\\bHD\\b|\\b720P?\\b"),
    Regex("(?i)\\bSD\\b|\\b480P?\\b")
)

fun channelBaseName(name: String): String {
    var out = name
        .replace(Regex("(?i)\\[(?:4K|UHD|FHD|HD|SD|1080P?|720P?|480P?)\\]"), " ")
        .replace(Regex("(?i)\\((?:4K|UHD|FHD|HD|SD|1080P?|720P?|480P?)\\)"), " ")
    qualityTokens.forEach { out = out.replace(it, " ") }
    out = out
        .replace(Regex("(?i)(?:²|³|⁴)$"), " ")
        .replace(Regex("(?i)\\s+(?:2|3|4)$"), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '|', ':')
    return out.ifBlank { name.trim() }
}

fun qualityLabel(name: String): String = when {
    Regex("(?i)\\b4K\\b|\\bUHD\\b").containsMatchIn(name) -> "4K"
    Regex("(?i)\\bFHD\\b|FULL\\s*HD|1080P?").containsMatchIn(name) -> "FHD"
    Regex("(?i)\\bHD\\b|720P?").containsMatchIn(name) -> "HD"
    Regex("(?i)\\bSD\\b|480P?").containsMatchIn(name) -> "SD"
    else -> "PADRÃO"
}

fun qualityRank(label: String): Int = when (label.uppercase()) {
    "4K" -> 4
    "FHD" -> 3
    "HD" -> 2
    "SD" -> 1
    else -> 0
}

fun groupLiveItems(items: List<CatalogItem>): List<CatalogItem> =
    items.groupBy { channelBaseName(it.name).lowercase() }
        .values
        .map { variants ->
            variants.maxByOrNull { qualityRank(qualityLabel(it.name)) } ?: variants.first()
        }
        .sortedBy { it.name.lowercase() }

fun liveVariants(items: List<CatalogItem>, item: CatalogItem): List<CatalogItem> =
    items.filter { channelBaseName(it.name).equals(channelBaseName(item.name), ignoreCase = true) }
        .sortedByDescending { qualityRank(qualityLabel(it.name)) }
