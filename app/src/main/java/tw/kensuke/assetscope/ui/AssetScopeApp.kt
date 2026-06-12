package tw.kensuke.assetscope.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tw.kensuke.assetscope.BuildConfig
import tw.kensuke.assetscope.data.AppUpdate
import tw.kensuke.assetscope.data.AppUpdateManager
import tw.kensuke.assetscope.data.PortfolioRepository
import tw.kensuke.assetscope.domain.model.Allocation
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.Expense
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.Institution
import tw.kensuke.assetscope.domain.model.MarketSummary
import tw.kensuke.assetscope.domain.model.PerformanceSummary
import tw.kensuke.assetscope.domain.model.PaperBot
import tw.kensuke.assetscope.domain.model.PaperTradingDashboard
import tw.kensuke.assetscope.domain.model.PortfolioHistory
import tw.kensuke.assetscope.domain.model.PriceHistory
import tw.kensuke.assetscope.domain.model.PortfolioSummary
import tw.kensuke.assetscope.domain.model.Transaction
import tw.kensuke.assetscope.domain.model.TransactionType
import tw.kensuke.assetscope.domain.model.UiLanguage
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetScopeApp(repository: PortfolioRepository) {
    val viewModel: PortfolioViewModel = viewModel(factory = PortfolioViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager(context) }
    val coroutineScope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var updateDownloadId by remember { mutableStateOf<Long?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var chartHolding by remember { mutableStateOf<Holding?>(null) }
    var chartHistory by remember { mutableStateOf<PriceHistory?>(null) }
    var chartLoading by remember { mutableStateOf(false) }
    var chartError by remember { mutableStateOf<String?>(null) }
    var chartDays by remember { mutableStateOf(90) }
    var showPortfolioTrend by remember { mutableStateOf(false) }
    var portfolioHistory by remember { mutableStateOf<PortfolioHistory?>(null) }
    var portfolioHistoryLoading by remember { mutableStateOf(false) }
    var portfolioHistoryError by remember { mutableStateOf<String?>(null) }
    val displayCurrency = state.appSettings.displayCurrency
    var showSettings by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var transactionFilter by rememberSaveable { mutableStateOf(TransactionFilter.ALL) }
    val pagerState = rememberPagerState(pageCount = { AppPage.entries.size })
    val snackbarHostState = remember { SnackbarHostState() }
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.readText(context)?.let(viewModel::importCsv)
    }
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            viewModel.configureAutoSync(it)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) {
            checkingUpdate = true
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { availableUpdate = it }
                .onFailure { updateMessage = it.message }
            checkingUpdate = false
        }
    }
    LaunchedEffect(state.serverUrl) {
        if (state.serverUrl != null) {
            runCatching { repository.syncFromServer() }
        }
    }
    LaunchedEffect(state.serverUrl, state.holdings) {
        if (state.serverUrl != null) {
            runCatching { repository.refreshMarketSummaries() }
        }
    }
    LaunchedEffect(updateMessage) {
        updateMessage?.let {
            snackbarHostState.showSnackbar(it)
            updateMessage = null
        }
    }
    DisposableEffect(updateDownloadId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val completedId = intent?.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID,
                    -1L,
                )
                if (completedId != null && completedId == updateDownloadId) {
                    runCatching { updateManager.openInstaller(completedId) }
                        .onFailure { updateMessage = it.message ?: "無法開啟更新安裝程式" }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    CompositionLocalProvider(LocalUiLanguage provides state.appSettings.language) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {},
                actions = {
                    if (!BuildConfig.DEBUG) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    checkingUpdate = true
                                    runCatching { updateManager.checkForUpdate() }
                                        .onSuccess { update ->
                                            availableUpdate = update
                                            if (update == null) {
                                                updateMessage =
                                                    "目前版本 ${BuildConfig.VERSION_NAME} 已是最新版"
                                            }
                                        }
                                        .onFailure {
                                            updateMessage = it.message ?: "無法檢查更新"
                                        }
                                    checkingUpdate = false
                                }
                            },
                            enabled = !checkingUpdate,
                        ) {
                            Icon(
                                Icons.Outlined.SystemUpdate,
                                contentDescription = "檢查 App 更新",
                            )
                        }
                    }
                    IconButton(onClick = viewModel::resetSampleData) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = uiText("重新整理", "Refresh"),
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = uiText("設定", "Settings"),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PageNavigation(
                selectedPage = pagerState.currentPage,
                onPageSelected = { page ->
                    coroutineScope.launch { pagerState.animateScrollToPage(page) }
                },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val pageType = AppPage.entries[page]
                val listState = rememberLazyListState()
                val regularHoldings = remember(state.holdings) {
                    state.holdings
                        .filterNot(Holding::isCurrencyHolding)
                        .sortedWith(compareBy(Holding::institution, Holding::symbol))
                }
                val currencyHoldings = remember(state.holdings) {
                    state.holdings
                        .filter(Holding::isCurrencyHolding)
                        .sortedBy(Holding::symbol)
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 18.dp,
                            top = 12.dp,
                            end = 18.dp,
                            bottom = 36.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                    when (pageType) {
                        AppPage.OVERVIEW -> {
                            item(key = "overview-total") {
                                TotalAssetOverviewCard(
                                    summary = state.summary,
                                    usdToTwd = state.rates.usdToTwd,
                                    rateSource = state.rates.source,
                                    rateUpdatedAt = state.rates.updatedAt,
                                    displayCurrency = displayCurrency,
                                    onShowTrend = {
                                        showPortfolioTrend = true
                                        portfolioHistory = null
                                        portfolioHistoryError = null
                                        portfolioHistoryLoading = true
                                        coroutineScope.launch {
                                            runCatching { repository.loadPortfolioHistory() }
                                                .onSuccess { portfolioHistory = it }
                                                .onFailure {
                                                    portfolioHistoryError =
                                                        it.message ?: "無法載入資產歷史"
                                                }
                                            portfolioHistoryLoading = false
                                        }
                                    },
                                )
                            }
                            availableUpdate?.let { update ->
                                item(key = "overview-update") {
                                    AppUpdateCard(
                                        update = update,
                                        currentVersion = BuildConfig.VERSION_NAME,
                                        isDownloading = updateDownloadId != null,
                                        onDownload = {
                                            coroutineScope.launch {
                                                runCatching { updateManager.download(update) }
                                                    .onSuccess {
                                                        updateDownloadId = it
                                                        updateMessage =
                                                            "已開始下載 ${update.versionName}"
                                                    }
                                                    .onFailure {
                                                        updateMessage =
                                                            it.message ?: "無法下載更新"
                                                    }
                                            }
                                        },
                                    )
                                }
                            }
                            if (state.transactions.isNotEmpty()) {
                                item(key = "overview-performance") {
                                    PerformanceCard(
                                        performance = state.performance,
                                        displayCurrency = displayCurrency,
                                        usdToTwd = state.rates.usdToTwd,
                                    )
                                }
                            }
                            item(key = "overview-allocation") {
                                DistributionCard(
                                    assetAllocations = state.summary.assetAllocations,
                                    institutionAllocations =
                                        state.summary.institutionAllocations,
                                    displayCurrency = displayCurrency,
                                    usdToTwd = state.rates.usdToTwd,
                                )
                            }
                        }
                        AppPage.HOLDINGS -> {
                            item(key = "holdings-header") {
                                SectionHeader(
                                    title = uiText("持倉明細", "Holdings"),
                                    trailing = uiText(
                                        "${state.holdings.size} 項資產",
                                        "${state.holdings.size} assets",
                                    ),
                                )
                            }
                            items(regularHoldings, key = Holding::id) { holding ->
                                HoldingRow(
                                    holding = holding,
                                    marketSummary = state.marketSummaries[
                                        "${holding.institution.name}:${holding.symbol}"
                                    ],
                                    displayCurrency = displayCurrency,
                                    usdToTwd = state.rates.usdToTwd,
                                    onShowChart = {
                                        chartHolding = holding
                                        chartHistory = null
                                        chartError = null
                                        chartLoading = true
                                        chartDays = 90
                                        coroutineScope.launch {
                                            runCatching {
                                                repository.loadPriceHistory(
                                                    holding,
                                                    chartDays,
                                                )
                                            }
                                                .onSuccess { chartHistory = it }
                                                .onFailure {
                                                    chartError =
                                                        it.message ?: "無法載入歷史行情"
                                                }
                                            chartLoading = false
                                        }
                                    },
                                )
                            }
                            if (currencyHoldings.isNotEmpty()) {
                                item(key = "currency-divider") {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                items(currencyHoldings, key = Holding::id) { holding ->
                                    HoldingRow(
                                        holding = holding,
                                        marketSummary = null,
                                        displayCurrency = displayCurrency,
                                        usdToTwd = state.rates.usdToTwd,
                                        onShowChart = {},
                                    )
                                }
                            }
                        }
                        AppPage.TRANSACTIONS -> {
                            val filteredTransactions = when (transactionFilter) {
                                TransactionFilter.ALL -> state.transactions
                                TransactionFilter.FIRSTRADE -> state.transactions.filter {
                                    it.institution == Institution.FIRSTRade
                                }
                                TransactionFilter.SINOPAC -> state.transactions.filter {
                                    it.institution == Institution.SINOPAC_SECURITIES
                                }
                            }
                            item(key = "transactions-header") {
                                TransactionHeader(
                                    totalCount = state.transactions.size,
                                    filter = transactionFilter,
                                    onFilterChange = { transactionFilter = it },
                                )
                            }
                            item(key = "transactions-timeline") {
                                TransactionContent(
                                    transactions = filteredTransactions,
                                    filter = transactionFilter,
                                    displayCurrency = displayCurrency,
                                    usdToTwd = state.rates.usdToTwd,
                                )
                            }
                        }
                        AppPage.EXPENSES -> {
                            val expenseData = buildExpensePageData(
                                expenses = state.expenses,
                                usdToTwd = state.rates.usdToTwd,
                            )
                            item(key = "expenses-summary") {
                                ExpenseSummarySection(
                                    data = expenseData,
                                    displayCurrency = displayCurrency,
                                    usdToTwd = state.rates.usdToTwd,
                                )
                            }
                            if (expenseData.monthlyExpenses.isEmpty()) {
                                item(key = "expenses-empty") {
                                    ExpenseEmptySection()
                                }
                            } else {
                                if (expenseData.allocations.isNotEmpty()) {
                                    item(key = "expenses-categories") {
                                        ExpenseCategoriesSection(
                                            allocations = expenseData.allocations,
                                            displayCurrency = displayCurrency,
                                            usdToTwd = state.rates.usdToTwd,
                                        )
                                    }
                                }
                                item(key = "expenses-activity") {
                                    ExpenseActivitySection(
                                        expenses = expenseData.monthlyExpenses,
                                        displayCurrency = displayCurrency,
                                        usdToTwd = state.rates.usdToTwd,
                                    )
                                }
                            }
                        }
                        AppPage.ROBOTS -> {
                            item(key = "robots-dashboard") {
                                PaperTradingPage(
                                    dashboard = state.paperTrading,
                                    displayCurrency = displayCurrency,
                                    usdToTwd = state.rates.usdToTwd,
                                    onRefresh = viewModel::refreshPaperTrading,
                                )
                            }
                        }
                        AppPage.SYNC -> {
                            item(key = "sync-server") {
                                ServerSyncCard(
                                    configuredUrl = state.serverUrl,
                                    defaultUrl = "http://192.168.0.102:8787",
                                    onConnect = viewModel::configureServer,
                                    onSyncNow = viewModel::syncServerNow,
                                    onDisable = viewModel::disableServerSync,
                                )
                            }
                            item(key = "sync-folder") {
                                AutoSyncCard(
                                    enabled = state.autoSyncFolder != null,
                                    onChooseFolder = { folderLauncher.launch(null) },
                                    onSyncNow = viewModel::syncNow,
                                    onDisable = viewModel::disableAutoSync,
                                )
                            }
                            item(key = "sync-import") {
                                ImportCard(
                                    onImport = {
                                        csvLauncher.launch(arrayOf("text/*", "text/csv"))
                                    },
                                )
                            }
                        }
                    }
                }
                    if (pageType == AppPage.OVERVIEW && listState.isScrollInProgress) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.68f),
                        ) {
                            Text(
                                text = scrollHint(
                                    page = pageType,
                                    visibleKey = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.offset + it.size > 0 }
                                        ?.key,
                                    regularHoldings = regularHoldings,
                                    currencyHoldings = currencyHoldings,
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    chartHolding?.let { holding ->
        StockChartDialog(
            holding = holding,
            history = chartHistory,
            loading = chartLoading,
            error = chartError,
            displayCurrency = displayCurrency,
            usdToTwd = state.rates.usdToTwd,
            selectedDays = chartDays,
            onPeriodChange = { days ->
                if (days != chartDays) {
                    chartDays = days
                    chartLoading = true
                    chartError = null
                    coroutineScope.launch {
                        runCatching { repository.loadPriceHistory(holding, days) }
                            .onSuccess { chartHistory = it }
                            .onFailure {
                                chartError = it.message ?: "無法載入價格歷史"
                            }
                        chartLoading = false
                    }
                }
            },
            onDismiss = {
                chartHolding = null
                chartHistory = null
                chartError = null
            },
        )
    }
    if (showPortfolioTrend) {
        PortfolioTrendDialog(
            history = portfolioHistory,
            loading = portfolioHistoryLoading,
            error = portfolioHistoryError,
            displayCurrency = displayCurrency,
            usdToTwd = state.rates.usdToTwd,
            onDismiss = {
                showPortfolioTrend = false
                portfolioHistory = null
                portfolioHistoryError = null
            },
        )
    }
    if (showSettings) {
        SettingsDialog(
            settings = state.appSettings,
            onCurrencyChange = viewModel::setDisplayCurrency,
            onLanguageChange = viewModel::setUiLanguage,
            onDismiss = { showSettings = false },
        )
    }
    }
}

@Composable
private fun SettingsDialog(
    settings: tw.kensuke.assetscope.domain.model.AppSettings,
    onCurrencyChange: (Currency) -> Unit,
    onLanguageChange: (UiLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("設定", "Settings")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SettingChoice(
                    title = uiText("顯示幣別", "Display currency"),
                    options = Currency.entries.map { it.name },
                    selected = settings.displayCurrency.name,
                    onSelect = { onCurrencyChange(Currency.valueOf(it)) },
                )
                SettingChoice(
                    title = uiText("介面語言", "Interface language"),
                    options = listOf("繁體中文", "English"),
                    selected = if (settings.language == UiLanguage.EN) "English" else "繁體中文",
                    onSelect = {
                        onLanguageChange(
                            if (it == "English") UiLanguage.EN else UiLanguage.ZH_TW,
                        )
                    },
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(uiText("完成", "Done"))
            }
        },
    )
}

@Composable
private fun SettingChoice(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Button(
                    onClick = { onSelect(option) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == option) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (selected == option) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                ) {
                    Text(option)
                }
            }
        }
    }
}

@Composable
private fun PageNavigation(
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppPage.entries.forEachIndexed { index, page ->
            Button(
                onClick = { onPageSelected(index) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 4.dp,
                    vertical = 8.dp,
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedPage == index) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (selectedPage == index) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                Text(
                    page.label(LocalUiLanguage.current),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private data class ExpensePageData(
    val latestMonth: String?,
    val monthlyExpenses: List<Expense>,
    val totalTwd: Double,
    val allocations: List<Allocation>,
)

private fun buildExpensePageData(
    expenses: List<Expense>,
    usdToTwd: Double,
): ExpensePageData {
    val latestMonth = expenses.maxOfOrNull { it.transactionDate.take(7) }
    val monthlyExpenses = expenses.filter { it.transactionDate.startsWith(latestMonth.orEmpty()) }
    val totalTwd = monthlyExpenses.sumOf { expense ->
        expense.amount * if (expense.currency == Currency.USD) usdToTwd else 1.0
    }
    val categoryTotals = monthlyExpenses
        .groupBy { it.category }
        .mapValues { (_, items) ->
            items.sumOf { expense ->
                expense.amount * if (expense.currency == Currency.USD) usdToTwd else 1.0
            }
        }
        .filterValues { it > 0 }
        .toList()
        .sortedByDescending { it.second }
    val positiveTotal = categoryTotals.sumOf { it.second }
    val allocations = categoryTotals.map { (category, value) ->
        Allocation(
            label = category.displayName,
            valueTwd = value,
            ratio = if (positiveTotal == 0.0) 0.0 else value / positiveTotal,
        )
    }
    return ExpensePageData(latestMonth, monthlyExpenses, totalTwd, allocations)
}

@Composable
private fun ExpenseSummarySection(
    data: ExpensePageData,
    displayCurrency: Currency,
    usdToTwd: Double,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(
            title = uiText("日常花費", "Expenses"),
            trailing = data.latestMonth?.replace("-", " / ")
                ?: uiText("尚無資料", "No data"),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    uiText("本月信用卡支出", "Card spending this month"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    data.totalTwd
                        .toDisplayCurrency(displayCurrency, usdToTwd)
                        .asMoney(displayCurrency),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    uiText(
                        "${data.monthlyExpenses.size} 筆永豐信用卡交易",
                        "${data.monthlyExpenses.size} SinoPac card transactions",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpenseEmptySection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            uiText(
                "尚無刷卡紀錄。將永豐官方匯出的 CSV 放入電腦伺服器的 data/imports 後同步。",
                "No card activity yet. Export the official SinoPac CSV, place it in the server data/imports folder, then sync.",
            ),
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpenseCategoriesSection(
    allocations: List<Allocation>,
    displayCurrency: Currency,
    usdToTwd: Double,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader(
                uiText("支出分類", "Spending categories"),
                uiText("${allocations.size} 類", "${allocations.size} categories"),
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AllocationPie(
                    allocations = allocations,
                    colors = allocationColors,
                    modifier = Modifier.size(190.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            allocations.forEachIndexed { index, allocation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(
                                allocationColors[index % allocationColors.size],
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        expenseCategoryLabel(allocation.label),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        allocation.valueTwd
                            .toDisplayCurrency(displayCurrency, usdToTwd)
                            .asMoney(displayCurrency),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseActivitySection(
    expenses: List<Expense>,
    displayCurrency: Currency,
    usdToTwd: Double,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader(
                uiText("刷卡明細", "Card activity"),
                uiText("最近 ${expenses.size} 筆", "${expenses.size} recent"),
            )
            Spacer(Modifier.height(8.dp))
            expenses.forEachIndexed { index, expense ->
                val multiplier = currencyMultiplier(
                    expense.currency,
                    displayCurrency,
                    usdToTwd,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            expense.merchant,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(expense.transactionDate)
                                append(" · ")
                                append(expense.category.localizedName())
                                if (expense.cardLastFour.isNotBlank()) {
                                    append(" · •")
                                    append(expense.cardLastFour)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        (expense.amount * multiplier).asSignedMoney(displayCurrency),
                        color = if (expense.amount < 0) lossColor else Color.Unspecified,
                    )
                }
                if (index != expenses.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun TransactionHeader(
    totalCount: Int,
    filter: TransactionFilter,
    onFilterChange: (TransactionFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(
            uiText("交易明細", "Transactions"),
            uiText("$totalCount 筆", "$totalCount items"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionFilter.entries.forEach { item ->
                Button(
                    onClick = { onFilterChange(item) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (filter == item) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (filter == item) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                ) {
                    Text(item.label(LocalUiLanguage.current))
                }
            }
        }
    }
}

@Composable
private fun TransactionContent(
    transactions: List<Transaction>,
    filter: TransactionFilter,
    displayCurrency: Currency,
    usdToTwd: Double,
) {
    if (transactions.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Text(
                text = when (filter) {
                    TransactionFilter.ALL ->
                        uiText(
                            "尚無交易資料。請到「同步」頁按立即同步。",
                            "No transactions yet. Open Sync and tap Sync now.",
                        )
                    TransactionFilter.FIRSTRADE ->
                        uiText(
                            "尚無 Firstrade 交易資料，請重新匯出交易 CSV。",
                            "No Firstrade transactions. Export the transaction CSV again.",
                        )
                    TransactionFilter.SINOPAC ->
                        uiText(
                            "目前沒有 Shioaji 可查的永豐持倉批次或已實現賣出。",
                            "No SinoPac position lots or realized sales are currently available from Shioaji.",
                        )
                },
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        TransactionHistoryCard(
            transactions = transactions,
            totalCount = transactions.size,
            displayCurrency = displayCurrency,
            usdToTwd = usdToTwd,
        )
    }
}

@Composable
private fun AppUpdateCard(
    update: AppUpdate,
    currentVersion: String,
    isDownloading: Boolean,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "APP UPDATE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        uiText("新版本 ${update.versionName}", "Version ${update.versionName}"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        uiText(
                            "目前 $currentVersion → 最新 ${update.versionName}",
                            "Current $currentVersion → Latest ${update.versionName}",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            if (update.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    update.releaseNotes,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDownload,
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (isDownloading) {
                        uiText("下載中", "Downloading")
                    } else {
                        uiText("下載並安裝", "Download and install")
                    },
                )
            }
        }
    }
}

@Composable
private fun PerformanceCard(
    performance: PerformanceSummary,
    displayCurrency: Currency,
    usdToTwd: Double,
) {
    val multiplier = if (displayCurrency == Currency.TWD) usdToTwd else 1.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader(uiText("投資績效", "Performance"), performance.returnRate.asPercent())
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PerformanceMetric(
                    label = uiText("已實現損益", "Realized P/L"),
                    value = (performance.realizedProfit * multiplier)
                        .asSignedMoney(displayCurrency),
                    modifier = Modifier.weight(1f),
                )
                PerformanceMetric(
                    label = uiText("未實現損益", "Unrealized P/L"),
                    value = (performance.unrealizedProfit * multiplier)
                        .asSignedMoney(displayCurrency),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PerformanceMetric(
                    label = uiText("股息收入", "Dividend income"),
                    value = (performance.dividendIncome * multiplier).asMoney(displayCurrency),
                    modifier = Modifier.weight(1f),
                )
                PerformanceMetric(
                    label = uiText("總投資報酬", "Total return"),
                    value = (performance.totalReturn * multiplier)
                        .asSignedMoney(displayCurrency),
                    modifier = Modifier.weight(1f),
                )
            }
            if (performance.valuationNote.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    performance.valuationNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PerformanceMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(14.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TransactionHistoryCard(
    transactions: List<Transaction>,
    totalCount: Int,
    displayCurrency: Currency,
    usdToTwd: Double,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader(
                uiText("交易時間軸", "Timeline"),
                uiText(
                    "最近 ${transactions.size}／$totalCount 筆",
                    "${transactions.size} of $totalCount recent",
                ),
            )
            Spacer(Modifier.height(12.dp))
            transactions.forEachIndexed { index, transaction ->
                TransactionRow(transaction, displayCurrency, usdToTwd)
                if (index != transactions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    displayCurrency: Currency,
    usdToTwd: Double,
) {
    val multiplier = currencyMultiplier(transaction.currency, displayCurrency, usdToTwd)
    val typeColor = when (transaction.transactionType) {
        TransactionType.BUY -> lossColor
        TransactionType.SELL -> profitColor
        TransactionType.DIVIDEND -> MaterialTheme.colorScheme.secondary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(typeColor.copy(alpha = 0.14f), MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                transaction.transactionType.localizedName(),
                style = MaterialTheme.typography.labelMedium,
                color = typeColor,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${transaction.symbol} · ${transaction.tradeDate}",
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${transaction.institution.localizedName()} · ${transaction.accountName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                when (transaction.transactionType) {
                    TransactionType.DIVIDEND -> transaction.name
                    else -> (
                        "${transaction.quantity.asQuantity()} × " +
                            (transaction.price * multiplier).asMoney(displayCurrency)
                        )
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (transaction.amount * multiplier).asSignedMoney(displayCurrency),
                fontWeight = FontWeight.Medium,
            )
            if (transaction.transactionType == TransactionType.SELL) {
                Text(
                    "${uiText("損益", "P/L")} ${
                        (transaction.realizedProfit * multiplier).asSignedMoney(displayCurrency)
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (transaction.realizedProfit >= 0) profitColor else lossColor,
                )
            }
            transaction.settledDate?.let {
                Text(
                    "${uiText("交割", "Settled")} $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ServerSyncCard(
    configuredUrl: String?,
    defaultUrl: String,
    onConnect: (String, String) -> Unit,
    onSyncNow: () -> Unit,
    onDisable: () -> Unit,
) {
    var url by rememberSaveable(configuredUrl) {
        mutableStateOf(configuredUrl ?: defaultUrl)
    }
    var token by rememberSaveable { mutableStateOf("") }
    val isConfigured = configuredUrl != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        uiText("電腦資產伺服器", "PC asset server"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (isConfigured) {
                    uiText(
                        "已連接 $configuredUrl，每 12 小時自動同步。",
                        "Connected to $configuredUrl. Syncs every 12 hours.",
                    )
                } else {
                    uiText(
                        "手機與電腦需連接同一個 Wi-Fi。輸入電腦區網網址與 .env 中的 API Token。",
                        "Connect the phone and PC to the same network, then enter the server URL and API token from .env.",
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isConfigured) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("伺服器網址", "Server URL")) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.replace("\r", "").replace("\n", "") },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = MaterialTheme.shapes.small,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = if (isConfigured) onSyncNow else {
                        { onConnect(url, token) }
                    },
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (isConfigured) {
                            uiText("立即同步", "Sync now")
                        } else {
                            uiText("連接並同步", "Connect and sync")
                        },
                    )
                }
                if (isConfigured) {
                    Button(
                        onClick = onDisable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(uiText("停用", "Disable"))
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoSyncCard(
    enabled: Boolean,
    onChooseFolder: () -> Unit,
    onSyncNow: () -> Unit,
    onDisable: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        uiText("資料夾自動同步", "Folder auto-sync"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (enabled) {
                            uiText(
                                "已啟用，每 12 小時檢查最新 CSV",
                                "Enabled. Checks for a new CSV every 12 hours.",
                            )
                        } else {
                            uiText("尚未連接資料夾", "No folder connected")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (enabled) Icons.Outlined.Sync else Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                uiText(
                    "Android 不允許直接讀取 Firstrade 或永豐 App 的私有資料。請將官方匯出的 CSV 存入你授權的資料夾，App 會自動讀取最新檔案。",
                    "Android cannot read private data from Firstrade or SinoPac apps. Save an official CSV in an authorized folder and the app will import the newest file.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = if (enabled) onSyncNow else onChooseFolder) {
                    Icon(
                        imageVector = if (enabled) Icons.Outlined.Sync else Icons.Outlined.FolderOpen,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (enabled) uiText("立即同步", "Sync now")
                        else uiText("選擇資料夾", "Choose folder"),
                    )
                }
                if (enabled) {
                    Button(
                        onClick = onChooseFolder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) {
                        Text(uiText("更換", "Change"))
                    }
                    Button(
                        onClick = onDisable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(uiText("停用", "Disable"))
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalAssetOverviewCard(
    summary: PortfolioSummary,
    usdToTwd: Double,
    rateSource: String,
    rateUpdatedAt: String?,
    displayCurrency: Currency,
    onShowTrend: () -> Unit,
) {
    val multiplier = if (displayCurrency == Currency.TWD) 1.0 else 1 / usdToTwd
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Text(
                text = uiText("總資產淨值", "TOTAL BALANCE"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = (summary.totalValueTwd * multiplier).asMoney(displayCurrency),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric(
                    label = uiText("未實現損益", "Unrealized P/L"),
                    value = (summary.unrealizedProfitTwd * multiplier)
                        .asSignedMoney(displayCurrency),
                )
                Metric(label = uiText("報酬率", "Return"), value = summary.returnRate.asPercent())
                Metric(label = "USD/TWD", value = "%.3f".format(usdToTwd))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "${uiText("匯率", "FX rate")}: $rateSource"
                    + (rateUpdatedAt?.let { " · ${it.take(16).replace('T', ' ')} UTC" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onShowTrend,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ShowChart, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(uiText("查看總資產趨勢", "View net worth trend"))
            }
        }
    }
}

@Composable
private fun DistributionCard(
    assetAllocations: List<Allocation>,
    institutionAllocations: List<Allocation>,
    displayCurrency: Currency,
    usdToTwd: Double,
) {
    var view by rememberSaveable { mutableStateOf(DistributionView.ASSET) }
    val allocations = remember(view, assetAllocations, institutionAllocations) {
        val source = when (view) {
            DistributionView.ASSET -> assetAllocations
            DistributionView.INSTITUTION -> institutionAllocations
        }
        source.groupSmallAllocations()
    }
    val colors = allocationColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader(
                uiText("資產分布", "Allocation"),
                uiText("依顯示幣別計價", "In display currency"),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistributionToggle(
                    text = uiText("個別資產", "Assets"),
                    selected = view == DistributionView.ASSET,
                    onClick = { view = DistributionView.ASSET },
                )
                DistributionToggle(
                    text = uiText("金融機構", "Institutions"),
                    selected = view == DistributionView.INSTITUTION,
                    onClick = { view = DistributionView.INSTITUTION },
                )
            }
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AllocationPie(
                    allocations = allocations,
                    colors = colors,
                    modifier = Modifier.size(210.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                allocations.forEachIndexed { index, allocation ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(colors[index % colors.size], CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = allocationLabel(allocation.label),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                allocation.valueTwd
                                    .toDisplayCurrency(displayCurrency, usdToTwd)
                                    .asMoney(displayCurrency),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                allocation.ratio.asPercent(unsigned = true),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionToggle(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Text(text)
    }
}

@Composable
private fun AllocationPie(
    allocations: List<Allocation>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (allocations.isEmpty()) {
            drawCircle(color = Color.LightGray)
            return@Canvas
        }

        var startAngle = -90f
        allocations.forEachIndexed { index, allocation ->
            val sweep = (allocation.ratio * 360f).toFloat()
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
            )
            startAngle += sweep
        }
        allocations.dropLast(1).runningFold(-90f) { angle, allocation ->
            angle + (allocation.ratio * 360f).toFloat()
        }.drop(1).forEach { angle ->
            val radians = Math.toRadians(angle.toDouble())
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = center,
                end = Offset(
                    x = center.x + (kotlin.math.cos(radians) * radius).toFloat(),
                    y = center.y + (kotlin.math.sin(radians) * radius).toFloat(),
                ),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Composable
private fun HoldingRow(
    holding: Holding,
    marketSummary: MarketSummary?,
    displayCurrency: Currency,
    usdToTwd: Double,
    onShowChart: () -> Unit,
) {
    val multiplier = currencyMultiplier(holding.currency, displayCurrency, usdToTwd)
    val isMarketAsset = holding.assetType ==
        tw.kensuke.assetscope.domain.model.AssetType.STOCK ||
        holding.assetType == tw.kensuke.assetscope.domain.model.AssetType.ETF
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isMarketAsset, onClick = onShowChart),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        holding.symbol.take(3),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        holding.symbol,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = holding.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        uiText(
                            "${holding.quantity.asQuantity()} 股／單位",
                            "${holding.quantity.asQuantity()} shares / units",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isMarketAsset) {
                    MiniTrendChart(
                        values = marketSummary?.closes.orEmpty(),
                        positive = (marketSummary?.change ?: holding.unrealizedProfit) >= 0,
                        modifier = Modifier
                            .width(76.dp)
                            .height(48.dp)
                            .padding(horizontal = 8.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        ((marketSummary?.latestPrice ?: holding.marketPrice) * multiplier)
                            .asMoney(displayCurrency),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = marketSummary?.let {
                            "${(it.change * multiplier).asSignedMoney(displayCurrency)} " +
                                "(${it.changeRate.asPercent()})"
                        } ?: (holding.unrealizedProfit * multiplier)
                            .asSignedMoney(displayCurrency),
                        color = if ((marketSummary?.change ?: holding.unrealizedProfit) >= 0) {
                            marketUpColor
                        } else {
                            marketDownColor
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${uiText("均價", "Avg")} ${
                        (holding.averageCost * multiplier).asMoney(displayCurrency)
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${uiText("市值", "Value")} ${
                        (holding.marketValue * multiplier).asMoney(displayCurrency)
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    holding.returnRate.asPercent(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (holding.returnRate >= 0) profitColor else lossColor,
                )
            }
            if (isMarketAsset) {
                Spacer(Modifier.height(5.dp))
                Text(
                    uiText(
                        "點擊查看 K 線 · ${marketSummary?.source ?: "行情載入中"}",
                        "Tap for candlestick chart · ${
                            marketSummary?.source ?: "Loading quotes"
                        }",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun MiniTrendChart(
    values: List<Double>,
    positive: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (positive) marketUpColor else marketDownColor
    val placeholderColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        if (values.size < 2) {
            drawLine(
                color = placeholderColor,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.dp.toPx(),
            )
            return@Canvas
        }
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            val y = size.height - ((value - min) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "IMPORT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(uiText("匯入資產 CSV", "Import holdings CSV"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                uiText(
                    "支援標準化持倉資料。你也可以從其他 App 分享 CSV，或在檔案管理器直接用本 App 開啟。",
                    "Import normalized holdings data, share a CSV from another app, or open one from the file manager.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(uiText("選擇 CSV", "Choose CSV"))
            }
        }
    }
}

@Composable
private fun PaperTradingPage(
    dashboard: PaperTradingDashboard?,
    displayCurrency: Currency,
    usdToTwd: Double,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    uiText("AI 模擬交易實驗室", "AI paper trading lab"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    uiText(
                        "永久隔離實盤，只記錄虛擬成交與績效",
                        "Permanently isolated from live trading",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = uiText("更新", "Refresh"))
            }
        }
        if (dashboard == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Text(
                    uiText(
                        "連接電腦伺服器後即可啟動三個模擬交易機器人。",
                        "Connect to the PC server to start the three paper-trading bots.",
                    ),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            dashboard.bots.forEach { bot ->
                PaperBotCard(bot, displayCurrency, usdToTwd)
            }
        }
    }
}

@Composable
private fun PaperBotCard(
    bot: PaperBot,
    displayCurrency: Currency,
    usdToTwd: Double,
) {
    val multiplier = if (displayCurrency == Currency.TWD) 1.0 else 1 / usdToTwd
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(bot.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        bot.strategy,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    bot.returnRate.asPercent(),
                    color = if (bot.returnRate >= 0) profitColor else lossColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric(
                    uiText("淨值", "Net value"),
                    (bot.netValueTwd * multiplier).asMoney(displayCurrency),
                )
                Metric(
                    uiText("現金", "Cash"),
                    (bot.cashTwd * multiplier).asMoney(displayCurrency),
                )
                Metric(uiText("交易", "Trades"), bot.tradeCount.toString())
            }
            if (bot.positions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    uiText("虛擬持倉", "Paper positions"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                bot.positions.take(5).forEach { position ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${position.symbol} · ${position.name}", modifier = Modifier.weight(1f))
                        Text(
                            (position.marketValueTwd * multiplier).asMoney(displayCurrency),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            if (bot.recentTrades.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    uiText("最近交易", "Recent trades"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                bot.recentTrades.take(3).forEach { trade ->
                    Text(
                        "${trade.side} ${trade.symbol} · ${
                            (trade.amountTwd * multiplier).asMoney(displayCurrency)
                        }",
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (trade.side == "BUY") lossColor else profitColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            trailing,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, light: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (light) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (light) MaterialTheme.colorScheme.onPrimary else Color.Unspecified,
        )
    }
}

private fun Uri.readText(context: Context): String? =
    context.contentResolver.openInputStream(this)
        ?.bufferedReader()
        ?.use { it.readText() }

private val twdFormatter = NumberFormat.getCurrencyInstance(Locale.TAIWAN).apply {
    maximumFractionDigits = 0
}

private fun Double.asPercent(unsigned: Boolean = false): String =
    "${if (!unsigned && this >= 0) "+" else ""}%.1f%%".format(this * 100)
private fun Double.asMoney(currency: Currency): String = when (currency) {
    Currency.TWD -> twdFormatter.format(this)
    Currency.USD -> "US$${"%,.2f".format(this)}"
}
private fun Double.asSignedMoney(currency: Currency): String =
    "${if (this >= 0) "+" else ""}${asMoney(currency)}"
private fun Double.toDisplayCurrency(currency: Currency, usdToTwd: Double): Double =
    if (currency == Currency.TWD) this else this / usdToTwd
private fun currencyMultiplier(
    source: Currency,
    target: Currency,
    usdToTwd: Double,
): Double = when {
    source == target -> 1.0
    source == Currency.USD -> usdToTwd
    else -> 1 / usdToTwd
}
private fun Double.asQuantity(): String =
    if (this % 1.0 == 0.0) "%,.0f".format(this) else "%,.2f".format(this)

private fun Holding.isCurrencyHolding(): Boolean = symbol == "USD" || symbol == "TWD"

@Composable
private fun scrollHint(
    page: AppPage,
    visibleKey: Any?,
    regularHoldings: List<Holding>,
    currencyHoldings: List<Holding>,
): String {
    val language = LocalUiLanguage.current
    val key = visibleKey?.toString()
    if (page == AppPage.HOLDINGS) {
        val holding = (regularHoldings + currencyHoldings).firstOrNull { it.id == key }
        if (holding != null) return holding.symbol
    }
    return when (key) {
        "overview-total" -> if (language == UiLanguage.EN) "Net worth" else "總資產淨值"
        "overview-update" -> if (language == UiLanguage.EN) "App update" else "版本更新"
        "overview-performance" -> if (language == UiLanguage.EN) "Performance" else "投資績效"
        "overview-allocation" -> if (language == UiLanguage.EN) "Allocation" else "資產分布"
        "holdings-header" -> page.label(language)
        "currency-divider" -> if (language == UiLanguage.EN) "Cash" else "現金"
        "transactions-header" -> if (language == UiLanguage.EN) "Transactions" else "交易明細"
        "transactions-timeline" -> if (language == UiLanguage.EN) "Timeline" else "交易時間軸"
        "expenses-summary" -> if (language == UiLanguage.EN) "Expenses" else "日常花費"
        "expenses-empty" -> if (language == UiLanguage.EN) "No activity" else "尚無紀錄"
        "expenses-categories" ->
            if (language == UiLanguage.EN) "Spending categories" else "支出分類"
        "expenses-activity" -> if (language == UiLanguage.EN) "Card activity" else "刷卡明細"
        "sync-server" -> if (language == UiLanguage.EN) "PC server" else "電腦資產伺服器"
        "sync-folder" -> if (language == UiLanguage.EN) "Folder sync" else "資料夾自動同步"
        "sync-import" -> if (language == UiLanguage.EN) "CSV import" else "匯入資產 CSV"
        else -> page.label(language)
    }
}

private val Holding.returnRate: Double
    get() = if (cost == 0.0) 0.0 else unrealizedProfit / cost

private val profitColor = Color(0xFF9A5D50)
private val lossColor = Color(0xFF627066)
private val marketUpColor = Color(0xFF2F8F76)
private val marketDownColor = Color(0xFFC65D5D)
private val allocationColors = listOf(
    Color(0xFF6F7965),
    Color(0xFFB68468),
    Color(0xFF8D9A9A),
    Color(0xFFC0A879),
    Color(0xFF9B8790),
    Color(0xFF8FA08C),
    Color(0xFFC28D7A),
    Color(0xFF7F8998),
)

private enum class DistributionView {
    ASSET,
    INSTITUTION,
}

private enum class AppPage {
    OVERVIEW,
    HOLDINGS,
    TRANSACTIONS,
    EXPENSES,
    ROBOTS,
    SYNC;

    fun label(language: UiLanguage): String = when (this) {
        OVERVIEW -> if (language == UiLanguage.EN) "Overview" else "總覽"
        HOLDINGS -> if (language == UiLanguage.EN) "Holdings" else "持股"
        TRANSACTIONS -> if (language == UiLanguage.EN) "Trades" else "交易"
        EXPENSES -> if (language == UiLanguage.EN) "Expenses" else "消費"
        ROBOTS -> if (language == UiLanguage.EN) "Bots" else "機器人"
        SYNC -> if (language == UiLanguage.EN) "Sync" else "同步"
    }
}

private enum class TransactionFilter {
    ALL,
    FIRSTRADE,
    SINOPAC;

    fun label(language: UiLanguage): String = when (this) {
        ALL -> if (language == UiLanguage.EN) "All" else "全部"
        FIRSTRADE -> "Firstrade"
        SINOPAC -> if (language == UiLanguage.EN) "SinoPac" else "永豐"
    }
}

private fun List<Allocation>.groupSmallAllocations(maxSlices: Int = 7): List<Allocation> {
    if (size <= maxSlices) return this

    val visible = take(maxSlices - 1)
    val remainder = drop(maxSlices - 1)
    return visible + Allocation(
        label = "OTHER",
        valueTwd = remainder.sumOf(Allocation::valueTwd),
        ratio = remainder.sumOf(Allocation::ratio),
    )
}

@Composable
private fun Institution.localizedName(): String = when (this) {
    Institution.FIRSTRade -> "Firstrade"
    Institution.SINOPAC_SECURITIES -> uiText("永豐證券", "SinoPac Securities")
    Institution.SINOPAC_BANK -> uiText("永豐銀行", "SinoPac Bank")
}

@Composable
private fun TransactionType.localizedName(): String = when (this) {
    TransactionType.BUY -> uiText("買入", "Buy")
    TransactionType.SELL -> uiText("賣出", "Sell")
    TransactionType.DIVIDEND -> uiText("股息", "Dividend")
}

@Composable
private fun tw.kensuke.assetscope.domain.model.ExpenseCategory.localizedName(): String =
    expenseCategoryLabel(displayName)

@Composable
private fun expenseCategoryLabel(value: String): String = when (value) {
    "餐飲" -> uiText("餐飲", "Dining")
    "交通" -> uiText("交通", "Transport")
    "購物" -> uiText("購物", "Shopping")
    "日用品" -> uiText("日用品", "Groceries")
    "娛樂" -> uiText("娛樂", "Entertainment")
    "訂閱" -> uiText("訂閱", "Subscriptions")
    "旅遊" -> uiText("旅遊", "Travel")
    "醫療" -> uiText("醫療", "Health")
    "水電電信" -> uiText("水電電信", "Utilities")
    else -> uiText("其他", "Other")
}

@Composable
private fun allocationLabel(value: String): String = when (value) {
    "股票" -> uiText("股票", "Stocks")
    "ETF" -> "ETF"
    "現金" -> uiText("現金", "Cash")
    "存款" -> uiText("存款", "Deposits")
    "永豐證券" -> uiText("永豐證券", "SinoPac Securities")
    "永豐銀行" -> uiText("永豐銀行", "SinoPac Bank")
    "OTHER", "其他" -> uiText("其他", "Other")
    else -> value
}
