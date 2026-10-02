package com.viraplay.shared

import java.net.URLEncoder

object SupportConfig {
    const val WHATSAPP_NUMBER = "558499276322"
    const val WHATSAPP_DISPLAY = "+55 84 9927-6322"

    fun whatsappUrl(deviceCode: String? = null): String {
        val message = buildString {
            append("Olá! Preciso de suporte com meu ViraPlay.")
            if (!deviceCode.isNullOrBlank()) {
                append(" Código do aparelho: ")
                append(deviceCode)
            }
        }
        return "https://wa.me/$WHATSAPP_NUMBER?text=" +
            URLEncoder.encode(message, "UTF-8")
    }
}
