package com.viraplay.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.viraplay.shared.SupportConfig

object Support {
    fun openWhatsApp(context: Context, code: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(SupportConfig.whatsappUrl(code))
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
