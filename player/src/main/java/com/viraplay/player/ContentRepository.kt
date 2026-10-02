package com.viraplay.player

import android.content.Context
import com.viraplay.shared.AppConfig
import com.viraplay.shared.DeviceConfig
import com.viraplay.shared.Http
import com.viraplay.shared.XtreamAccountClient
import com.viraplay.shared.XtreamAccountInfo
import java.net.URLEncoder
import org.json.JSONObject

class ContentRepository(
    private val context: Context,
    private val db: CatalogDb
) {
    private val prefs = context.getSharedPreferences("viraplay_network", Context.MODE_PRIVATE)
    private val xtream = XtreamSync()
    private val m3u = M3uSync()

    fun registerIfNeeded(identity: DeviceIdentity, platform: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_register", 0L)
        if (!force && last > 0L && now - last < 24L * 60L * 60L * 1000L) return

        val body = JSONObject()
            .put("device_id", identity.deviceId)
            .put("device_secret", identity.deviceSecret)
            .put("pairing_code", identity.pairingCode)
            .put("platform", platform)

        Http.postJson("${AppConfig.SERVER_BASE_URL}/api/register", body)
        prefs.edit().putLong("last_register", now).apply()
    }

    fun config(identity: DeviceIdentity): DeviceConfig {
        val id = URLEncoder.encode(identity.deviceId, "UTF-8")
        val secret = URLEncoder.encode(identity.deviceSecret, "UTF-8")
        return Http.parseDeviceConfig(
            Http.getText(
                "${AppConfig.SERVER_BASE_URL}/api/config?device_id=$id&secret=$secret",
                maxChars = 250_000
            )
        )
    }


    fun accountInfo(playlistUrl: String): XtreamAccountInfo? =
        runCatching { XtreamAccountClient.fetchFromPlaylist(playlistUrl) }.getOrNull()

    fun syncCatalog(
        playlistUrl: String,
        onProgress: (String) -> Unit = {}
    ): SyncResult {
        val credentials = SourceResolver.xtreamFromPlaylist(playlistUrl)
        if (credentials != null) {
            val xtreamResult = runCatching {
                xtream.sync(playlistUrl, db) { section, count ->
                    onProgress("$section: $count")
                }
            }
            if (xtreamResult.isSuccess) return xtreamResult.getOrThrow()
        }

        return m3u.sync(playlistUrl, db) { count ->
            onProgress("Organizando $count itens")
        }
    }

    fun episodes(parent: CatalogItem, force: Boolean = false): List<CatalogItem> {
        val sourceUrl = db.getMeta("playlist_url").orEmpty()
        return if (!parent.seriesId.isNullOrBlank() && SourceResolver.xtreamFromPlaylist(sourceUrl) != null) {
            xtream.loadEpisodes(sourceUrl, parent, db, force)
        } else {
            db.episodes(parent)
        }
    }

    fun epg(item: CatalogItem): List<EpgProgram> {
        val sourceUrl = db.getMeta("playlist_url").orEmpty()
        val streamId = item.sourceId ?: return emptyList()
        return xtream.shortEpg(sourceUrl, streamId)
    }
}
