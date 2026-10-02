package com.viraplay.shared

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Http {

    private const val USER_AGENT = "ViraPlay/0.4 Android"

    fun getText(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 90_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

        return try {
            val code = conn.responseCode
            val stream =
                if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream ?: throw IllegalStateException("HTTP $code")
                }

            val text = stream.bufferedReader().use { it.readText() }

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${text.take(180)}")
            }

            text
        } finally {
            conn.disconnect()
        }
    }

    fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap()
    ): String {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 40_000
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

        return try {
            conn.outputStream.use {
                it.write(body.toString().toByteArray())
            }

            val code = conn.responseCode
            val stream =
                if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream ?: throw IllegalStateException("HTTP $code")
                }

            val text = stream.bufferedReader().use { it.readText() }

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${text.take(180)}")
            }

            text
        } finally {
            conn.disconnect()
        }
    }

    fun parseDeviceConfig(json: String): DeviceConfig {
        val o = JSONObject(json)

        return DeviceConfig(
            enabled = o.optBoolean("enabled", false),
            playlistUrl =
                o.optString("playlist_url")
                    .takeIf { it.isNotBlank() && it != "null" },
            playlistName =
                o.optString("playlist_name")
                    .takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun parseDevices(json: String): List<AdminDevice> {
        val arr = JSONArray(json)

        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)

            AdminDevice(
                deviceId = o.optString("device_id"),
                pairingCode = o.optString("pairing_code"),
                label =
                    o.optString("label")
                        .takeIf { it.isNotBlank() && it != "null" },
                platform =
                    o.optString("platform")
                        .takeIf { it.isNotBlank() && it != "null" },
                enabled = o.optBoolean("enabled", true),
                playlistUrl =
                    o.optString("playlist_url")
                        .takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
