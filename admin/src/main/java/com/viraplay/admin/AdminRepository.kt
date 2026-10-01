package com.viraplay.admin

import com.viraplay.shared.AdminDevice
import com.viraplay.shared.AppConfig
import com.viraplay.shared.Http
import org.json.JSONObject

class AdminRepository {
    private fun auth(token: String) = mapOf("Authorization" to "Bearer $token")

    fun list(token: String): List<AdminDevice> =
        Http.parseDevices(Http.getText("${AppConfig.SERVER_BASE_URL}/api/admin/devices", auth(token)))

    fun claim(token: String, code: String, label: String, playlist: String) {
        val body = JSONObject().put("pairing_code", code).put("label", label).put("playlist_url", playlist)
        Http.postJson("${AppConfig.SERVER_BASE_URL}/api/admin/claim", body, auth(token))
    }

    fun update(token: String, deviceId: String, enabled: Boolean, playlist: String) {
        val body = JSONObject().put("device_id", deviceId).put("enabled", enabled).put("playlist_url", playlist)
        Http.postJson("${AppConfig.SERVER_BASE_URL}/api/admin/update", body, auth(token))
    }
}
