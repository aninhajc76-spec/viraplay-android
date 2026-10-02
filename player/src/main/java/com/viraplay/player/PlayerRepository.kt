package com.viraplay.player

import com.viraplay.shared.AppConfig
import com.viraplay.shared.DeviceConfig
import com.viraplay.shared.Http
import com.viraplay.shared.M3uParser
import com.viraplay.shared.ParsedPlaylist
import java.net.URLEncoder
import org.json.JSONObject

class PlayerRepository {

    fun register(
        deviceId: String,
        secret: String,
        code: String,
        platform: String
    ) {
        val body =
            JSONObject()
                .put("device_id", deviceId)
                .put("device_secret", secret)
                .put("pairing_code", code)
                .put("platform", platform)

        Http.postJson(
            "${AppConfig.SERVER_BASE_URL}/api/register",
            body
        )
    }

    fun config(
        deviceId: String,
        secret: String
    ): DeviceConfig {
        val device = URLEncoder.encode(deviceId, "UTF-8")
        val deviceSecret = URLEncoder.encode(secret, "UTF-8")

        val url =
            "${AppConfig.SERVER_BASE_URL}/api/config" +
                "?device_id=$device" +
                "&secret=$deviceSecret"

        return Http.parseDeviceConfig(Http.getText(url))
    }

    fun playlist(url: String): ParsedPlaylist {
        val text = Http.getText(url)

        if (!text.trimStart().startsWith("#EXTM3U", ignoreCase = true)) {
            throw IllegalStateException("A URL não retornou uma lista M3U válida")
        }

        return M3uParser.parse(text)
    }
}
