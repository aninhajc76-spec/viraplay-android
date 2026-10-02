package com.viraplay.player

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.viraplay.shared.ContentType

class CatalogDb(context: Context) : SQLiteOpenHelper(
    context,
    "viraplay_catalog_v3.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE items (
                row_id INTEGER PRIMARY KEY AUTOINCREMENT,
                generation TEXT NOT NULL,
                item_key TEXT NOT NULL,
                source_id TEXT,
                name TEXT NOT NULL,
                url TEXT,
                image TEXT,
                backdrop TEXT,
                category_id TEXT,
                category_name TEXT NOT NULL,
                content_type TEXT NOT NULL,
                series_id TEXT,
                series_key TEXT,
                season INTEGER,
                episode INTEGER,
                plot TEXT,
                rating TEXT,
                container_extension TEXT,
                sort_index INTEGER NOT NULL DEFAULT 0,
                UNIQUE(generation, item_key)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_items_generation_type ON items(generation, content_type)")
        db.execSQL("CREATE INDEX idx_items_generation_category ON items(generation, content_type, category_id)")
        db.execSQL("CREATE INDEX idx_items_series ON items(generation, series_id, series_key, season, episode)")
        db.execSQL(
            """
            CREATE TABLE categories (
                generation TEXT NOT NULL,
                content_type TEXT NOT NULL,
                category_id TEXT NOT NULL,
                name TEXT NOT NULL,
                sort_index INTEGER NOT NULL DEFAULT 0,
                UNIQUE(generation, content_type, category_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE favorites (item_key TEXT PRIMARY KEY)")
        db.execSQL(
            """
            CREATE TABLE progress (
                item_key TEXT PRIMARY KEY,
                position_ms INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun activeGeneration(): String? = getMeta("active_generation")

    fun hasCatalog(): Boolean {
        val generation = activeGeneration() ?: return false
        return readableDatabase.rawQuery(
            "SELECT 1 FROM items WHERE generation=? AND content_type!='EPISODE' LIMIT 1",
            arrayOf(generation)
        ).use { it.moveToFirst() }
    }

    fun countAll(): Int {
        val generation = activeGeneration() ?: return 0
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM items WHERE generation=? AND content_type!='EPISODE'",
            arrayOf(generation)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun count(type: ContentType): Int {
        val generation = activeGeneration() ?: return 0
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM items WHERE generation=? AND content_type=?",
            arrayOf(generation, type.name)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getMeta(key: String): String? = readableDatabase.rawQuery(
        "SELECT value FROM meta WHERE key=?",
        arrayOf(key)
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun getMetaLong(key: String): Long = getMeta(key)?.toLongOrNull() ?: 0L

    fun putMeta(key: String, value: String?) {
        val db = writableDatabase
        if (value == null) {
            db.delete("meta", "key=?", arrayOf(key))
            return
        }
        val v = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("meta", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun newGeneration(): String = System.currentTimeMillis().toString()

    fun <T> inTransaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val result = block()
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    fun putCategory(
        generation: String,
        type: ContentType,
        categoryId: String,
        name: String,
        sortIndex: Int
    ) {
        val v = ContentValues().apply {
            put("generation", generation)
            put("content_type", type.name)
            put("category_id", categoryId)
            put("name", name.ifBlank { "Outros" })
            put("sort_index", sortIndex)
        }
        writableDatabase.insertWithOnConflict(
            "categories",
            null,
            v,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun putItem(generation: String, seed: CatalogSeed) {
        val v = ContentValues().apply {
            put("generation", generation)
            put("item_key", seed.itemKey)
            putNullable("source_id", seed.sourceId)
            put("name", seed.name.ifBlank { "Sem título" })
            putNullable("url", seed.url)
            putNullable("image", seed.image)
            putNullable("backdrop", seed.backdrop)
            putNullable("category_id", seed.categoryId)
            put("category_name", seed.categoryName.ifBlank { "Outros" })
            put("content_type", seed.type.name)
            putNullable("series_id", seed.seriesId)
            putNullable("series_key", seed.seriesKey)
            putNullableInt("season", seed.season)
            putNullableInt("episode", seed.episode)
            putNullable("plot", seed.plot)
            putNullable("rating", seed.rating)
            putNullable("container_extension", seed.containerExtension)
            put("sort_index", seed.sortIndex)
        }
        writableDatabase.insertWithOnConflict(
            "items",
            null,
            v,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun completeGeneration(
        generation: String,
        sourceUrl: String,
        sourceKind: String,
        sourceName: String
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            putMetaInternal(db, "active_generation", generation)
            putMetaInternal(db, "playlist_url", sourceUrl)
            putMetaInternal(db, "source_kind", sourceKind)
            putMetaInternal(db, "source_name", sourceName)
            putMetaInternal(db, "last_sync", System.currentTimeMillis().toString())
            db.delete("items", "generation<>?", arrayOf(generation))
            db.delete("categories", "generation<>?", arrayOf(generation))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun cancelGeneration(generation: String) {
        writableDatabase.delete("items", "generation=?", arrayOf(generation))
        writableDatabase.delete("categories", "generation=?", arrayOf(generation))
    }

    fun categories(type: ContentType): List<CategoryEntry> {
        val generation = activeGeneration() ?: return emptyList()
        return readableDatabase.rawQuery(
            """
            SELECT c.category_id, c.name, COUNT(i.row_id) AS qty
            FROM categories c
            LEFT JOIN items i
              ON i.generation=c.generation
             AND i.content_type=c.content_type
             AND i.category_id=c.category_id
            WHERE c.generation=? AND c.content_type=?
            GROUP BY c.category_id, c.name, c.sort_index
            HAVING qty > 0
            ORDER BY c.sort_index, c.name COLLATE NOCASE
            """.trimIndent(),
            arrayOf(generation, type.name)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(CategoryEntry(cursor.getString(0), cursor.getString(1), cursor.getInt(2)))
                }
            }
        }
    }

    fun query(
        type: ContentType,
        categoryId: String? = null,
        search: String = "",
        limit: Int = 300,
        offset: Int = 0
    ): List<CatalogItem> {
        val generation = activeGeneration() ?: return emptyList()
        val args = mutableListOf(generation, type.name)
        val where = buildString {
            append("i.generation=? AND i.content_type=?")
            if (!categoryId.isNullOrBlank() && categoryId != "ALL") {
                append(" AND i.category_id=?")
                args += categoryId
            }
            if (search.isNotBlank()) {
                append(" AND (i.name LIKE ? OR i.category_name LIKE ?)")
                val q = "%${search.trim()}%"
                args += q
                args += q
            }
        }
        args += limit.toString()
        args += offset.toString()
        return readableDatabase.rawQuery(
            baseSelect() + " WHERE $where ORDER BY i.sort_index, i.name COLLATE NOCASE LIMIT ? OFFSET ?",
            args.toTypedArray()
        ).use(::readItems)
    }


    fun liveNeighbor(item: CatalogItem, next: Boolean): CatalogItem? {
        val generation = activeGeneration() ?: return null
        if (item.type != ContentType.LIVE) return null

        val currentSort = readableDatabase.rawQuery(
            "SELECT sort_index FROM items WHERE generation=? AND item_key=? LIMIT 1",
            arrayOf(generation, item.itemKey)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else return null
        }

        val direction = if (next) ">" else "<"
        val order = if (next) "ASC" else "DESC"
        val args = arrayOf(
            generation,
            item.categoryName,
            item.itemKey,
            currentSort.toString()
        )

        fun queryNeighbor(whereDirection: Boolean): CatalogItem? {
            val sql = if (whereDirection) {
                baseSelect() +
                    " WHERE i.generation=? AND i.content_type='LIVE'" +
                    " AND i.category_name=? AND i.item_key<>? AND i.sort_index $direction ?" +
                    " ORDER BY i.sort_index $order, i.name COLLATE NOCASE $order LIMIT 1"
            } else {
                baseSelect() +
                    " WHERE i.generation=? AND i.content_type='LIVE'" +
                    " AND i.category_name=? AND i.item_key<>?" +
                    " ORDER BY i.sort_index $order, i.name COLLATE NOCASE $order LIMIT 1"
            }
            val useArgs = if (whereDirection) args else args.copyOfRange(0, 3)
            return readableDatabase.rawQuery(sql, useArgs).use(::readItems).firstOrNull()
        }

        return queryNeighbor(true) ?: queryNeighbor(false)
    }

    fun favorites(
        search: String = "",
        limit: Int = 500
    ): List<CatalogItem> {
        val generation = activeGeneration() ?: return emptyList()
        val args = mutableListOf(generation)
        val extra = if (search.isBlank()) "" else {
            val q = "%${search.trim()}%"
            args += q
            args += q
            " AND (i.name LIKE ? OR i.category_name LIKE ?)"
        }
        args += limit.toString()
        return readableDatabase.rawQuery(
            baseSelect() +
                " WHERE i.generation=? AND f.item_key IS NOT NULL AND i.content_type!='EPISODE' $extra" +
                " ORDER BY i.name COLLATE NOCASE LIMIT ?",
            args.toTypedArray()
        ).use(::readItems)
    }

    fun continueWatching(limit: Int = 30): List<CatalogItem> {
        val generation = activeGeneration() ?: return emptyList()
        return readableDatabase.rawQuery(
            baseSelect() +
                " WHERE i.generation=? AND i.content_type IN ('MOVIE','EPISODE')" +
                " AND p.position_ms>30000" +
                " AND (p.duration_ms<=0 OR CAST(p.position_ms AS REAL)/CAST(p.duration_ms AS REAL)<0.95)" +
                " ORDER BY p.updated_at DESC LIMIT ?",
            arrayOf(generation, limit.toString())
        ).use(::readItems)
    }

    fun episodes(parent: CatalogItem): List<CatalogItem> {
        val generation = activeGeneration() ?: return emptyList()
        return if (!parent.seriesId.isNullOrBlank()) {
            readableDatabase.rawQuery(
                baseSelect() +
                    " WHERE i.generation=? AND i.content_type='EPISODE' AND i.series_id=?" +
                    " ORDER BY COALESCE(i.season,9999), COALESCE(i.episode,9999), i.sort_index",
                arrayOf(generation, parent.seriesId)
            ).use(::readItems)
        } else {
            readableDatabase.rawQuery(
                baseSelect() +
                    " WHERE i.generation=? AND i.content_type='EPISODE' AND i.series_key=?" +
                    " ORDER BY COALESCE(i.season,9999), COALESCE(i.episode,9999), i.sort_index",
                arrayOf(generation, parent.seriesKey ?: parent.name)
            ).use(::readItems)
        }
    }

    fun replaceEpisodes(parent: CatalogItem, episodes: List<CatalogSeed>) {
        val generation = activeGeneration() ?: return
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (!parent.seriesId.isNullOrBlank()) {
                db.delete(
                    "items",
                    "generation=? AND content_type='EPISODE' AND series_id=?",
                    arrayOf(generation, parent.seriesId)
                )
            } else {
                db.delete(
                    "items",
                    "generation=? AND content_type='EPISODE' AND series_key=?",
                    arrayOf(generation, parent.seriesKey ?: parent.name)
                )
            }
            episodes.forEach { putItem(generation, it) }
            putMetaInternal(db, "series_loaded_${parent.itemKey}", System.currentTimeMillis().toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun seriesLoadedAt(parent: CatalogItem): Long =
        getMetaLong("series_loaded_${parent.itemKey}")

    fun toggleFavorite(itemKey: String): Boolean {
        val db = writableDatabase
        val exists = db.rawQuery(
            "SELECT 1 FROM favorites WHERE item_key=? LIMIT 1",
            arrayOf(itemKey)
        ).use { it.moveToFirst() }
        if (exists) {
            db.delete("favorites", "item_key=?", arrayOf(itemKey))
            return false
        }
        val v = ContentValues().apply { put("item_key", itemKey) }
        db.insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    fun progress(itemKey: String): Pair<Long, Long> = readableDatabase.rawQuery(
        "SELECT position_ms,duration_ms FROM progress WHERE item_key=?",
        arrayOf(itemKey)
    ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else 0L to 0L }

    fun saveProgress(itemKey: String, positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return
        val v = ContentValues().apply {
            put("item_key", itemKey)
            put("position_ms", positionMs)
            put("duration_ms", durationMs)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "progress",
            null,
            v,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun clearProgress(itemKey: String) {
        writableDatabase.delete("progress", "item_key=?", arrayOf(itemKey))
    }

    private fun baseSelect(): String =
        """
        SELECT
            i.item_key,i.source_id,i.name,i.url,i.image,i.backdrop,i.category_id,i.category_name,
            i.content_type,i.series_id,i.series_key,i.season,i.episode,i.plot,i.rating,i.container_extension,
            CASE WHEN f.item_key IS NULL THEN 0 ELSE 1 END AS favorite,
            COALESCE(p.position_ms,0),COALESCE(p.duration_ms,0)
        FROM items i
        LEFT JOIN favorites f ON f.item_key=i.item_key
        LEFT JOIN progress p ON p.item_key=i.item_key
        """.trimIndent()

    private fun readItems(cursor: Cursor): List<CatalogItem> = buildList {
        while (cursor.moveToNext()) {
            add(
                CatalogItem(
                    itemKey = cursor.getString(0),
                    sourceId = cursor.stringOrNull(1),
                    name = cursor.getString(2),
                    url = cursor.stringOrNull(3),
                    image = cursor.stringOrNull(4),
                    backdrop = cursor.stringOrNull(5),
                    categoryId = cursor.stringOrNull(6),
                    categoryName = cursor.getString(7),
                    type = ContentType.valueOf(cursor.getString(8)),
                    seriesId = cursor.stringOrNull(9),
                    seriesKey = cursor.stringOrNull(10),
                    season = cursor.intOrNull(11),
                    episode = cursor.intOrNull(12),
                    plot = cursor.stringOrNull(13),
                    rating = cursor.stringOrNull(14),
                    containerExtension = cursor.stringOrNull(15),
                    favorite = cursor.getInt(16) == 1,
                    progressMs = cursor.getLong(17),
                    durationMs = cursor.getLong(18)
                )
            )
        }
    }

    private fun putMetaInternal(db: SQLiteDatabase, key: String, value: String) {
        val v = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("meta", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullableInt(key: String, value: Int?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun Cursor.stringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.intOrNull(index: Int): Int? =
        if (isNull(index)) null else getInt(index)
}
