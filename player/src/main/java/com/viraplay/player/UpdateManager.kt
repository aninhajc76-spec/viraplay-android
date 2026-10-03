package com.viraplay.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val message: String,
    val mandatory: Boolean,
    val url: String,
    val sha256: String?
)

enum class InstallLaunchResult {
    STARTED,
    NEED_PERMISSION
}

class UpdateManager(private val context: Context) {
    companion object {
        // Fallback público. Futuramente o ADM/Worker pode publicar no endpoint privado
        // sem precisar alterar novamente os apps dos clientes.
        private const val FALLBACK_MANIFEST =
            "https://raw.githubusercontent.com/aninhajc76-spec/viraplay-android/main/update/latest.json"
    }

    fun check(): AppUpdateInfo? {
        val text = runCatching { getText(FALLBACK_MANIFEST) }.getOrNull() ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null

        val versionCode = root.optInt("versionCode", 0)
        val url = root.optString("url", "").trim()
        if (versionCode <= BuildConfig.VERSION_CODE || url.isBlank()) return null

        return AppUpdateInfo(
            versionCode = versionCode,
            versionName = root.optString("versionName", versionCode.toString()),
            message = root.optString(
                "message",
                "Nova versão disponível. Atualize para manter estabilidade e reprodução."
            ),
            mandatory = root.optBoolean("mandatory", false),
            url = url,
            sha256 = root.optString("sha256", "").trim().ifBlank { null }
        )
    }

    fun download(info: AppUpdateInfo): File {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val target = File(targetDir, "ViraPlay-${info.versionName}.apk")
        val temp = File(targetDir, "ViraPlay-${info.versionName}.part")

        if (temp.exists()) temp.delete()

        val connection = URL(info.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ViraPlay/${BuildConfig.VERSION_NAME}")
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Falha ao baixar atualização: HTTP ${connection.responseCode}")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        connection.inputStream.use { input ->
            temp.outputStream().buffered(64 * 1024).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        connection.disconnect()

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        info.sha256?.let { expected ->
            if (!actual.equals(expected, ignoreCase = true)) {
                temp.delete()
                throw IllegalStateException("Arquivo de atualização inválido. Tente novamente.")
            }
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }

        return target
    }

    fun launchInstaller(apk: File): InstallLaunchResult {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return InstallLaunchResult.NEED_PERMISSION
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
        return InstallLaunchResult.STARTED
    }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 6_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ViraPlay/${BuildConfig.VERSION_NAME}")
        connection.connect()

        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }

        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .also { connection.disconnect() }
    }
}
