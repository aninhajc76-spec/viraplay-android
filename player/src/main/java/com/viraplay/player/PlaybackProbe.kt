package com.viraplay.player

import java.net.HttpURLConnection
import java.net.URL

data class PlaybackProbeResult(
    val statusCode: Int?,
    val detail: String
)

object PlaybackProbe {
    private const val UA = "Mozilla/5.0 (Linux; Android) VPlayo/3.2"

    fun check(url: String): PlaybackProbeResult {
        if (url.isBlank()) return PlaybackProbeResult(null, "URL vazia")
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7_000
                readTimeout = 7_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Range", "bytes=0-1")
            }
        }.getOrElse {
            return PlaybackProbeResult(null, it.javaClass.simpleName)
        }

        return try {
            val code = connection.responseCode
            val type = connection.contentType.orEmpty().substringBefore(';')
            PlaybackProbeResult(
                statusCode = code,
                detail = buildString {
                    append("HTTP ")
                    append(code)
                    if (type.isNotBlank()) {
                        append(" • ")
                        append(type.take(60))
                    }
                }
            )
        } catch (e: Throwable) {
            PlaybackProbeResult(null, e.javaClass.simpleName)
        } finally {
            runCatching { connection.disconnect() }
        }
    }
}
