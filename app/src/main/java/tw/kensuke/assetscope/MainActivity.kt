package tw.kensuke.assetscope

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.kensuke.assetscope.data.LocalPortfolioRepository
import tw.kensuke.assetscope.ui.AssetScopeApp
import tw.kensuke.assetscope.ui.theme.AssetScopeTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: LocalPortfolioRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = LocalPortfolioRepository(applicationContext)
        setContent {
            AssetScopeTheme {
                AssetScopeApp(repository = repository)
            }
        }
        importSharedCsv(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importSharedCsv(intent)
    }

    override fun onDestroy() {
        repository.close()
        super.onDestroy()
    }

    private fun importSharedCsv(intent: Intent?) {
        val sharedContent = intent?.sharedCsvContent() ?: return
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val content = when (sharedContent) {
                        is SharedCsvContent.UriContent -> readCsv(sharedContent.uri)
                        is SharedCsvContent.TextContent -> sharedContent.text
                    }
                    val result = repository.importCsv(content)
                    "已從分享檔案匯入 ${result.importedCount} 筆"
                }.getOrElse { error ->
                    error.message ?: "分享檔案匯入失敗"
                }
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun Intent.sharedCsvContent(): SharedCsvContent? = when (action) {
        Intent.ACTION_SEND -> {
            val uri = IntentCompat.getParcelableExtra(
                this,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )
            when {
                uri != null -> SharedCsvContent.UriContent(uri)
                !getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank() -> {
                    SharedCsvContent.TextContent(getStringExtra(Intent.EXTRA_TEXT).orEmpty())
                }
                else -> null
            }
        }
        Intent.ACTION_VIEW -> data?.let(SharedCsvContent::UriContent)
        else -> null
    }

    private fun readCsv(uri: Uri): String {
        val descriptor = contentResolver.openAssetFileDescriptor(uri, "r")
        descriptor?.use {
            require(it.length <= MAX_IMPORT_BYTES || it.length == UNKNOWN_FILE_SIZE) {
                "CSV 檔案不可超過 5 MB"
            }
        }

        return contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { reader ->
                val content = reader.readText()
                require(content.toByteArray().size <= MAX_IMPORT_BYTES) {
                    "CSV 檔案不可超過 5 MB"
                }
                content
            }
            ?: error("無法讀取分享的 CSV")
    }

    private sealed interface SharedCsvContent {
        data class UriContent(val uri: Uri) : SharedCsvContent
        data class TextContent(val text: String) : SharedCsvContent
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
        const val UNKNOWN_FILE_SIZE = -1L
    }
}
