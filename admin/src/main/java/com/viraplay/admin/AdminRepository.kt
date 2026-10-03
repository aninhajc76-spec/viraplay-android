package com.viraplay.admin

import com.viraplay.shared.AdminDevice
import com.viraplay.shared.AppConfig
import com.viraplay.shared.Http
import com.viraplay.shared.XtreamAccountClient
import org.json.JSONObject

class AdminRepository {
    companion object {
        const val DELETED_MARKER = "__VIRAPLAY_DELETED__"
    }

    private fun auth(token: String) = mapOf("Authorization" to "Bearer $token")

    fun list(token: String): List<AdminDevice> =
        Http.parseDevices(
            Http.getText(
                "${AppConfig.SERVER_BASE_URL}/api/admin/devices",
                auth(token),
                maxChars = 2_000_000
            )
        ).filter { it.label != DELETED_MARKER }

    fun detectExpiryIso(playlist: String): String? =
        runCatching {
            ClientMetaCodec.epochSecondsToIso(
                XtreamAccountClient.fetchFromPlaylist(playlist)?.expiresAtEpochSeconds
            )
        }.getOrNull()

    fun enrichMissingExpiries(token: String, devices: List<AdminDevice>): List<AdminDevice> =
        devices.map { device ->
            val playlist = device.playlistUrl
            val meta = ClientMetaCodec.decode(device.label)
            if (playlist.isNullOrBlank() || meta.expiresIso != null) {
                device
            } else {
                val expiry = detectExpiryIso(playlist)
                if (expiry == null) {
                    device
                } else {
                    runCatching {
                        update(
                            token = token,
                            deviceId = device.deviceId,
                            enabled = device.enabled,
                            playlist = playlist,
                            name = meta.name.ifBlank { "Cliente" },
                            identifier = meta.identifier,
                            expiresIso = expiry
                        )
                    }
                    device.copy(label = ClientMetaCodec.encode(meta.name.ifBlank { "Cliente" }, meta.identifier, expiry))
                }
            }
        }

    fun claim(
        token: String,
        code: String,
        name: String,
        identifier: String?,
        expiresIso: String?,
        playlist: String
    ) {
        val expiry = expiresIso ?: detectExpiryIso(playlist)
        val body = JSONObject()
            .put("pairing_code", code)
            .put("label", ClientMetaCodec.encode(name, identifier, expiry))
            .put("playlist_url", playlist)
        Http.postJson("${AppConfig.SERVER_BASE_URL}/api/admin/claim", body, auth(token))
    }

    fun update(
        token: String,
        deviceId: String,
        enabled: Boolean,
        playlist: String,
        name: String,
        identifier: String?,
        expiresIso: String?
    ) {
        val expiry = expiresIso ?: detectExpiryIso(playlist)
        val body = JSONObject()
            .put("device_id", deviceId)
            .put("enabled", enabled)
            .put("playlist_url", playlist.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("label", ClientMetaCodec.encode(name, identifier, expiry))
        Http.postJson("${AppConfig.SERVER_BASE_URL}/api/admin/update", body, auth(token))
    }

    fun delete(token: String, deviceId: String) {
        val body = JSONObject()
            .put("device_id", deviceId)
            .put("enabled", false)
            .put("playlist_url", JSONObject.NULL)
            .put("label", DELETED_MARKER)
        Http.postJson("${AppConfig.SERVER_BASE_URL}/api/admin/update", body, auth(token))
    }
}
