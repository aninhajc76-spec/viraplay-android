package com.viraplay.shared

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

object Http {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) ViraPlay/3.2"

    private fun open(
        url: String,
        method: String,
        headers: Map<String, String>
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            requestMethod = method
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    fun <T> withInputStream(
        url: String,
        headers: Map<String, String> = emptyMap(),
        block: (InputStream) -> T
    ): T {
        val conn = open(url, "GET", headers)
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText().take(300) }
                    .orEmpty()
                throw IllegalStateException("HTTP $code${detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
            }
            conn.inputStream.use(block)
        } finally {
            conn.disconnect()
        }
    }

    fun <T> withReader(
        url: String,
        headers: Map<String, String> = emptyMap(),
        block: (BufferedReader) -> T
    ): T = withInputStream(url, headers) { input ->
        BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024).use(block)
    }

    fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxChars: Int = 4_000_000
    ): String = withReader(url, headers) { reader ->
        val out = StringBuilder(minOf(maxChars, 128_000))
        val buffer = CharArray(16 * 1024)
        while (true) {
            val read = reader.read(buffer)
            if (read <= 0) break
            if (out.length + read > maxChars) {
                throw IllegalStateException("Resposta maior que o limite permitido")
            }
            out.append(buffer, 0, read)
        }
        out.toString()
    }

    fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap()
    ): String {
        val conn = open(url, "POST", headers).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code${text.takeIf { it.isNotBlank() }?.let { ": ${it.take(200)}" }.orEmpty()}")
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
