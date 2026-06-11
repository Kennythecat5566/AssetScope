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
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.PortfolioSummary

data class PortfolioUiState(
    val holdings: List<Holding> = emptyList(),
    val rates: ExchangeRates = ExchangeRates(),
    val summary: PortfolioSummary = PortfolioCalculator.calculate(emptyList(), ExchangeRates()),
    val autoSyncFolder: String? = null,
    val serverUrl: String? = null,
    val message: String? = null,
)

class PortfolioViewModel(
    private val repository: PortfolioRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PortfolioUiState> = combine(
        repository.holdings,
        repository.exchangeRates,
        repository.autoSyncFolder,
        repository.serverUrl,
        message,
    ) { holdings, rates, autoSyncFolder, serverUrl, currentMessage ->
        PortfolioUiState(
            holdings = holdings,
            rates = rates,
            summary = PortfolioCalculator.calculate(holdings, rates),
            autoSyncFolder = autoSyncFolder,
            serverUrl = serverUrl,
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
                "已匯入 ${result.importedCount} 筆，略過 ${result.skippedCount} 筆"
            }.getOrElse { error ->
                error.message ?: "匯入失敗"
            }
        }
    }

    fun configureAutoSync(folderUri: Uri) {
        viewModelScope.launch {
            message.value = runCatching {
                repository.configureAutoSync(folderUri)
                val result = repository.syncFromConfiguredFolder()
                "自動同步已啟用，已從 ${result.fileName} 匯入 ${result.importedCount} 筆"
            }.getOrElse { error ->
                error.message ?: "無法設定自動同步"
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            message.value = runCatching {
                val result = repository.syncFromConfiguredFolder()
                "已從 ${result.fileName} 同步 ${result.importedCount} 筆"
            }.getOrElse { error ->
                error.message ?: "同步失敗"
            }
        }
    }

    fun disableAutoSync() {
        viewModelScope.launch {
            repository.disableAutoSync()
            message.value = "已停用資料夾自動同步"
        }
    }

    fun configureServer(baseUrl: String, apiToken: String) {
        viewModelScope.launch {
            message.value = runCatching {
                val result = repository.configureServer(baseUrl, apiToken)
                "電腦同步已啟用，取得 ${result.importedCount} 筆資產"
            }.getOrElse { error ->
                error.message ?: "無法連接電腦伺服器"
            }
        }
    }

    fun syncServerNow() {
        viewModelScope.launch {
            message.value = runCatching {
                val result = repository.syncFromServer()
                "已從 ${result.sourceCount} 個來源同步 ${result.importedCount} 筆"
            }.getOrElse { error ->
                error.message ?: "電腦同步失敗"
            }
        }
    }

    fun disableServerSync() {
        viewModelScope.launch {
            repository.disableServerSync()
            message.value = "已停用電腦伺服器同步"
        }
    }

    fun resetSampleData() {
        viewModelScope.launch {
            repository.resetToSampleData()
            message.value = "已還原範例資料"
        }
    }

    fun clearMessage() {
        message.value = null
    }

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
