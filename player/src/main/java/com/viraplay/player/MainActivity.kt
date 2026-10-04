package com.viraplay.player

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Fundo escuro desde o primeiro frame para evitar o clarão/cinza em algumas Android TV.
        window.setBackgroundDrawable(ColorDrawable(Color.rgb(1, 6, 17)))

        // Usa apenas o splash nativo. Na Android TV não seguramos uma tela vazia artificialmente.
        installSplashScreen()

        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3102)
        }

        setContent { ViraPlayApp() }
    }
}
