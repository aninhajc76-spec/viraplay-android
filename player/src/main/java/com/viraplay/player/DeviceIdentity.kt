package com.viraplay.player

import android.content.Context
import java.security.SecureRandom
import java.util.UUID

class DeviceIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("viraplay_device", Context.MODE_PRIVATE)

    val deviceId: String by lazy {
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    val deviceSecret: String by lazy {
        prefs.getString("device_secret", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit().putString("device_secret", it).apply()
        }
    }

    val pairingCode: String by lazy {
        prefs.getString("pairing_code", null) ?: buildString {
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val random = SecureRandom()
            repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) }
        }.also { prefs.edit().putString("pairing_code", it).apply() }
    }
}
