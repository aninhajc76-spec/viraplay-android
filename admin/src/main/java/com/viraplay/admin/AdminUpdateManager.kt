package com.viraplay.admin

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

data class AdminUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val message: String,
    val mandatory: Boolean,
    val url: String,
    val sha256: String?
)

enum class AdminInstallLaunchResult {
    STARTED,
    NEED_PERMISSION
}

class AdminUpdateManager(private val context: Context) {
    companion object {
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/aninhajc76-spec/viraplay-android/main/update/latest.json"
    }

    fun check(): AdminUpdateInfo? {
        val text = getText(MANIFEST_URL)
        val root = JSONObject(text)

        val versionCode = root.optInt("adminVersionCode", 0)
        val url = root.optString("adminUrl", "").trim()
        if (versionCode <= BuildConfig.VERSION_CODE || url.isBlank()) return null

        return AdminUpdateInfo(
            versionCode = versionCode,
            versionName = root.optString("adminVersionName", versionCode.toString()),
            message = root.optString(
                "adminMessage",
                root.optString("message", "Nova atualização do ViraPlay ADM disponível.")
            ),
            mandatory = root.optBoolean("adminMandatory", root.optBoolean("mandatory", false)),
            url = url,
            sha256 = root.optString("adminSha256", "").trim().ifBlank { null }
        )
    }

    fun download(info: AdminUpdateInfo): File {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val target = File(targetDir, "ViraPlay-ADM-${info.versionName}.apk")
        val temp = File(targetDir, "ViraPlay-ADM-${info.versionName}.part")

        if (temp.exists()) temp.delete()

        val connection = URL(info.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ViraPlay-ADM/${BuildConfig.VERSION_NAME}")
        connection.connect()

        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            throw IllegalStateException("Falha ao baixar atualização: HTTP $code")
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

    fun launchInstaller(apk: File): AdminInstallLaunchResult {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return AdminInstallLaunchResult.NEED_PERMISSION
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
        return AdminInstallLaunchResult.STARTED
    }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ViraPlay-ADM/${BuildConfig.VERSION_NAME}")
        connection.connect()

        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            throw IllegalStateException("Falha ao verificar atualização: HTTP $code")
        }

        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .also { connection.disconnect() }
    }
}
