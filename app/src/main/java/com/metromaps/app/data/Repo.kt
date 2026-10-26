package com.metromaps.app.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Загрузка и кэш данных метро (api.hh.ru — открытые данные, без ключа). */
class Repo(ctx: Context) {
    private val file = File(ctx.filesDir, "metro_all.json")

    fun cached(): String? = if (file.exists() && file.length() > 100) file.readText() else null

    fun cacheAgeMs(): Long = if (file.exists()) System.currentTimeMillis() - file.lastModified() else Long.MAX_VALUE

    /** Блокирующий вызов — запускать в Dispatchers.IO. */
    fun download(): String {
        val c = URL("https://api.hh.ru/metro").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 40000
            c.setRequestProperty("User-Agent", "MetroMaps/1.0 (Android)")
            c.setRequestProperty("HH-User-Agent", "MetroMaps/1.0 (metromaps@users.noreply.github.com)")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("сервер ответил $code")
            val text = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (!text.trimStart().startsWith("[")) throw IOException("неожиданный ответ")
            val tmp = File(file.parentFile, "metro_all.tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
            return text
        } finally {
            c.disconnect()
        }
    }
}
