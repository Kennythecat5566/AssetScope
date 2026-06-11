package tw.kensuke.assetscope.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val message: String? = null,
)

class PortfolioViewModel(
    private val repository: PortfolioRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PortfolioUiState> = combine(
        repository.holdings,
        repository.exchangeRates,
        message,
    ) { holdings, rates, currentMessage ->
        PortfolioUiState(
            holdings = holdings,
            rates = rates,
            summary = PortfolioCalculator.calculate(holdings, rates),
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

