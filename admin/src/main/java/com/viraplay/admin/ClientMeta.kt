package com.viraplay.admin

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone


data class ClientMeta(
    val name: String,
    val identifier: String?,
    val expiresIso: String?
)

object ClientMetaCodec {
    private val idRegex = Regex("\\s*\\[#([^\\]]*)]\\s*")
    private val expRegex = Regex("\\s*\\[V:(\\d{4}-\\d{2}-\\d{2})]\\s*")

    fun decode(label: String?): ClientMeta {
        val raw = label.orEmpty().trim()
        if (raw.isBlank()) return ClientMeta("", null, null)

        val identifier = idRegex.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val expiry = expRegex.find(raw)?.groupValues?.getOrNull(1)?.takeIf { isValidIso(it) }
        val name = raw
            .replace(idRegex, " ")
            .replace(expRegex, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return ClientMeta(name.ifBlank { raw }, identifier, expiry)
    }

    fun encode(name: String, identifier: String?, expiresIso: String?): String {
        val safeName = name.trim().ifBlank { "Cliente" }
        val safeId = identifier?.trim()?.replace("]", "")?.takeIf { it.isNotBlank() }
        val safeExpiry = expiresIso?.takeIf { isValidIso(it) }

        return buildString {
            append(safeName)
            safeId?.let { append(" [#").append(it).append(']') }
            safeExpiry?.let { append(" [V:").append(it).append(']') }
        }
    }

    fun inputToIso(value: String): String? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        if (isValidIso(clean)) return clean

        val br = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply {
            isLenient = false
        }
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }
        return runCatching { iso.format(br.parse(clean)!!) }.getOrNull()
    }

    fun isoToDisplay(value: String?): String? {
        val iso = value?.takeIf { isValidIso(it) } ?: return null
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val out = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return runCatching { out.format(parser.parse(iso)!!) }.getOrNull()
    }

    fun daysUntil(value: String?): Int? {
        val iso = value?.takeIf { isValidIso(it) } ?: return null
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }
        val target = runCatching { parser.parse(iso) }.getOrNull() ?: return null

        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val targetCal = Calendar.getInstance().apply {
            time = target
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return ((targetCal - now) / 86_400_000L).toInt()
    }

    fun epochSecondsToIso(epochSeconds: Long?): String? {
        if (epochSeconds == null || epochSeconds <= 0L) return null
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochSeconds * 1000L))
    }

    private fun isValidIso(value: String): Boolean {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        return runCatching { parser.parse(value) != null }.getOrDefault(false)
    }
}
