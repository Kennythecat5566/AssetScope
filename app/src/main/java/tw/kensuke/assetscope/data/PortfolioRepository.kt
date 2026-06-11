package tw.kensuke.assetscope.data

import kotlinx.coroutines.flow.StateFlow
import tw.kensuke.assetscope.domain.model.ExchangeRates
import tw.kensuke.assetscope.domain.model.Holding

interface PortfolioRepository {
    val holdings: StateFlow<List<Holding>>
    val exchangeRates: StateFlow<ExchangeRates>

    suspend fun importCsv(content: String): ImportResult
    suspend fun resetToSampleData()
}

data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int,
)

