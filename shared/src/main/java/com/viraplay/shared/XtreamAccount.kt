package com.viraplay.shared

import java.net.URLDecoder
import java.net.URLEncoder
import org.json.JSONObject

data class XtreamAccountInfo(
    val status: String?,
    val expiresAtEpochSeconds: Long?,
    val activeConnections: Int?,
    val maxConnections: Int?
)

object XtreamAccountClient {
    fun fetchFromPlaylist(playlistUrl: String): XtreamAccountInfo? {
        val credentials = parsePlaylist(playlistUrl) ?: return null
        val url = buildString {
            append(credentials.baseUrl)
            append("/player_api.php?username=")
            append(URLEncoder.encode(credentials.username, "UTF-8"))
            append("&password=")
            append(URLEncoder.encode(credentials.password, "UTF-8"))
        }

        val root = JSONObject(Http.getText(url, maxChars = 350_000))
        val user = root.optJSONObject("user_info") ?: return null
        val expiry = user.optString("exp_date")
            .takeIf { it.isNotBlank() && it != "null" && it != "0" }
            ?.toLongOrNull()

        return XtreamAccountInfo(
            status = user.optString("status").takeIf { it.isNotBlank() && it != "null" },
            expiresAtEpochSeconds = expiry,
            activeConnections = user.optString("active_cons").toIntOrNull(),
            maxConnections = user.optString("max_connections").toIntOrNull()
        )
    }

    private data class Credentials(
        val baseUrl: String,
        val username: String,
        val password: String
    )

    private fun parsePlaylist(url: String): Credentials? {
        val clean = url.trim()
        val qIndex = clean.indexOf('?')
        if (qIndex <= 0) return null

        val path = clean.substring(0, qIndex)
        if (!path.lowercase().endsWith("/get.php")) return null

        val params = clean.substring(qIndex + 1)
            .split('&')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null
                else {
                    val key = URLDecoder.decode(part.substring(0, eq), "UTF-8").lowercase()
                    val value = URLDecoder.decode(part.substring(eq + 1), "UTF-8")
                    key to value
                }
            }
            .toMap()

        val username = params["username"]?.takeIf { it.isNotBlank() } ?: return null
        val password = params["password"]?.takeIf { it.isNotBlank() } ?: return null
        val base = path.dropLast("/get.php".length).trimEnd('/')

        return Credentials(base, username, password)
    }
}
