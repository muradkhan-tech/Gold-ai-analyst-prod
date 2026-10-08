package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.GoldViewModel
import com.example.ui.TerminalTab
import com.example.quant.SilentLiquidityReport
import com.example.ui.components.ActiveSignalCard
import com.example.ui.components.BacktestScreen
import com.example.ui.components.CandlestickChart
import com.example.ui.components.GeminiChatScreen
import com.example.ui.components.InstitutionalFlowStatusWidget
import com.example.ui.components.MacroCalendarScreen
import com.example.ui.components.QuantSignalsScreen
import com.example.ui.components.SettingsScreen
import com.example.ui.components.TechnicalIndicatorsHud
import com.example.ui.components.TerminalTopBar
import com.example.ui.theme.GoldAiAnalystTheme
import com.example.ui.theme.GoldPrimary

class MainActivity : ComponentActivity() {

    private val viewModel: GoldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            GoldAiAnalystTheme(darkTheme = isDarkTheme) {
                GoldAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun GoldAppContent(viewModel: GoldViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val latestTick by viewModel.latestTick.collectAsStateWithLifecycle()
    val candles by viewModel.candles.collectAsStateWithLifecycle()
    val selectedTf by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
    val signals by viewModel.signals.collectAsStateWithLifecycle()
    val events by viewModel.economicEvents.collectAsStateWithLifecycle()
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    val useHighThinking by viewModel.useHighThinking.collectAsStateWithLifecycle()
    val backtestResult by viewModel.backtestResult.collectAsStateWithLifecycle()
    val systemHealthState by viewModel.systemHealthState.collectAsStateWithLifecycle()
    val activeProviderName by viewModel.activeProviderName.collectAsStateWithLifecycle()
    val validationReport by viewModel.lastValidationReport.collectAsStateWithLifecycle()
    val tradingViewAlerts by viewModel.tradingViewAlerts.collectAsStateWithLifecycle()
    val multiTimeframeAlignment by viewModel.multiTimeframeAlignment.collectAsStateWithLifecycle()
    val newsArticles by viewModel.newsArticles.collectAsStateWithLifecycle()
    val latestFundamentalAnalysis by viewModel.latestFundamentalAnalysis.collectAsStateWithLifecycle()
    val isEvaluatingFundamentals by viewModel.isEvaluatingFundamentals.collectAsStateWithLifecycle()
    val fundamentalError by viewModel.fundamentalError.collectAsStateWithLifecycle()
    val backtestOutcomes by viewModel.backtestOutcomes.collectAsStateWithLifecycle()
    val priceAlerts by viewModel.priceAlerts.collectAsStateWithLifecycle()
    val isAlertSoundEnabled by viewModel.isAlertSoundEnabled.collectAsStateWithLifecycle()
    val silentLiquidityReport by viewModel.silentLiquidityReport.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val adminDevMessages by viewModel.adminDevChatMessages.collectAsStateWithLifecycle()
    val isAdminDevThinking by viewModel.isAdminDevThinking.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            TerminalTopBar(
                tick = latestTick,
                isDarkTheme = isDarkTheme,
                onToggleTheme = { viewModel.toggleTheme() },
                systemHealthState = systemHealthState,
                onNavigateSettings = { viewModel.selectTab(TerminalTab.SETTINGS) }
            )
        },
        bottomBar = {
            TerminalBottomNav(
                currentTab = currentTab,
                onSelectTab = { viewModel.selectTab(it) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp) // Responsive tablet & desktop boundary
            ) {
                when (currentTab) {
                    TerminalTab.TERMINAL -> {
                        TerminalOverview(
                            candles = candles,
                            selectedTimeframe = selectedTf,
                            onSelectTimeframe = { viewModel.selectTimeframe(it) },
                            snapshot = viewModel.getTechnicalSnapshot(),
                            latestSignal = signals.firstOrNull(),
                            silentLiquidityReport = silentLiquidityReport
                        )
                    }

                    TerminalTab.SIGNALS -> {
                        QuantSignalsScreen(
                            signals = signals,
                            alignmentReport = multiTimeframeAlignment,
                            silentLiquidityReport = silentLiquidityReport,
                            tradingViewAlerts = tradingViewAlerts,
                            priceAlerts = priceAlerts,
                            currentBid = latestTick?.bid ?: 2864.38,
                            currentAsk = latestTick?.ask ?: 2864.63,
                            onRefreshSignals = { viewModel.refreshQuantSignals() },
                            onSimulateTradingViewAlert = { viewModel.simulateTradingViewAlert() },
                            onCreatePriceAlert = { target, cond, note -> viewModel.createPriceAlert(target, cond, note) },
                            onDeletePriceAlert = { id -> viewModel.deletePriceAlert(id) },
                            onTogglePriceAlert = { id, enabled -> viewModel.togglePriceAlert(id, enabled) }
                        )
                    }

                    TerminalTab.GEMINI_AI -> {
                        GeminiChatScreen(
                            messages = chatMessages,
                            isAiThinking = isAiThinking,
                            useHighThinking = useHighThinking,
                            onToggleHighThinking = { viewModel.toggleHighThinking() },
                            onSendMessage = { viewModel.sendChatMessage(it) }
                        )
                    }

                    TerminalTab.BACKTEST -> {
                        BacktestScreen(
                            summary = backtestResult,
                            savedOutcomes = backtestOutcomes,
                            onRunBacktest = { viewModel.runBacktest(it) }
                        )
                    }

                    TerminalTab.CALENDAR -> {
                        MacroCalendarScreen(
                            events = events,
                            newsArticles = newsArticles,
                            fundamentalAnalysis = latestFundamentalAnalysis,
                            isEvaluatingFundamentals = isEvaluatingFundamentals,
                            fundamentalError = fundamentalError,
                            onEvaluateFundamentals = { viewModel.evaluateFundamentals() },
                            onRefreshNews = { viewModel.refreshNews() },
                            onDismissError = { viewModel.dismissFundamentalError() },
                            onNavigateSettings = { viewModel.selectTab(TerminalTab.SETTINGS) }
                        )
                    }

                    TerminalTab.SETTINGS -> {
                        SettingsScreen(
                            apiKeys = apiKeys,
                            onSaveApiKey = { k, v -> viewModel.saveApiKey(k, v) },
                            systemHealthState = systemHealthState,
                            activeProviderName = activeProviderName,
                            validationReport = validationReport,
                            onTriggerPoll = { viewModel.pollMarketDataNow() },
                            isAlertSoundEnabled = isAlertSoundEnabled,
                            onToggleAlertSound = { viewModel.setAlertSoundEnabled(it) },
                            currentUser = currentUser,
                            adminDevMessages = adminDevMessages,
                            isAdminDevThinking = isAdminDevThinking,
                            onSendAdminDevMessage = { viewModel.sendAdminDevChatMessage(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalOverview(
    candles: List<com.example.data.local.CandleEntity>,
    selectedTimeframe: String,
    onSelectTimeframe: (String) -> Unit,
    snapshot: com.example.quant.TechnicalSnapshot,
    latestSignal: com.example.data.local.SignalEntity?,
    silentLiquidityReport: SilentLiquidityReport? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("terminal_overview_screen")
    ) {
        // Interactive Candlestick Chart
        CandlestickChart(
            candles = candles,
            selectedTimeframe = selectedTimeframe,
            onSelectTimeframe = onSelectTimeframe
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Institutional Flow Status Widget (Silent Liquidity Engine)
        if (silentLiquidityReport != null) {
            InstitutionalFlowStatusWidget(report = silentLiquidityReport)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Technical Indicators HUD
        TechnicalIndicatorsHud(snapshot = snapshot)

        Spacer(modifier = Modifier.height(16.dp))

        // Active Quant Signal Quick Preview Card
        if (latestSignal != null) {
            ActiveSignalCard(signal = latestSignal)
        }
    }
}

data class NavTabItem(
    val tab: TerminalTab,
    val title: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun TerminalBottomNav(
    currentTab: TerminalTab,
    onSelectTab: (TerminalTab) -> Unit
) {
    val items = listOf(
        NavTabItem(TerminalTab.TERMINAL, "Terminal", Icons.Default.CandlestickChart, "nav_terminal"),
        NavTabItem(TerminalTab.SIGNALS, "Signals", Icons.AutoMirrored.Filled.TrendingUp, "nav_signals"),
        NavTabItem(TerminalTab.GEMINI_AI, "Gemini AI", Icons.Default.Psychology, "nav_gemini"),
        NavTabItem(TerminalTab.CALENDAR, "Calendar", Icons.Default.CalendarMonth, "nav_calendar"),
        NavTabItem(TerminalTab.BACKTEST, "Backtest", Icons.Default.QueryStats, "nav_backtest"),
        NavTabItem(TerminalTab.SETTINGS, "Settings", Icons.Default.Settings, "nav_settings")
    )

    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            .testTag("terminal_bottom_nav"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        items.forEach { item ->
            val isSelected = currentTab == item.tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectTab(item.tab) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GoldPrimary,
                    selectedTextColor = GoldPrimary,
                    indicatorColor = GoldPrimary.copy(alpha = 0.15f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(item.tag)
            )
        }
    }
}
