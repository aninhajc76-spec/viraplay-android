package com.viraplay.player

import com.viraplay.shared.ContentType

data class CatalogItem(
    val itemKey: String,
    val sourceId: String?,
    val name: String,
    val url: String?,
    val image: String?,
    val backdrop: String?,
    val categoryId: String?,
    val categoryName: String,
    val type: ContentType,
    val seriesId: String?,
    val seriesKey: String?,
    val season: Int?,
    val episode: Int?,
    val plot: String?,
    val rating: String?,
    val containerExtension: String?,
    val favorite: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val watched: Boolean
        get() = durationMs > 0L && progressFraction >= 0.90f
}

data class CatalogSeed(
    val itemKey: String,
    val sourceId: String?,
    val name: String,
    val url: String?,
    val image: String?,
    val backdrop: String?,
    val categoryId: String?,
    val categoryName: String,
    val type: ContentType,
    val seriesId: String? = null,
    val seriesKey: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val plot: String? = null,
    val rating: String? = null,
    val containerExtension: String? = null,
    val sortIndex: Int = 0
)

data class CategoryEntry(
    val id: String,
    val name: String,
    val count: Int
)

data class SyncResult(
    val count: Int,
    val sourceKind: String,
    val sourceName: String
)

data class EpgProgram(
    val title: String,
    val description: String,
    val start: String,
    val end: String
)

data class XtreamCredentials(
    val baseUrl: String,
    val username: String,
    val password: String,
    val liveExtension: String
)
