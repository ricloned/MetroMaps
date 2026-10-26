package com.metromaps.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.metromaps.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Release(val run: Int, val name: String, val apkUrl: String, val notes: String, val size: Long)

/** Автообновление из GitHub Releases этого репозитория. */
object Updater {

    val currentRun: Int get() = BuildConfig.BUILD_RUN
    val repo: String get() = BuildConfig.GH_REPO

    /** Последний релиз (или null). Блокирующий вызов. */
    fun latest(): Release? {
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "MetroMaps-Updater")
            if (c.responseCode == 404) return null
            if (c.responseCode !in 200..299) throw IOException("GitHub ответил ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            val run = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull() ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return Release(run, o.optString("name", tag), a.optString("browser_download_url"), o.optString("body"), a.optLong("size"))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    fun isNewer(r: Release): Boolean = r.run > currentRun

    /** Скачивает APK в кэш. Блокирующий вызов. */
    fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "MetroMaps-${r.run}.apk")
        var url = URL(r.apkUrl)
        var redirects = 0
        while (true) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("User-Agent", "MetroMaps-Updater")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("пустой редирект")
                c.disconnect()
                url = URL(url, loc)
                if (++redirects > 5) throw IOException("слишком много редиректов")
                continue
            }
            if (code !in 200..299) throw IOException("загрузка: код $code")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            c.disconnect()
            return out
        }
    }

    /** true — установщик открыт; false — сначала надо разрешить установку из этого приложения. */
    fun install(ctx: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return true
    }
}
