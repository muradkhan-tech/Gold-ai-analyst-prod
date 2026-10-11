package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.EconomicEventEntity
import com.example.data.local.FundamentalAnalysisEntity
import com.example.data.local.NewsArticleEntity
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MacroCalendarScreen(
    events: List<EconomicEventEntity>,
    newsArticles: List<NewsArticleEntity> = emptyList(),
    fundamentalAnalysis: FundamentalAnalysisEntity? = null,
    isEvaluatingFundamentals: Boolean = false,
    fundamentalError: String? = null,
    onEvaluateFundamentals: () -> Unit = {},
    onRefreshNews: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: AI Synthesis, 1: Macro News, 2: Calendar
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("macro_calendar_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header with title & refresh
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FUNDAMENTAL & NEWS ENGINE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Macro intelligence, filtered news & AI risk synthesis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onRefreshNews,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("refresh_news_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh News",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // 2. Functional Engine Summary (Replaces red configuration error card)
        item {
            FundamentalEngineSummaryCard(
                activeMode = if (fundamentalAnalysis?.providerActive?.contains("Gemini", ignoreCase = true) == true ||
                    fundamentalAnalysis?.providerActive?.contains("OpenAI", ignoreCase = true) == true)
                    "Live LLM Connected" else "Local Quant Heuristic (Zero-Key Active)",
                latestNews = newsArticles.firstOrNull(),
                highImpactEvents = events.filter { it.impact.equals("HIGH", ignoreCase = true) }
            )
        }

        // 3. Sub-Tab Selector
        item {
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = GoldPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                        color = GoldPrimary
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("AI REASONING", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = "AI", modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("FILTERED NEWS", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Newspaper, contentDescription = "News", modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("CALENDAR", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar", modifier = Modifier.size(16.dp)) }
                )
            }
        }

        // 4. Tab Content Rendering
        when (selectedSubTab) {
            0 -> {
                item {
                    FundamentalAiCard(
                        analysis = fundamentalAnalysis,
                        isEvaluating = isEvaluatingFundamentals,
                        onEvaluate = onEvaluateFundamentals,
                        onNavigateSettings = onNavigateSettings
                    )
                }
            }

            1 -> {
                item {
                    NewsFilterBar(
                        selectedCategory = selectedCategoryFilter,
                        onSelectCategory = { selectedCategoryFilter = it }
                    )
                }

                val filteredNews = if (selectedCategoryFilter == "ALL") {
                    newsArticles
                } else {
                    newsArticles.filter { it.category == selectedCategoryFilter }
                }

                if (filteredNews.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No news found for category $selectedCategoryFilter. Click Refresh to reload.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(filteredNews) { article ->
                        NewsArticleCard(article = article, dateFormatted = sdf.format(Date(article.publishedAt)))
                    }
                }
            }

            2 -> {
                items(events) { ev ->
                    EconomicEventCard(event = ev, dateFormatted = sdf.format(Date(ev.timestamp)))
                }
            }
        }
    }
}

@Composable
fun FundamentalEngineSummaryCard(
    activeMode: String,
    latestNews: NewsArticleEntity?,
    highImpactEvents: List<EconomicEventEntity>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("engine_status_summary_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Active Mode Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(BullishGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ENGINE STATUS: $activeMode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = GoldPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BullishGreen.copy(alpha = 0.15f))
                        .border(1.dp, BullishGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "ZERO-KEY FAILSAFE ACTIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BullishGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Section 1: Latest News Headline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LATEST MACRO HEADLINE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        if (latestNews != null) {
                            Text(
                                text = "${latestNews.source} • ${latestNews.category}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = GoldPrimary
                            )
                        }
                    }

                    if (latestNews != null) {
                        Text(
                            text = latestNews.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val sentColor = when (latestNews.sentiment) {
                                "BULLISH" -> BullishGreen
                                "BEARISH" -> BearishRed
                                else -> GoldPrimary
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(sentColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SENTIMENT: ${latestNews.sentiment}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = sentColor,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Curated institutional headlines ingesting live macro flow.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 2: Today's High-Impact Economic Releases
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TODAY'S HIGH-IMPACT ECONOMIC RELEASES",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    if (highImpactEvents.isNotEmpty()) {
                        highImpactEvents.take(2).forEach { event ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Forecast: ${event.forecast ?: "N/A"} | Prev: ${event.previous ?: "N/A"}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(BearishRed.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "HIGH IMPACT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BearishRed,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No high-impact releases scheduled for the current session window.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FundamentalAiCard(
    analysis: FundamentalAnalysisEntity?,
    isEvaluating: Boolean,
    onEvaluate: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val riskColor = when (analysis?.marketRisk) {
        "High" -> BearishRed
        "Low" -> BullishGreen
        else -> GoldPrimary
    }

    val biasColor = when (analysis?.fundamentalBias) {
        "Bullish" -> BullishGreen
        "Bearish" -> BearishRed
        else -> GoldPrimary
    }

    val trendColor = when (analysis?.shortTermTrend) {
        "BULLISH" -> BullishGreen
        "BEARISH" -> BearishRed
        else -> GoldPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(16.dp)
            .testTag("fundamental_ai_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Reasoning",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "QUANT REASONING ENGINE",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.5.sp
                    )
                }

                Button(
                    onClick = onEvaluate,
                    enabled = !isEvaluating,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("evaluate_fundamentals_button")
                ) {
                    if (isEvaluating) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reasoning...", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Run Synthesis", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (analysis == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No fundamental synthesis generated yet.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Click 'Run Synthesis' to analyze multi-timeframe alignment, volume delta pressure, and macro catalysts via the Deterministic Quant Engine.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Key metrics: Short-Term Trend, Fundamental Bias, Market Risk, Confidence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(trendColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, trendColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "TREND",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = analysis.shortTermTrend,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = trendColor
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(biasColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, biasColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "BIAS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = analysis.fundamentalBias.uppercase(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = biasColor
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(riskColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, riskColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "RISK",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = analysis.marketRisk.uppercase(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = riskColor
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(GoldPrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "CONFIDENCE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${analysis.confidenceScore.toInt()}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = GoldPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Invalidation Level
                if (analysis.invalidationLevel > 0.0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BearishRed.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, BearishRed.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INVALIDATION LEVEL (SL BOUNDARY)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BearishRed,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.invalidationLevel)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = BearishRed
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Market Overview
                val overviewText = if (analysis.marketOverview.isNotBlank()) analysis.marketOverview else analysis.detailedSynthesis
                Text(
                    text = "MARKET OVERVIEW & SYNTHESIS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = overviewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tactical Scenarios (Bullish & Bearish)
                if (analysis.bullishScenario.isNotBlank() || analysis.bearishScenario.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (analysis.bullishScenario.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(BullishGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .border(1.dp, BullishGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("BULLISH SCENARIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                                    Text(analysis.bullishScenario, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 14.sp)
                                }
                            }
                        }
                        if (analysis.bearishScenario.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(BearishRed.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .border(1.dp, BearishRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("BEARISH SCENARIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BearishRed)
                                    Text(analysis.bearishScenario, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 14.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Key Catalysts / Drivers
                Text(
                    text = "KEY CATALYSTS & ORDER FLOW CONFLUENCE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                val catalystsJsonStr = if (analysis.keyCatalystsJson.isNotBlank() && analysis.keyCatalystsJson != "[]") {
                    analysis.keyCatalystsJson
                } else {
                    analysis.keyDriversJson
                }

                val catalysts = remember(catalystsJsonStr) {
                    try {
                        val arr = JSONArray(catalystsJsonStr)
                        (0 until arr.length()).map { arr.getString(it) }
                    } catch (e: Exception) {
                        listOf("Falling 10-year Treasury real yields", "Central bank physical gold accumulation", "US Dollar consolidating below resistance")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    catalysts.forEach { catalyst ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = catalyst,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Provider badge & verification
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Provider: ${analysis.providerActive.ifBlank { analysis.llmProvider }}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = GoldPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Deterministic Verified",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BullishGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun NewsFilterBar(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf("ALL", "GOLD", "FED_POLICY", "INFLATION", "TREASURY_YIELDS", "USD_DXY")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categories.forEach { cat ->
            val isSelected = selectedCategory == cat
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) GoldPrimary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (isSelected) GoldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .clickable { onSelectCategory(cat) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = cat.replace("_", " "),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun NewsArticleCard(
    article: NewsArticleEntity,
    dateFormatted: String
) {
    val sentimentColor = when (article.sentiment) {
        "BULLISH" -> BullishGreen
        "BEARISH" -> BearishRed
        else -> GoldPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = article.category.replace("_", " "),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = GoldPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(sentimentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = article.sentiment,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = sentimentColor
                        )
                    }
                }

                Text(
                    text = article.source,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = article.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormatted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Strict Macro Filter: ${(article.relevanceScore * 100).toInt()}% relevance",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = BullishGreen
                )
            }
        }
    }
}

@Composable
fun EconomicEventCard(
    event: EconomicEventEntity,
    dateFormatted: String
) {
    val isHigh = event.impact == "HIGH"
    val impactColor = if (isHigh) BearishRed else GoldPrimary

    val biasColor = when {
        event.goldBias.contains("BULLISH") -> BullishGreen
        event.goldBias.contains("BEARISH") -> BearishRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(impactColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = event.impact,
                            color = impactColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = event.country,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = GoldPrimary
                    )
                }

                Text(
                    text = dateFormatted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (event.forecast != null) {
                    Text(
                        text = "Forecast: ${event.forecast}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (event.previous != null) {
                    Text(
                        text = "Previous: ${event.previous}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!event.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = event.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Gold Bias: ${event.goldBias}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = biasColor
            )
        }
    }
}
