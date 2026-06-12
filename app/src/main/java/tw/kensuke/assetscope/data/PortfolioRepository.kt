package tw.kensuke.assetscope.data

import kotlinx.coroutines.flow.StateFlow
import android.net.Uri
import tw.kensuke.assetscope.domain.model.ExchangeRates
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.MarketSummary
import tw.kensuke.assetscope.domain.model.PortfolioInsights
import tw.kensuke.assetscope.domain.model.PortfolioHistory
import tw.kensuke.assetscope.domain.model.PriceHistory

interface PortfolioRepository {
    val holdings: StateFlow<List<Holding>>
    val exchangeRates: StateFlow<ExchangeRates>
    val autoSyncFolder: StateFlow<String?>
    val serverUrl: StateFlow<String?>
    val insights: StateFlow<PortfolioInsights>
    val marketSummaries: StateFlow<Map<String, MarketSummary>>

    suspend fun importCsv(content: String): ImportResult
    suspend fun configureAutoSync(folderUri: Uri)
    suspend fun syncFromConfiguredFolder(): FolderSyncResult
    suspend fun disableAutoSync()
    suspend fun configureServer(baseUrl: String, apiToken: String): ServerSyncResult
    suspend fun syncFromServer(): ServerSyncResult
    suspend fun loadPriceHistory(holding: Holding, days: Int = 90): PriceHistory
    suspend fun loadPortfolioHistory(days: Int = 365): PortfolioHistory
    suspend fun refreshMarketSummaries()
    suspend fun disableServerSync()
    suspend fun resetToSampleData()
}

data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int,
)

data class FolderSyncResult(
    val fileName: String,
    val importedCount: Int,
    val skippedCount: Int,
)

data class ServerSyncResult(
    val importedCount: Int,
    val sourceCount: Int,
)
