package com.viraplay.shared

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Http {
    fun getText(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 25_000
            requestMethod = "GET"
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 25_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream.bufferedReader().use { it.readText() }
    }

    fun parseDeviceConfig(json: String): DeviceConfig {
        val o = JSONObject(json)
        return DeviceConfig(
            enabled = o.optBoolean("enabled", false),
            playlistUrl = o.optString("playlist_url").takeIf { it.isNotBlank() && it != "null" },
            playlistName = o.optString("playlist_name").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun parseDevices(json: String): List<AdminDevice> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AdminDevice(
                deviceId = o.optString("device_id"),
                pairingCode = o.optString("pairing_code"),
                label = o.optString("label").takeIf { it.isNotBlank() && it != "null" },
                platform = o.optString("platform").takeIf { it.isNotBlank() && it != "null" },
                enabled = o.optBoolean("enabled", true),
                playlistUrl = o.optString("playlist_url").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
