package com.kai.masterbrowse

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Manual in-app update from the latest GitHub release of this repo. */
object Updater {

    private const val LATEST_URL =
        "https://api.github.com/repos/kaiharimoto/Master-Browse/releases/latest"

    data class Release(val tag: String, val apkUrl: String?)

    fun fetchLatest(): Release {
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "MasterBrowse")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        try {
            if (conn.responseCode != 200) {
                throw IOException("GitHub API returned HTTP ${conn.responseCode} (is there a public release?)")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name")
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }
            return Release(tag, apkUrl)
        } finally {
            conn.disconnect()
        }
    }

    fun downloadApk(context: Context, url: String, onProgress: (Int) -> Unit): File {
        val dest = File(context.cacheDir, "update.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "MasterBrowse")
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) throw IOException("Download failed: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
            return dest
        } finally {
            conn.disconnect()
        }
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
