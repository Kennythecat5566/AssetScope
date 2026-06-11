package tw.kensuke.assetscope.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.kensuke.assetscope.BuildConfig
import java.net.HttpURLConnection
import java.net.URI

data class AppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

class AppUpdateManager(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = URI(
            "https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest",
        ).toURL().openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            require(connection.responseCode in 200..299) {
                "無法檢查更新：HTTP ${connection.responseCode}"
            }
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = root.getString("tag_name").removePrefix("v")
            if (compareVersions(version, BuildConfig.VERSION_NAME) <= 0) return@withContext null
            val assets = root.getJSONArray("assets")
            val apkUrl = (0 until assets.length())
                .map(assets::getJSONObject)
                .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
                ?.getString("browser_download_url")
                ?: error("最新版 Release 沒有 APK")
            AppUpdate(
                versionName = version,
                releaseNotes = root.optString("body"),
                downloadUrl = apkUrl,
            )
        } finally {
            connection.disconnect()
        }
    }

    fun download(update: AppUpdate): Long {
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("AssetScope ${update.versionName}")
            .setDescription("正在下載 App 更新")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                "AssetScope-${update.versionName}.apk",
            )
        return requireNotNull(appContext.getSystemService<DownloadManager>()).enqueue(request)
    }

    fun openInstaller(downloadId: Long) {
        val manager = requireNotNull(appContext.getSystemService<DownloadManager>())
        val uri = manager.getUriForDownloadedFile(downloadId)
            ?: error("找不到下載完成的 APK")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(intent)
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".").map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split(".").map { it.toIntOrNull() ?: 0 }
        val size = maxOf(leftParts.size, rightParts.size)
        repeat(size) { index ->
            val comparison = (leftParts.getOrNull(index) ?: 0)
                .compareTo(rightParts.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
