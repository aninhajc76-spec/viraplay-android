package com.viraplay.player

import java.net.URLDecoder

object SourceResolver {
    fun xtreamFromPlaylist(url: String): XtreamCredentials? {
        val clean = url.trim()
        val qIndex = clean.indexOf('?')
        if (qIndex <= 0) return null

        val path = clean.substring(0, qIndex)
        if (!path.lowercase().endsWith("/get.php")) return null

        val query = clean.substring(qIndex + 1)
        val params = query.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null
            else {
                val key = URLDecoder.decode(part.substring(0, eq), "UTF-8").lowercase()
                val value = URLDecoder.decode(part.substring(eq + 1), "UTF-8")
                key to value
            }
        }.toMap()

        val username = params["username"]?.takeIf { it.isNotBlank() } ?: return null
        val password = params["password"]?.takeIf { it.isNotBlank() } ?: return null
        val base = path.dropLast("/get.php".length).trimEnd('/')
        val output = params["output"].orEmpty().lowercase()
        val extension = if (output.contains("m3u8") || output.contains("hls")) "m3u8" else "ts"

        return XtreamCredentials(
            baseUrl = base,
            username = username,
            password = password,
            liveExtension = extension
        )
    }

    fun usernameFromPlaylist(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val qIndex = url.indexOf('?')
        if (qIndex < 0) return null
        return url.substring(qIndex + 1)
            .split('&')
            .firstOrNull { it.startsWith("username=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.isNotBlank() }
    }
}
