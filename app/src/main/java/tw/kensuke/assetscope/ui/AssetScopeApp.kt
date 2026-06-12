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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.PerformanceSummary
import tw.kensuke.assetscope.domain.model.PriceHistory
import tw.kensuke.assetscope.domain.model.PortfolioSummary
import tw.kensuke.assetscope.domain.model.Transaction
import tw.kensuke.assetscope.domain.model.TransactionType
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
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { availableUpdate = it }
                .onFailure { updateMessage = it.message }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(
                            text = "ASSET SCOPE",
                            style = MaterialTheme.typography.titleMedium,
                            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                        )
                        Text(
                            text = "暮らしと資産 · 跨境資產總覽",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::resetSampleData) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "還原範例資料")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                top = 12.dp,
                end = 18.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { TotalAssetCard(state.summary, state.rates.usdToTwd) }
            availableUpdate?.let { update ->
                item {
                    AppUpdateCard(
                        update = update,
                        isDownloading = updateDownloadId != null,
                        onDownload = {
                            coroutineScope.launch {
                                runCatching { updateManager.download(update) }
                                    .onSuccess {
                                        updateDownloadId = it
                                        updateMessage = "已開始下載 ${update.versionName}"
                                    }
                                    .onFailure {
                                        updateMessage = it.message ?: "無法下載更新"
                                    }
                            }
                        },
                    )
                }
            }
            if (state.transactions.isNotEmpty()) {
                item { PerformanceCard(state.performance) }
            }
            item {
                ServerSyncCard(
                    configuredUrl = state.serverUrl,
                    defaultUrl = "http://192.168.0.102:8787",
                    onConnect = viewModel::configureServer,
                    onSyncNow = viewModel::syncServerNow,
                    onDisable = viewModel::disableServerSync,
                )
            }
            item {
                DistributionCard(
                    assetAllocations = state.summary.assetAllocations,
                    institutionAllocations = state.summary.institutionAllocations,
                )
            }
            item {
                SectionHeader(
                    title = "持倉明細",
                    trailing = "${state.holdings.size} 項資產",
                )
            }
            items(state.holdings, key = Holding::id) { holding ->
                HoldingRow(
                    holding = holding,
                    onShowChart = {
                        chartHolding = holding
                        chartHistory = null
                        chartError = null
                        chartLoading = true
                        coroutineScope.launch {
                            runCatching { repository.loadPriceHistory(holding) }
                                .onSuccess { chartHistory = it }
                                .onFailure {
                                    chartError = it.message ?: "無法載入歷史行情"
                                }
                            chartLoading = false
                        }
                    },
                )
            }
            if (state.transactions.isNotEmpty()) {
                item {
                    TransactionHistoryCard(
                        transactions = state.transactions.take(20),
                        totalCount = state.transactions.size,
                    )
                }
            }
            item {
                AutoSyncCard(
                    enabled = state.autoSyncFolder != null,
                    onChooseFolder = { folderLauncher.launch(null) },
                    onSyncNow = viewModel::syncNow,
                    onDisable = viewModel::disableAutoSync,
                )
            }
            item {
                ImportCard(
                    onImport = { csvLauncher.launch(arrayOf("text/*", "text/csv")) },
                )
            }
        }
    }

    chartHolding?.let { holding ->
        StockChartDialog(
            holding = holding,
            history = chartHistory,
            loading = chartLoading,
            error = chartError,
            onDismiss = {
                chartHolding = null
                chartHistory = null
                chartError = null
            },
        )
    }
}

@Composable
private fun AppUpdateCard(
    update: AppUpdate,
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
                        "新版本 ${update.versionName}",
                        style = MaterialTheme.typography.titleMedium,
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
                Text(if (isDownloading) "下載中" else "下載並安裝")
            }
        }
    }
}

@Composable
private fun PerformanceCard(performance: PerformanceSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "PERFORMANCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(4.dp))
            SectionHeader("投資績效", performance.returnRate.asPercent())
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PerformanceMetric(
                    label = "已實現損益",
                    value = performance.realizedProfit.asSignedUsd(),
                    modifier = Modifier.weight(1f),
                )
                PerformanceMetric(
                    label = "未實現損益",
                    value = performance.unrealizedProfit.asSignedUsd(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PerformanceMetric(
                    label = "股息收入",
                    value = performance.dividendIncome.asUsd(),
                    modifier = Modifier.weight(1f),
                )
                PerformanceMetric(
                    label = "總投資報酬",
                    value = performance.totalReturn.asSignedUsd(),
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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "ACTIVITY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            SectionHeader("交易時間軸", "最近 ${transactions.size}／$totalCount 筆")
            Spacer(Modifier.height(12.dp))
            transactions.forEachIndexed { index, transaction ->
                TransactionRow(transaction)
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
private fun TransactionRow(transaction: Transaction) {
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
                transaction.transactionType.displayName,
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
                when (transaction.transactionType) {
                    TransactionType.DIVIDEND -> transaction.name
                    else -> "${transaction.quantity.asQuantity()} × ${transaction.price.asUsd()}"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                transaction.amount.asSignedUsd(),
                fontWeight = FontWeight.Medium,
            )
            if (transaction.transactionType == TransactionType.SELL) {
                Text(
                    "損益 ${transaction.realizedProfit.asSignedUsd()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (transaction.realizedProfit >= 0) profitColor else lossColor,
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
                        "PC SERVER",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("電腦資產伺服器", style = MaterialTheme.typography.titleMedium)
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
                    "已連接 $configuredUrl，每 12 小時自動同步。"
                } else {
                    "手機與電腦需連接同一個 Wi-Fi。輸入電腦區網網址與 .env 中的 API Token。"
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
                    label = { Text("伺服器網址") },
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
                    Text(if (isConfigured) "立即同步" else "連接並同步")
                }
                if (isConfigured) {
                    Button(
                        onClick = onDisable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text("停用")
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
                        "AUTO SYNC",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("資料夾自動同步", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "已啟用，每 12 小時檢查最新 CSV" else "尚未連接資料夾",
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
                "Android 不允許直接讀取 Firstrade 或永豐 App 的私有資料。請將官方匯出的 CSV 存入你授權的資料夾，AssetScope 會自動讀取最新檔案。",
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
                    Text(if (enabled) "立即同步" else "選擇資料夾")
                }
                if (enabled) {
                    Button(
                        onClick = onChooseFolder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) {
                        Text("更換")
                    }
                    Button(
                        onClick = onDisable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text("停用")
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalAssetCard(summary: PortfolioSummary, usdToTwd: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
            Text(
                text = "TOTAL BALANCE  /  總資產淨值",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = summary.totalValueTwd.asTwd(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric(
                    label = "未實現損益",
                    value = summary.unrealizedProfitTwd.asSignedTwd(),
                    light = false,
                )
                Metric(
                    label = "報酬率",
                    value = summary.returnRate.asPercent(),
                    light = false,
                )
                Metric(
                    label = "USD/TWD",
                    value = "%.2f".format(usdToTwd),
                    light = false,
                )
            }
        }
    }
}

@Composable
private fun DistributionCard(
    assetAllocations: List<Allocation>,
    institutionAllocations: List<Allocation>,
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
            SectionHeader("資產分布", "以新台幣計價")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistributionToggle(
                    text = "個別資產",
                    selected = view == DistributionView.ASSET,
                    onClick = { view = DistributionView.ASSET },
                )
                DistributionToggle(
                    text = "金融機構",
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
                            text = allocation.label,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                allocation.valueTwd.asTwd(),
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
    onShowChart: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(holding.symbol, fontWeight = FontWeight.Bold)
                    Text(
                        text = holding.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(holding.marketValue.asMoney(holding.currency), fontWeight = FontWeight.Bold)
                    Text(
                        text = holding.unrealizedProfit.asSignedMoney(holding.currency),
                        color = if (holding.unrealizedProfit >= 0) profitColor else lossColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "均價 ${holding.averageCost.asMoney(holding.currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "估價 ${holding.marketPrice.asMoney(holding.currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    holding.returnRate.asPercent(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (holding.returnRate >= 0) profitColor else lossColor,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${holding.institution.displayName} · ${holding.accountName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${holding.quantity.asQuantity()} 股／單位",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (holding.assetType == tw.kensuke.assetscope.domain.model.AssetType.STOCK ||
                holding.assetType == tw.kensuke.assetscope.domain.model.AssetType.ETF
            ) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onShowChart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("查看 K 線與趨勢")
                }
            }
        }
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
            Text("匯入資產 CSV", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "支援標準化持倉資料。你也可以從其他 App 分享 CSV，或在檔案管理器直接用 AssetScope 開啟。",
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
                Text("選擇 CSV")
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

private fun Double.asTwd(): String = twdFormatter.format(this)
private fun Double.asSignedTwd(): String = "${if (this >= 0) "+" else ""}${asTwd()}"
private fun Double.asPercent(unsigned: Boolean = false): String =
    "${if (!unsigned && this >= 0) "+" else ""}%.1f%%".format(this * 100)
private fun Double.asMoney(currency: Currency): String = when (currency) {
    Currency.TWD -> twdFormatter.format(this)
    Currency.USD -> "US$${"%,.2f".format(this)}"
}
private fun Double.asUsd(): String = "US$${"%,.2f".format(this)}"
private fun Double.asSignedUsd(): String = "${if (this >= 0) "+" else ""}${asUsd()}"
private fun Double.asSignedMoney(currency: Currency): String =
    "${if (this >= 0) "+" else ""}${asMoney(currency)}"
private fun Double.asQuantity(): String =
    if (this % 1.0 == 0.0) "%,.0f".format(this) else "%,.2f".format(this)

private val Holding.returnRate: Double
    get() = if (cost == 0.0) 0.0 else unrealizedProfit / cost

private val profitColor = Color(0xFF9A5D50)
private val lossColor = Color(0xFF627066)
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

private fun List<Allocation>.groupSmallAllocations(maxSlices: Int = 7): List<Allocation> {
    if (size <= maxSlices) return this

    val visible = take(maxSlices - 1)
    val remainder = drop(maxSlices - 1)
    return visible + Allocation(
        label = "其他",
        valueTwd = remainder.sumOf(Allocation::valueTwd),
        ratio = remainder.sumOf(Allocation::ratio),
    )
}
