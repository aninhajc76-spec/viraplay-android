package com.viraplay.player

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.viraplay.shared.ContentType
import java.io.BufferedReader

data class CatalogItem(
    val id: Long,
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val type: ContentType,
    val seriesKey: String?,
    val season: Int?,
    val episode: Int?,
    val favorite: Boolean,
    val progressMs: Long,
    val durationMs: Long
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val watched: Boolean
        get() = durationMs > 0L && progressFraction >= 0.90f
}

class CatalogDb(context: Context) : SQLiteOpenHelper(
    context,
    "viraplay_catalog_v2.db",
    null,
    1
) {
    private val attrRegex = Regex("""([\\w-]+)=\"([^\"]*)\"""")
    private val seasonEpisode = Regex("""(?i)^(.*?)(?:[\\s._-]+)S(\\d{1,2})E(\\d{1,3})(?:\\b|[\\s._-])""")
    private val xEpisode = Regex("""(?i)^(.*?)(?:[\\s._-]+)(\\d{1,2})x(\\d{1,3})(?:\\b|[\\s._-])""")

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE catalog (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                url TEXT NOT NULL UNIQUE,
                logo TEXT,
                group_name TEXT NOT NULL,
                content_type TEXT NOT NULL,
                series_key TEXT,
                season INTEGER,
                episode INTEGER,
                sort_index INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_catalog_type_group ON catalog(content_type, group_name)")
        db.execSQL("CREATE INDEX idx_catalog_series ON catalog(series_key, season, episode)")
        db.execSQL("CREATE TABLE favorites (url TEXT PRIMARY KEY)")
        db.execSQL(
            """
            CREATE TABLE progress (
                url TEXT PRIMARY KEY,
                position_ms INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun hasCatalog(): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM catalog LIMIT 1",
        null
    ).use { it.moveToFirst() }

    fun countAll(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM catalog",
        null
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun getMeta(key: String): String? = readableDatabase.rawQuery(
        "SELECT value FROM meta WHERE key=?",
        arrayOf(key)
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun putMeta(db: SQLiteDatabase, key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun replaceFromM3u(reader: BufferedReader, sourceUrl: String): Int {
        val db = writableDatabase
        var attrs: Map<String, String> = emptyMap()
        var pendingName: String? = null
        var index = 0
        var inserted = 0

        db.beginTransaction()
        try {
            db.delete("catalog", null, null)
            val stmt = db.compileStatement(
                """
                INSERT OR REPLACE INTO catalog(
                    name,url,logo,group_name,content_type,series_key,season,episode,sort_index
                ) VALUES(?,?,?,?,?,?,?,?,?)
                """.trimIndent()
            )

            reader.lineSequence().forEach { raw ->
                val line = raw.trim().removePrefix("\uFEFF")
                if (line.isBlank()) return@forEach

                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    attrs = attrRegex.findAll(line).associate {
                        it.groupValues[1].lowercase() to it.groupValues[2]
                    }
                    pendingName = line.substringAfterLast(',', "Conteúdo").trim().ifBlank { "Conteúdo" }
                    return@forEach
                }

                if (!line.startsWith("#") && pendingName != null) {
                    val name = attrs["tvg-name"]?.takeIf { it.isNotBlank() } ?: pendingName.orEmpty()
                    val group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Outros"
                    val logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    val type = detectType(name, group, line)
                    val seriesInfo = if (type == ContentType.SERIES) parseSeries(name) else null

                    stmt.clearBindings()
                    stmt.bindString(1, name)
                    stmt.bindString(2, line)
                    if (logo != null) stmt.bindString(3, logo) else stmt.bindNull(3)
                    stmt.bindString(4, group)
                    stmt.bindString(5, type.name)
                    if (seriesInfo != null) {
                        stmt.bindString(6, seriesInfo.first)
                        stmt.bindLong(7, seriesInfo.second.toLong())
                        stmt.bindLong(8, seriesInfo.third.toLong())
                    } else {
                        stmt.bindNull(6)
                        stmt.bindNull(7)
                        stmt.bindNull(8)
                    }
                    stmt.bindLong(9, index.toLong())
                    stmt.executeInsert()

                    index++
                    inserted++
                    attrs = emptyMap()
                    pendingName = null
                }
            }

            putMeta(db, "playlist_url", sourceUrl)
            putMeta(db, "last_sync", System.currentTimeMillis().toString())
            db.setTransactionSuccessful()
            return inserted
        } finally {
            db.endTransaction()
        }
    }

    fun groups(type: ContentType): List<String> = readableDatabase.rawQuery(
        "SELECT DISTINCT group_name FROM catalog WHERE content_type=? ORDER BY group_name COLLATE NOCASE",
        arrayOf(type.name)
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun queryItems(
        type: ContentType,
        group: String? = null,
        search: String = "",
        limit: Int = 1000
    ): List<CatalogItem> {
        val args = mutableListOf(type.name)
        val where = buildString {
            append("c.content_type=?")
            if (!group.isNullOrBlank() && group != "Todos") {
                append(" AND c.group_name=?")
                args += group
            }
            if (search.isNotBlank()) {
                append(" AND (c.name LIKE ? OR c.group_name LIKE ?)")
                val q = "%${search.trim()}%"
                args += q
                args += q
            }
        }
        args += limit.toString()
        return readableDatabase.rawQuery(baseSelect() + " WHERE $where ORDER BY c.sort_index LIMIT ?", args.toTypedArray()).use(::readItems)
    }

    fun querySeries(group: String? = null, search: String = "", limit: Int = 800): List<CatalogItem> {
        val args = mutableListOf<String>()
        val where = buildString {
            append("c.content_type='SERIES'")
            if (!group.isNullOrBlank() && group != "Todos") {
                append(" AND c.group_name=?")
                args += group
            }
            if (search.isNotBlank()) {
                append(" AND (COALESCE(c.series_key,c.name) LIKE ? OR c.group_name LIKE ?)")
                val q = "%${search.trim()}%"
                args += q
                args += q
            }
        }
        args += limit.toString()
        return readableDatabase.rawQuery(
            """
            SELECT MIN(c.id), COALESCE(c.series_key,c.name), MIN(c.url), MAX(c.logo), MIN(c.group_name),
                   'SERIES', COALESCE(c.series_key,c.name), MIN(c.season), MIN(c.episode),
                   0, 0, 0
            FROM catalog c
            WHERE $where
            GROUP BY COALESCE(c.series_key,c.name)
            ORDER BY MIN(c.sort_index)
            LIMIT ?
            """.trimIndent(),
            args.toTypedArray()
        ).use(::readItems)
    }

    fun episodes(seriesKey: String): List<CatalogItem> = readableDatabase.rawQuery(
        baseSelect() +
            " WHERE COALESCE(c.series_key,c.name)=? ORDER BY COALESCE(c.season,9999), COALESCE(c.episode,9999), c.sort_index",
        arrayOf(seriesKey)
    ).use(::readItems)

    fun queryFavorites(search: String = "", limit: Int = 1000): List<CatalogItem> {
        val args = mutableListOf<String>()
        val searchSql = if (search.isBlank()) "" else {
            val q = "%${search.trim()}%"
            args += q
            args += q
            " AND (c.name LIKE ? OR c.group_name LIKE ?)"
        }
        args += limit.toString()
        return readableDatabase.rawQuery(
            baseSelect() + " WHERE f.url IS NOT NULL $searchSql ORDER BY c.sort_index LIMIT ?",
            args.toTypedArray()
        ).use(::readItems)
    }

    fun continueWatching(limit: Int = 24): List<CatalogItem> = readableDatabase.rawQuery(
        baseSelect() +
            " WHERE p.position_ms>30000 AND (p.duration_ms<=0 OR CAST(p.position_ms AS REAL)/CAST(p.duration_ms AS REAL)<0.95)" +
            " ORDER BY p.updated_at DESC LIMIT ?",
        arrayOf(limit.toString())
    ).use(::readItems)

    fun toggleFavorite(url: String): Boolean {
        val db = writableDatabase
        val exists = db.rawQuery("SELECT 1 FROM favorites WHERE url=? LIMIT 1", arrayOf(url)).use { it.moveToFirst() }
        if (exists) {
            db.delete("favorites", "url=?", arrayOf(url))
            return false
        }
        val v = ContentValues().apply { put("url", url) }
        db.insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    fun getProgress(url: String): Pair<Long, Long> = readableDatabase.rawQuery(
        "SELECT position_ms,duration_ms FROM progress WHERE url=?",
        arrayOf(url)
    ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else 0L to 0L }

    fun saveProgress(url: String, positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return
        val v = ContentValues().apply {
            put("url", url)
            put("position_ms", positionMs)
            put("duration_ms", durationMs)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("progress", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun baseSelect() =
        """
        SELECT c.id,c.name,c.url,c.logo,c.group_name,c.content_type,c.series_key,c.season,c.episode,
               CASE WHEN f.url IS NULL THEN 0 ELSE 1 END,
               COALESCE(p.position_ms,0),COALESCE(p.duration_ms,0)
        FROM catalog c
        LEFT JOIN favorites f ON f.url=c.url
        LEFT JOIN progress p ON p.url=c.url
        """.trimIndent()

    private fun readItems(c: Cursor): List<CatalogItem> = buildList {
        while (c.moveToNext()) {
            add(
                CatalogItem(
                    id = c.getLong(0),
                    name = c.getString(1),
                    url = c.getString(2),
                    logo = if (c.isNull(3)) null else c.getString(3),
                    group = c.getString(4),
                    type = ContentType.valueOf(c.getString(5)),
                    seriesKey = if (c.isNull(6)) null else c.getString(6),
                    season = if (c.isNull(7)) null else c.getInt(7),
                    episode = if (c.isNull(8)) null else c.getInt(8),
                    favorite = c.getInt(9) == 1,
                    progressMs = c.getLong(10),
                    durationMs = c.getLong(11)
                )
            )
        }
    }

    private fun detectType(name: String, group: String, url: String): ContentType {
        val value = "$group $name $url".lowercase()
        if (listOf("/series/", "séries", "series", "temporada", "season", " s01e", " s02e").any(value::contains)) {
            return ContentType.SERIES
        }
        if (listOf("/movie/", "filmes", "filme", "movie", "cinema", "vod").any(value::contains)) {
            return ContentType.MOVIE
        }
        return ContentType.LIVE
    }

    private fun parseSeries(name: String): Triple<String, Int, Int> {
        val match = seasonEpisode.find(name) ?: xEpisode.find(name)
        if (match != null) {
            val key = match.groupValues[1].trim().trimEnd('-', '_', '.', ' ')
            return Triple(key.ifBlank { name }, match.groupValues[2].toInt(), match.groupValues[3].toInt())
        }
        return Triple(name, 1, 1)
    }
}
