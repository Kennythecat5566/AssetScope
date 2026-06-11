package tw.kensuke.assetscope.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.kensuke.assetscope.data.PortfolioRepository
import tw.kensuke.assetscope.domain.model.Allocation
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.PortfolioSummary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetScopeApp(repository: PortfolioRepository) {
    val viewModel: PortfolioViewModel = viewModel(factory = PortfolioViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.readText(context)?.let(viewModel::importCsv)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AssetScope", fontWeight = FontWeight.Bold)
                        Text(
                            text = "跨境資產總覽",
                            style = MaterialTheme.typography.labelMedium,
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TotalAssetCard(state.summary, state.rates.usdToTwd) }
            item { AllocationCard(state.summary.allocations) }
            item {
                SectionHeader(
                    title = "持倉明細",
                    trailing = "${state.holdings.size} 項資產",
                )
            }
            items(state.holdings, key = Holding::id) { holding ->
                HoldingRow(holding)
            }
            item {
                ImportCard(
                    onImport = { csvLauncher.launch(arrayOf("text/*", "text/csv")) },
                )
            }
        }
    }
}

@Composable
private fun TotalAssetCard(summary: PortfolioSummary, usdToTwd: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "總資產淨值",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = summary.totalValueTwd.asTwd(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric(
                    label = "未實現損益",
                    value = summary.unrealizedProfitTwd.asSignedTwd(),
                    light = true,
                )
                Metric(
                    label = "報酬率",
                    value = summary.returnRate.asPercent(),
                    light = true,
                )
                Metric(
                    label = "USD/TWD",
                    value = "%.2f".format(usdToTwd),
                    light = true,
                )
            }
        }
    }
}

@Composable
private fun AllocationCard(allocations: List<Allocation>) {
    val colors = allocationColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader("機構配置", "以新台幣計價")
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AllocationRing(
                    allocations = allocations,
                    colors = colors,
                    modifier = Modifier.size(112.dp),
                )
                Spacer(Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    allocations.forEachIndexed { index, allocation ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(colors[index % colors.size], CircleShape),
                            )
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(allocation.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    allocation.ratio.asPercent(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllocationRing(
    allocations: List<Allocation>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        var startAngle = -90f
        allocations.forEachIndexed { index, allocation ->
            val sweep = (allocation.ratio * 360f).toFloat()
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(12.dp.toPx(), 12.dp.toPx()),
                size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()),
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun HoldingRow(holding: Holding) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
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
        }
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("匯入資產 CSV", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "支援 Firstrade、永豐證券與永豐銀行的標準化持倉資料。匯入會取代目前資料。",
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
private fun Double.asPercent(): String = "${if (this >= 0) "+" else ""}%.1f%%".format(this * 100)
private fun Double.asMoney(currency: Currency): String = when (currency) {
    Currency.TWD -> twdFormatter.format(this)
    Currency.USD -> "US$${"%,.2f".format(this)}"
}
private fun Double.asSignedMoney(currency: Currency): String =
    "${if (this >= 0) "+" else ""}${asMoney(currency)}"
private fun Double.asQuantity(): String =
    if (this % 1.0 == 0.0) "%,.0f".format(this) else "%,.2f".format(this)

private val profitColor = Color(0xFFB3261E)
private val lossColor = Color(0xFF126B45)
private val allocationColors = listOf(
    Color(0xFF2F6B4F),
    Color(0xFFE0A53B),
    Color(0xFF718FA4),
)

