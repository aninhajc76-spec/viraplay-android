package com.viraplay.player

import android.content.Context
import java.security.MessageDigest

class PlaybackPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("viraplay_preferences", Context.MODE_PRIVATE)

    var groupChannels: Boolean
        get() = prefs.getBoolean("group_channels", true)
        set(value) = prefs.edit().putBoolean("group_channels", value).apply()

    var autoQuality: Boolean
        get() = prefs.getBoolean("auto_quality", true)
        set(value) = prefs.edit().putBoolean("auto_quality", value).apply()

    var manualQuality: String
        get() = prefs.getString("manual_quality", "FHD").orEmpty().ifBlank { "FHD" }
        set(value) = prefs.edit().putString("manual_quality", value).apply()

    var parentalEnabled: Boolean
        get() = prefs.getBoolean("parental_enabled", true)
        set(value) = prefs.edit().putBoolean("parental_enabled", value).apply()

    fun hasPin(): Boolean = !prefs.getString("parental_pin_hash", null).isNullOrBlank()

    fun setPin(pin: String): Boolean {
        if (!pin.matches(Regex("\\d{4}"))) return false
        prefs.edit().putString("parental_pin_hash", hash(pin)).apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val saved = prefs.getString("parental_pin_hash", null) ?: return false
        return saved == hash(pin)
    }

    fun clearPin() {
        prefs.edit().remove("parental_pin_hash").apply()
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private val adultWords = listOf(
    "adult", "adulto", "adultos", "xxx", "18+", "+18", "porn", "erotic", "erotico", "erótico", "sexo", "sex"
)

fun isAdultContent(item: CatalogItem): Boolean =
    adultWords.any { word ->
        item.categoryName.contains(word, ignoreCase = true) || item.name.contains(word, ignoreCase = true)
    }

fun isAdultCategory(name: String): Boolean = adultWords.any { name.contains(it, ignoreCase = true) }
