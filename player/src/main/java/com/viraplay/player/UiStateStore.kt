package com.viraplay.player

import android.content.Context

class UiStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("viraplay_ui", Context.MODE_PRIVATE)

    fun section(): String = prefs.getString("section", "HOME") ?: "HOME"
    fun setSection(value: String) = prefs.edit().putString("section", value).apply()

    fun category(key: String): String = prefs.getString("category_$key", "ALL") ?: "ALL"
    fun setCategory(key: String, value: String) = prefs.edit().putString("category_$key", value).apply()

    fun lastEnabled(): Boolean = prefs.getBoolean("last_enabled", true)
    fun setLastEnabled(value: Boolean) = prefs.edit().putBoolean("last_enabled", value).apply()
}
