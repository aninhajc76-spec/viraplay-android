package com.viraplay.player

import com.viraplay.shared.AppConfig
import com.viraplay.shared.DeviceConfig
import com.viraplay.shared.Http
import java.net.URLEncoder
import org.json.JSONObject

/** Compatibilidade com a base anterior. A ViraPlay 3.0 usa ContentRepository. */
class PlayerRepository {
    fun register(deviceId: String, secret: String, code: String, platform: String) {
        val body = JSONObject()
            .put("device_id", deviceId)
            .put("device_secret", secret)
            .put("pairing_code", code)
            .put("platform", platform)
        Http.postJson("${AppConfig.SERVER_BASE_URL}/api/register", body)
    }

    fun config(deviceId: String, secret: String): DeviceConfig {
        val id = URLEncoder.encode(deviceId, "UTF-8")
        val sec = URLEncoder.encode(secret, "UTF-8")
        return Http.parseDeviceConfig(
            Http.getText("${AppConfig.SERVER_BASE_URL}/api/config?device_id=$id&secret=$sec", maxChars = 250_000)
        )
    }

    fun syncCatalog(playlistUrl: String, db: CatalogDb): Int =
        M3uSync().sync(playlistUrl, db).count
}
