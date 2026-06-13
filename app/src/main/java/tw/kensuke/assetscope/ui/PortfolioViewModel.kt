package tw.kensuke.assetscope.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kensuke.assetscope.data.PortfolioRepository
import tw.kensuke.assetscope.domain.PortfolioCalculator
import tw.kensuke.assetscope.domain.model.ExchangeRates
import tw.kensuke.assetscope.domain.model.AppSettings
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.Expense
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.MarketSummary
import tw.kensuke.assetscope.domain.model.PortfolioSummary
import tw.kensuke.assetscope.domain.model.PerformanceSummary
import tw.kensuke.assetscope.domain.model.Transaction
import tw.kensuke.assetscope.domain.model.UiLanguage
import tw.kensuke.assetscope.domain.model.PaperTradingDashboard

data class PortfolioUiState(
    val holdings: List<Holding> = emptyList(),
    val rates: ExchangeRates = ExchangeRates(),
    val summary: PortfolioSummary = PortfolioCalculator.calculate(emptyList(), ExchangeRates()),
    val autoSyncFolder: String? = null,
    val serverUrl: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val performance: PerformanceSummary = PerformanceSummary(),
    val marketSummaries: Map<String, MarketSummary> = emptyMap(),
    val appSettings: AppSettings = AppSettings(),
    val paperTrading: PaperTradingDashboard? = null,
    val message: String? = null,
)

class PortfolioViewModel(
    private val repository: PortfolioRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    init {
        if (repository.serverUrl.value != null) {
            viewModelScope.launch {
                runCatching { repository.syncFromServer() }
            }
        }
    }

    private val portfolioState = combine(
        repository.holdings,
        repository.exchangeRates,
        repository.insights,
        repository.marketSummaries,
        repository.paperTrading,
    ) { holdings, rates, insights, marketSummaries, paperTrading ->
        PortfolioUiState(
            holdings = holdings,
            rates = rates,
            summary = PortfolioCalculator.calculate(holdings, rates),
            transactions = insights.transactions,
            expenses = insights.expenses,
            performance = insights.performance,
            marketSummaries = marketSummaries,
            paperTrading = paperTrading,
        )
    }

    val uiState: StateFlow<PortfolioUiState> = combine(
        portfolioState,
        repository.autoSyncFolder,
        repository.serverUrl,
        repository.appSettings,
        message,
    ) { portfolio, autoSyncFolder, serverUrl, appSettings, currentMessage ->
        portfolio.copy(
            autoSyncFolder = autoSyncFolder,
            serverUrl = serverUrl,
            appSettings = appSettings,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PortfolioUiState(),
    )

    fun importCsv(content: String) {
        viewModelScope.launch {
            message.value = runCatching {
                val result = repository.importCsv(content)
                localized(
                    "已匯入 ${result.importedCount} 筆，略過 ${result.skippedCount} 筆",
                    "Imported ${result.importedCount}; skipped ${result.skippedCount}",
                )
            }.getOrElse { error ->
                error.message ?: localized("匯入失敗", "Import failed")
            }
        }
    }

    fun configureAutoSync(folderUri: Uri) {
        viewModelScope.launch {
            message.value = runCatching {
                repository.configureAutoSync(folderUri)
                val result = repository.syncFromConfiguredFolder()
                localized(
                    "自動同步已啟用，已從 ${result.fileName} 匯入 ${result.importedCount} 筆",
                    "Auto-sync enabled. Imported ${result.importedCount} from ${result.fileName}",
                )
            }.getOrElse { error ->
                error.message ?: localized("無法設定自動同步", "Could not enable auto-sync")
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            message.value = runCatching {
                val result = repository.syncFromConfiguredFolder()
                localized(
                    "已從 ${result.fileName} 同步 ${result.importedCount} 筆",
                    "Synced ${result.importedCount} from ${result.fileName}",
                )
            }.getOrElse { error ->
                error.message ?: localized("同步失敗", "Sync failed")
            }
        }
    }

    fun disableAutoSync() {
        viewModelScope.launch {
            repository.disableAutoSync()
            message.value = localized("已停用資料夾自動同步", "Folder auto-sync disabled")
        }
    }

    fun configureServer(baseUrl: String, apiToken: String) {
        viewModelScope.launch {
            message.value = runCatching {
                val result = repository.configureServer(baseUrl, apiToken)
                localized(
                    "電腦同步已啟用，取得 ${result.importedCount} 筆資產",
                    "PC sync enabled. Received ${result.importedCount} assets",
                )
            }.getOrElse { error ->
                error.message ?: localized("無法連接電腦伺服器", "Could not connect to PC server")
            }
        }
    }

    fun syncServerNow() {
        viewModelScope.launch {
            message.value = runCatching {
                val result = repository.syncFromServer()
                localized(
                    "已從 ${result.sourceCount} 個來源同步 ${result.importedCount} 筆",
                    "Synced ${result.importedCount} assets from ${result.sourceCount} sources",
                )
            }.getOrElse { error ->
                error.message ?: localized("電腦同步失敗", "PC sync failed")
            }
        }
    }

    fun disableServerSync() {
        viewModelScope.launch {
            repository.disableServerSync()
            message.value = localized("已停用電腦伺服器同步", "PC server sync disabled")
        }
    }

    fun resetSampleData() {
        viewModelScope.launch {
            repository.resetToSampleData()
            message.value = localized("已還原範例資料", "Sample data restored")
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun refreshPaperTrading() {
        viewModelScope.launch {
            message.value = runCatching {
                repository.refreshPaperTrading()
                localized("模擬交易資料已更新", "Paper trading refreshed")
            }.getOrElse { error ->
                error.message ?: localized(
                    "無法更新模擬交易資料",
                    "Could not refresh paper trading",
                )
            }
        }
    }

    fun setDisplayCurrency(currency: Currency) {
        viewModelScope.launch { repository.setDisplayCurrency(currency) }
    }

    fun setUiLanguage(language: UiLanguage) {
        viewModelScope.launch { repository.setUiLanguage(language) }
    }

    private fun localized(zhTw: String, en: String): String =
        if (repository.appSettings.value.language == UiLanguage.EN) en else zhTw

    companion object {
        fun factory(repository: PortfolioRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PortfolioViewModel(repository) as T
                }
            }
    }
}
