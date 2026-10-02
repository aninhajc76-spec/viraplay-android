package com.viraplay.shared

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object Http {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android) ViraPlay/1.1"

    private fun open(
        url: String,
        method: String,
        headers: Map<String, String>
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection)
            .apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                requestMethod = method
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
                headers.forEach { (k, v) ->
                    setRequestProperty(k, v)
                }
            }

    fun <T> withReader(
        url: String,
        headers: Map<String, String> = emptyMap(),
        block: (BufferedReader) -> T
    ): T {
        val conn = open(url, "GET", headers)

        return try {
            val code = conn.responseCode

            if (code !in 200..299) {
                val detail =
                    conn.errorStream
                        ?.bufferedReader()
                        ?.use { reader ->
                            buildString {
                                val buffer = CharArray(1024)
                                var remaining = 4096

                                while (remaining > 0) {
                                    val read =
                                        reader.read(
                                            buffer,
                                            0,
                                            minOf(
                                                buffer.size,
                                                remaining
                                            )
                                        )

                                    if (read <= 0) break

                                    append(
                                        buffer,
                                        0,
                                        read
                                    )

                                    remaining -= read
                                }
                            }
                        }
                        .orEmpty()

                throw IllegalStateException(
                    "HTTP $code" +
                        detail
                            .takeIf { it.isNotBlank() }
                            ?.let {
                                ": ${it.take(160)}"
                            }
                            .orEmpty()
                )
            }

            BufferedReader(
                InputStreamReader(
                    conn.inputStream,
                    Charsets.UTF_8
                ),
                64 * 1024
            ).use(block)

        } finally {
            conn.disconnect()
        }
    }

    fun getText(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String =
        withReader(url, headers) {
            it.readText()
        }

    fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap()
    ): String {
        val conn =
            open(url, "POST", headers)
                .apply {
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json"
                    )
                }

        return try {
            conn.outputStream.use {
                it.write(
                    body.toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }

            val code = conn.responseCode

            val stream =
                if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

            val text =
                stream
                    ?.bufferedReader()
                    ?.use { reader ->
                        reader.readText()
                    }
                    .orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException(
                    "HTTP $code" +
                        text
                            .takeIf { it.isNotBlank() }
                            ?.let {
                                ": ${it.take(160)}"
                            }
                            .orEmpty()
                )
            }

            text
        } finally {
            conn.disconnect()
        }
    }

    fun parseDeviceConfig(
        json: String
    ): DeviceConfig {
        val o = JSONObject(json)

        return DeviceConfig(
            enabled =
                o.optBoolean(
                    "enabled",
                    false
                ),

            playlistUrl =
                o.optString(
                    "playlist_url"
                ).takeIf {
                    it.isNotBlank() &&
                        it != "null"
                },

            playlistName =
                o.optString(
                    "playlist_name"
                ).takeIf {
                    it.isNotBlank() &&
                        it != "null"
                }
        )
    }

    fun parseDevices(
        json: String
    ): List<AdminDevice> {
        val arr = JSONArray(json)

        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)

            AdminDevice(
                deviceId =
                    o.optString(
                        "device_id"
                    ),

                pairingCode =
                    o.optString(
                        "pairing_code"
                    ),

                label =
                    o.optString(
                        "label"
                    ).takeIf {
                        it.isNotBlank() &&
                            it != "null"
                    },

                platform =
                    o.optString(
                        "platform"
                    ).takeIf {
                        it.isNotBlank() &&
                            it != "null"
                    },

                enabled =
                    o.optBoolean(
                        "enabled",
                        true
                    ),

                playlistUrl =
                    o.optString(
                        "playlist_url"
                    ).takeIf {
                        it.isNotBlank() &&
                            it != "null"
                    }
            )
        }
    }
}
