package com.viraplay.player

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import com.viraplay.shared.ContentType
import com.viraplay.shared.Http
import java.net.URLEncoder
import org.json.JSONObject

class XtreamSync {
    fun sync(
        playlistUrl: String,
        db: CatalogDb,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): SyncResult {
        val credentials = SourceResolver.xtreamFromPlaylist(playlistUrl)
            ?: throw IllegalArgumentException("Lista não compatível com API Xtream")

        val generation = db.newGeneration()
        val liveCategories = linkedMapOf<String, String>()
        val movieCategories = linkedMapOf<String, String>()
        val seriesCategories = linkedMapOf<String, String>()
        var total = 0

        try {
            loadCategories(credentials, "get_live_categories", ContentType.LIVE, generation, db, liveCategories)
            loadCategories(credentials, "get_vod_categories", ContentType.MOVIE, generation, db, movieCategories)
            loadCategories(credentials, "get_series_categories", ContentType.SERIES, generation, db, seriesCategories)

            val live = db.inTransaction {
                streamArray(api(credentials, "get_live_streams")) { data, index ->
                    val id = data["stream_id"].orEmpty()
                    if (id.isBlank()) return@streamArray
                    val categoryId = data["category_id"].orEmpty().ifBlank { "0" }
                    if (!liveCategories.containsKey(categoryId)) {
                        liveCategories[categoryId] = "Outros"
                        db.putCategory(generation, ContentType.LIVE, categoryId, "Outros", 9999)
                    }
                    val direct = data["direct_source"].cleanNull()
                    val streamUrl = direct ?: "${credentials.baseUrl}/live/${enc(credentials.username)}/${enc(credentials.password)}/$id.${credentials.liveExtension}"
                    db.putItem(
                        generation,
                        CatalogSeed(
                            itemKey = "L:$id",
                            sourceId = id,
                            name = data["name"].orEmpty().ifBlank { "Canal $id" },
                            url = streamUrl,
                            image = data["stream_icon"].cleanNull(),
                            backdrop = null,
                            categoryId = categoryId,
                            categoryName = liveCategories[categoryId] ?: "Outros",
                            type = ContentType.LIVE,
                            containerExtension = credentials.liveExtension,
                            sortIndex = data["num"]?.toIntOrNull() ?: index
                        )
                    )
                }
            }
            total += live
            onProgress("Canais", live)

            val movies = db.inTransaction {
                streamArray(api(credentials, "get_vod_streams")) { data, index ->
                    val id = data["stream_id"].orEmpty()
                    if (id.isBlank()) return@streamArray
                    val categoryId = data["category_id"].orEmpty().ifBlank { "0" }
                    if (!movieCategories.containsKey(categoryId)) {
                        movieCategories[categoryId] = "Outros"
                        db.putCategory(generation, ContentType.MOVIE, categoryId, "Outros", 9999)
                    }
                    val ext = data["container_extension"].cleanNull() ?: "mp4"
                    val direct = data["direct_source"].cleanNull()
                    val streamUrl = direct ?: "${credentials.baseUrl}/movie/${enc(credentials.username)}/${enc(credentials.password)}/$id.$ext"
                    db.putItem(
                        generation,
                        CatalogSeed(
                            itemKey = "M:$id",
                            sourceId = id,
                            name = data["name"].orEmpty().ifBlank { "Filme $id" },
                            url = streamUrl,
                            image = data["stream_icon"].cleanNull(),
                            backdrop = null,
                            categoryId = categoryId,
                            categoryName = movieCategories[categoryId] ?: "Outros",
                            type = ContentType.MOVIE,
                            rating = data["rating"].cleanNull(),
                            containerExtension = ext,
                            sortIndex = data["num"]?.toIntOrNull() ?: index
                        )
                    )
                }
            }
            total += movies
            onProgress("Filmes", movies)

            val series = db.inTransaction {
                streamArray(api(credentials, "get_series")) { data, index ->
                    val id = data["series_id"].orEmpty()
                    if (id.isBlank()) return@streamArray
                    val categoryId = data["category_id"].orEmpty().ifBlank { "0" }
                    if (!seriesCategories.containsKey(categoryId)) {
                        seriesCategories[categoryId] = "Outros"
                        db.putCategory(generation, ContentType.SERIES, categoryId, "Outros", 9999)
                    }
                    db.putItem(
                        generation,
                        CatalogSeed(
                            itemKey = "S:$id",
                            sourceId = id,
                            name = data["name"].orEmpty().ifBlank { "Série $id" },
                            url = null,
                            image = data["cover"].cleanNull(),
                            backdrop = data["backdrop_path"].cleanNull(),
                            categoryId = categoryId,
                            categoryName = seriesCategories[categoryId] ?: "Outros",
                            type = ContentType.SERIES,
                            seriesId = id,
                            seriesKey = data["name"].cleanNull(),
                            plot = data["plot"].cleanNull(),
                            rating = data["rating"].cleanNull(),
                            sortIndex = data["num"]?.toIntOrNull() ?: index
                        )
                    )
                }
            }
            total += series
            onProgress("Séries", series)

            if (total <= 0) {
                throw IllegalStateException("A API não retornou conteúdo")
            }

            val sourceName = runCatching { java.net.URL(credentials.baseUrl).host }
                .getOrDefault("Servidor")

            db.completeGeneration(generation, playlistUrl, "XTREAM", sourceName)
            return SyncResult(total, "XTREAM", sourceName)
        } catch (e: Throwable) {
            db.cancelGeneration(generation)
            throw e
        }
    }

    fun loadEpisodes(
        playlistUrl: String,
        parent: CatalogItem,
        db: CatalogDb,
        force: Boolean = false
    ): List<CatalogItem> {
        if (parent.seriesId.isNullOrBlank()) return db.episodes(parent)

        val now = System.currentTimeMillis()
        val loadedAt = db.seriesLoadedAt(parent)
        if (!force && loadedAt > 0L && now - loadedAt < 24L * 60L * 60L * 1000L) {
            val cached = db.episodes(parent)
            if (cached.isNotEmpty()) return cached
        }

        val credentials = SourceResolver.xtreamFromPlaylist(playlistUrl)
            ?: return db.episodes(parent)

        val json = Http.getText(
            api(credentials, "get_series_info", mapOf("series_id" to parent.seriesId)),
            maxChars = 12_000_000
        )
        val root = JSONObject(json)
        val episodesObject = root.optJSONObject("episodes") ?: return emptyList()
        val seeds = mutableListOf<CatalogSeed>()
        var sort = 0

        val seasonKeys = episodesObject.keys().asSequence().toList()
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

        seasonKeys.forEach { seasonKey ->
            val seasonNumber = seasonKey.toIntOrNull()
            val array = episodesObject.optJSONArray(seasonKey) ?: return@forEach
            for (i in 0 until array.length()) {
                val e = array.optJSONObject(i) ?: continue
                val id = e.optString("id")
                if (id.isBlank()) continue
                val info = e.optJSONObject("info")
                val episodeNumber = e.optInt("episode_num", i + 1)
                val ext = e.optString("container_extension").takeIf { it.isNotBlank() && it != "null" } ?: "mp4"
                val title = e.optString("title").takeIf { it.isNotBlank() } ?: "Episódio $episodeNumber"
                val url = "${credentials.baseUrl}/series/${enc(credentials.username)}/${enc(credentials.password)}/$id.$ext"
                val image = info?.optString("movie_image")?.cleanNull() ?: parent.image
                val plot = info?.optString("plot")?.cleanNull()
                val rating = info?.optString("rating")?.cleanNull()

                seeds += CatalogSeed(
                    itemKey = "E:$id",
                    sourceId = id,
                    name = title,
                    url = url,
                    image = image,
                    backdrop = parent.backdrop,
                    categoryId = parent.categoryId,
                    categoryName = parent.categoryName,
                    type = ContentType.EPISODE,
                    seriesId = parent.seriesId,
                    seriesKey = parent.name,
                    season = seasonNumber,
                    episode = episodeNumber,
                    plot = plot,
                    rating = rating,
                    containerExtension = ext,
                    sortIndex = sort++
                )
            }
        }

        db.replaceEpisodes(parent, seeds)
        return db.episodes(parent)
    }

    fun shortEpg(playlistUrl: String, streamId: String): List<EpgProgram> {
        val credentials = SourceResolver.xtreamFromPlaylist(playlistUrl) ?: return emptyList()
        return runCatching {
            val json = Http.getText(
                api(credentials, "get_short_epg", mapOf("stream_id" to streamId, "limit" to "5")),
                maxChars = 1_500_000
            )
            val root = JSONObject(json)
            val arr = root.optJSONArray("epg_listings") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        EpgProgram(
                            title = decodeMaybeBase64(o.optString("title")).ifBlank { "Programação" },
                            description = decodeMaybeBase64(o.optString("description")),
                            start = o.optString("start"),
                            end = o.optString("end")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadCategories(
        credentials: XtreamCredentials,
        action: String,
        type: ContentType,
        generation: String,
        db: CatalogDb,
        target: MutableMap<String, String>
    ) {
        db.inTransaction {
            streamArray(api(credentials, action)) { data, index ->
                val id = data["category_id"].orEmpty().ifBlank { index.toString() }
                val name = data["category_name"].orEmpty().ifBlank { "Outros" }
                target[id] = name
                db.putCategory(generation, type, id, name, index)
            }
        }
    }

    private fun streamArray(
        url: String,
        onObject: (Map<String, String?>, Int) -> Unit
    ): Int = Http.withReader(url) { buffered ->
        val reader = JsonReader(buffered)
        var index = 0
        reader.beginArray()
        while (reader.hasNext()) {
            val data = readSimpleObject(reader)
            onObject(data, index)
            index++
        }
        reader.endArray()
        index
    }

    private fun readSimpleObject(reader: JsonReader): Map<String, String?> {
        val out = hashMapOf<String, String?>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            val value = when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                JsonToken.NULL -> {
                    reader.nextNull()
                    null
                }
                else -> {
                    reader.skipValue()
                    null
                }
            }
            out[name] = value
        }
        reader.endObject()
        return out
    }

    private fun api(
        c: XtreamCredentials,
        action: String,
        extras: Map<String, String> = emptyMap()
    ): String = buildString {
        append(c.baseUrl)
        append("/player_api.php?username=")
        append(enc(c.username))
        append("&password=")
        append(enc(c.password))
        append("&action=")
        append(enc(action))
        extras.forEach { (k, v) ->
            append('&')
            append(enc(k))
            append('=')
            append(enc(v))
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun String?.cleanNull(): String? =
        this?.takeIf { it.isNotBlank() && it != "null" && it != "[]" }

    private fun decodeMaybeBase64(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val decoded = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
            if (decoded.any { it.isLetterOrDigit() }) decoded else value
        }.getOrDefault(value)
    }
}
