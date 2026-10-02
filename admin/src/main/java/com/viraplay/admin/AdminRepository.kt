package com.viraplay.admin

import com.viraplay.shared.AdminDevice
import com.viraplay.shared.AppConfig
import com.viraplay.shared.Http
import org.json.JSONObject

class AdminRepository {

    companion object {
        const val DELETED_MARKER =
            "__VIRAPLAY_DELETED__"
    }

    private fun auth(
        token: String
    ) =
        mapOf(
            "Authorization" to
                "Bearer $token"
        )

    fun list(
        token: String
    ): List<AdminDevice> =
        Http.parseDevices(
            Http.getText(
                "${AppConfig.SERVER_BASE_URL}/api/admin/devices",
                auth(token)
            )
        ).filter {
            it.label !=
                DELETED_MARKER
        }

    fun claim(
        token: String,
        code: String,
        label: String,
        playlist: String
    ) {
        val body =
            JSONObject()
                .put(
                    "pairing_code",
                    code
                )
                .put(
                    "label",
                    label
                )
                .put(
                    "playlist_url",
                    playlist
                )

        Http.postJson(
            "${AppConfig.SERVER_BASE_URL}/api/admin/claim",
            body,
            auth(token)
        )
    }

    fun update(
        token: String,
        deviceId: String,
        enabled: Boolean,
        playlist: String,
        label: String
    ) {
        val body =
            JSONObject()
                .put(
                    "device_id",
                    deviceId
                )
                .put(
                    "enabled",
                    enabled
                )
                .put(
                    "playlist_url",
                    playlist.takeIf {
                        it.isNotBlank()
                    } ?: JSONObject.NULL
                )
                .put(
                    "label",
                    label
                )

        Http.postJson(
            "${AppConfig.SERVER_BASE_URL}/api/admin/update",
            body,
            auth(token)
        )
    }

    fun delete(
        token: String,
        deviceId: String
    ) {
        val body =
            JSONObject()
                .put(
                    "device_id",
                    deviceId
                )
                .put(
                    "enabled",
                    false
                )
                .put(
                    "playlist_url",
                    JSONObject.NULL
                )
                .put(
                    "label",
                    DELETED_MARKER
                )

        Http.postJson(
            "${AppConfig.SERVER_BASE_URL}/api/admin/update",
            body,
            auth(token)
        )
    }
}
