package com.viraplay.player

import android.content.Context
import com.viraplay.shared.AppConfig
import com.viraplay.shared.Http
import com.viraplay.shared.XtreamAccountClient
import java.net.URLEncoder
import org.json.JSONObject

enum class DirectAccessMode { PROVIDER, DNS, M3U }

data class DirectAccessSession(
    val mode: DirectAccessMode,
    val playlistUrl: String,
    val providerCode: String? = null,
    val providerName: String? = null
)

data class ProviderConnection(
    val code: String,
    val name: String,
    val dnsPrimary: String,
    val dnsSecondary: String?
)

data class ProviderLoginResult(
    val provider: ProviderConnection,
    val playlistUrl: String
)

class DirectAccessStore(context: Context) {
    private val prefs = context.getSharedPreferences("vplayo_direct_access", Context.MODE_PRIVATE)

    fun session(): DirectAccessSession? {
        val mode = prefs.getString("mode", null)?.let { runCatching { DirectAccessMode.valueOf(it) }.getOrNull() }
            ?: return null
        val playlist = prefs.getString("playlist_url", null)?.takeIf { it.isNotBlank() } ?: return null
        return DirectAccessSession(
            mode = mode,
            playlistUrl = playlist,
            providerCode = prefs.getString("provider_code", null)?.takeIf { it.isNotBlank() },
            providerName = prefs.getString("provider_name", null)?.takeIf { it.isNotBlank() }
        )
    }

    fun saveProvider(code: String, name: String, playlistUrl: String) {
        prefs.edit()
            .putString("mode", DirectAccessMode.PROVIDER.name)
            .putString("playlist_url", playlistUrl)
            .putString("provider_code", code.trim().uppercase())
            .putString("provider_name", name)
            .putLong("provider_checked_at", System.currentTimeMillis())
            .apply()
    }

    fun updateProviderPlaylist(name: String, playlistUrl: String) {
        prefs.edit()
            .putString("provider_name", name)
            .putString("playlist_url", playlistUrl)
            .putLong("provider_checked_at", System.currentTimeMillis())
            .apply()
    }

    fun saveDns(playlistUrl: String, label: String) {
        prefs.edit()
            .putString("mode", DirectAccessMode.DNS.name)
            .putString("playlist_url", playlistUrl)
            .remove("provider_code")
            .putString("provider_name", label)
            .remove("provider_checked_at")
            .remove("provider_touch_at")
            .apply()
    }

    fun saveM3u(playlistUrl: String) {
        prefs.edit()
            .putString("mode", DirectAccessMode.M3U.name)
            .putString("playlist_url", playlistUrl.trim())
            .remove("provider_code")
            .remove("provider_name")
            .remove("provider_checked_at")
            .remove("provider_touch_at")
            .apply()
    }

    fun shouldRefreshProvider(maxAgeMs: Long = 6L * 60L * 60L * 1000L): Boolean {
        val at = prefs.getLong("provider_checked_at", 0L)
        return at <= 0L || System.currentTimeMillis() - at >= maxAgeMs
    }

    fun markProviderChecked() {
        prefs.edit().putLong("provider_checked_at", System.currentTimeMillis()).apply()
    }

    fun shouldTouchProvider(maxAgeMs: Long = 24L * 60L * 60L * 1000L): Boolean {
        val at = prefs.getLong("provider_touch_at", 0L)
        return at <= 0L || System.currentTimeMillis() - at >= maxAgeMs
    }

    fun markProviderTouched() {
        prefs.edit().putLong("provider_touch_at", System.currentTimeMillis()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun description(): String? = session()?.let { session ->
        when (session.mode) {
            DirectAccessMode.PROVIDER -> "Provedor • ${session.providerName ?: session.providerCode ?: "VPlayo"}"
            DirectAccessMode.DNS -> "DNS direto • ${session.providerName ?: "servidor"}"
            DirectAccessMode.M3U -> "Lista própria M3U / Xtream"
        }
    }
}

object ProviderAccessClient {
    fun resolve(code: String): ProviderConnection {
        val cleanCode = code.trim().uppercase()
        require(cleanCode.isNotBlank()) { "Informe o código do provedor." }
        val encoded = URLEncoder.encode(cleanCode, "UTF-8")
        val o = JSONObject(
            Http.getText(
                "${AppConfig.SERVER_BASE_URL}/api/provider/resolve?code=$encoded",
                maxChars = 100_000
            )
        )
        return ProviderConnection(
            code = o.optString("provider_code", cleanCode),
            name = o.optString("name", "Provedor VPlayo"),
            dnsPrimary = o.optString("dns_primary").trim().trimEnd('/'),
            dnsSecondary = o.optString("dns_secondary").trim().trimEnd('/').takeIf { it.isNotBlank() }
        ).also {
            require(it.dnsPrimary.isNotBlank()) { "O provedor ainda não configurou o DNS." }
        }
    }

    fun login(code: String, username: String, password: String): ProviderLoginResult {
        val provider = resolve(code)
        val user = username.trim()
        val pass = password.trim()
        require(user.isNotBlank() && pass.isNotBlank()) { "Informe usuário e senha." }

        val bases = listOfNotNull(provider.dnsPrimary, provider.dnsSecondary).distinct()
        var lastError: Throwable? = null
        for (base in bases) {
            val playlist = SourceResolver.buildXtreamPlaylist(base, user, pass)
            val info = runCatching { XtreamAccountClient.fetchFromPlaylist(playlist) }
                .onFailure { lastError = it }
                .getOrNull()
            val active = info != null && (info.status.isNullOrBlank() || info.status.equals("Active", true))
            if (active) {
                return ProviderLoginResult(provider, playlist)
            }
        }
        throw IllegalStateException(lastError?.message ?: "Usuário ou senha inválidos, ou servidor indisponível.")
    }

    fun loginDirectDns(dns: String, username: String, password: String): String {
        val raw = dns.trim().trimEnd('/')
        require(raw.isNotBlank()) { "Informe o DNS." }
        val base = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "http://$raw"
        val playlist = SourceResolver.buildXtreamPlaylist(base, username, password)
        val info = XtreamAccountClient.fetchFromPlaylist(playlist)
            ?: throw IllegalStateException("Não foi possível validar esse servidor.")
        val active = info.status.isNullOrBlank() || info.status.equals("Active", true)
        require(active) { "A conta não está ativa no servidor." }
        return playlist
    }

    fun rebuildWithCurrentDns(session: DirectAccessSession): ProviderLoginResult {
        val code = session.providerCode ?: throw IllegalStateException("Código do provedor não encontrado.")
        val old = SourceResolver.xtreamFromPlaylist(session.playlistUrl)
            ?: throw IllegalStateException("Acesso Xtream inválido.")
        return login(code, old.username, old.password)
    }

    fun touch(code: String, deviceId: String, platform: String) {
        runCatching {
            Http.postJson(
                "${AppConfig.SERVER_BASE_URL}/api/provider/session",
                JSONObject()
                    .put("provider_code", code.trim().uppercase())
                    .put("device_id", deviceId)
                    .put("platform", platform)
                    .put("app_version", BuildConfig.VERSION_NAME)
            )
        }
    }
}
