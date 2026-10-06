package com.viraplay.admin

import com.viraplay.shared.AdminDevice
import com.viraplay.shared.AppConfig
import com.viraplay.shared.Http
import com.viraplay.shared.XtreamAccountClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class AdminRepository {
    companion object {
        const val DELETED_MARKER = "__VIRAPLAY_DELETED__"
    }

    private fun auth(token: String) = mapOf("Authorization" to "Bearer $token")

    fun profile(token: String): AdminProfile {
        return try {
            val o = JSONObject(Http.getText("${AppConfig.SERVER_BASE_URL}/api/admin/profile", auth(token)))
            AdminProfile(
                role = o.optString("role", "PARTNER"),
                name = o.optString("name", "Parceiro"),
                credits = o.optInt("credits", 0),
                annualLicenseCredits = o.optInt("annual_license_credits", 15)
            )
        } catch (e: Throwable) {
            val message = e.message.orEmpty()
            if (!message.contains("HTTP 404")) throw e

            Http.getText(
                "${AppConfig.SERVER_BASE_URL}/api/admin/devices",
                auth(token),
                maxChars = 8_000
            )
            AdminProfile(
                role = "MASTER",
                name = "VPlayo MASTER",
                credits = 0,
                annualLicenseCredits = 0
            )
        }
    }

    fun list(token: String): List<AdminDevice> =
        Http.parseDevices(
            Http.getText(
                "${AppConfig.SERVER_BASE_URL}/api/admin/devices",
                auth(token),
                maxChars = 2_000_000
            )
        ).filter { it.label != DELETED_MARKER }

    fun lookup(token: String, code: String): AdminDevice {
        val encoded = URLEncoder.encode(code.trim().uppercase(), "UTF-8")
        val o = JSONObject(
            Http.getText(
                "${AppConfig.SERVER_BASE_URL}/api/admin/lookup?pairing_code=$encoded",
                auth(token)
            )
        )
        return AdminDevice(
            deviceId = o.optString("device_id"),
            pairingCode = o.optString("pairing_code"),
            label = o.optString("label").takeIf { it.isNotBlank() && it != "null" },
            platform = o.optString("platform").takeIf { it.isNotBlank() && it != "null" },
            enabled = o.optBoolean("enabled", true),
            playlistUrl = o.optString("playlist_url").takeIf { it.isNotBlank() && it != "null" }
        )
    }

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
                if (expiry == null) device else {
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
        playlist: String,
        licenseMonths: Int = 12
    ) {
        val expiry = expiresIso ?: detectExpiryIso(playlist)
        val body = JSONObject()
            .put("pairing_code", code.trim().uppercase())
            .put("label", ClientMetaCodec.encode(name, identifier, expiry))
            .put("playlist_url", playlist)
            .put("license_months", licenseMonths)
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

    fun partners(token: String): List<PartnerInfo> {
        val arr = JSONArray(Http.getText("${AppConfig.SERVER_BASE_URL}/api/admin/partners", auth(token), maxChars = 1_000_000))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PartnerInfo(
                id = o.optString("id"),
                name = o.optString("name"),
                loginCode = o.optString("login_code"),
                accessToken = o.optString("access_token"),
                status = o.optString("status", "ACTIVE"),
                credits = o.optInt("credits", 0),
                clients = o.optInt("clients", 0)
            )
        }
    }

    fun createPartner(token: String, name: String): PartnerInfo {
        val o = JSONObject(
            Http.postJson(
                "${AppConfig.SERVER_BASE_URL}/api/admin/partners/create",
                JSONObject().put("name", name.trim()),
                auth(token)
            )
        ).getJSONObject("partner")
        return PartnerInfo(
            id = o.optString("id"),
            name = o.optString("name"),
            loginCode = o.optString("login_code"),
            accessToken = o.optString("access_token"),
            status = o.optString("status", "ACTIVE"),
            credits = o.optInt("credits", 0),
            clients = o.optInt("clients", 0)
        )
    }

    fun updatePartner(token: String, partnerId: String, name: String, status: String) {
        Http.postJson(
            "${AppConfig.SERVER_BASE_URL}/api/admin/partners/update",
            JSONObject()
                .put("partner_id", partnerId)
                .put("name", name.trim())
                .put("status", status),
            auth(token)
        )
    }

    fun deletePartner(token: String, partnerId: String) {
        Http.postJson(
            "${AppConfig.SERVER_BASE_URL}/api/admin/partners/delete",
            JSONObject().put("partner_id", partnerId),
            auth(token)
        )
    }

    fun grantCredits(token: String, partnerId: String, amount: Int, note: String? = null) {
        Http.postJson(
            "${AppConfig.SERVER_BASE_URL}/api/admin/partners/credits",
            JSONObject()
                .put("partner_id", partnerId)
                .put("amount", amount)
                .put("note", note ?: "Crédito manual MASTER"),
            auth(token)
        )
    }

    fun creditHistory(token: String): List<CreditEntry> {
        val arr = JSONArray(Http.getText("${AppConfig.SERVER_BASE_URL}/api/admin/credits/history", auth(token), maxChars = 600_000))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CreditEntry(
                amount = o.optInt("amount", 0),
                kind = o.optString("kind"),
                note = o.optString("note").takeIf { it.isNotBlank() && it != "null" },
                createdAt = o.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
