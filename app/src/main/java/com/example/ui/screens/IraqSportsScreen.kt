package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MatchStatus
import com.example.model.NewsArticle
import com.example.model.SportsFilter
import com.example.model.SportsMatch
import com.example.ui.bayanString
import com.example.ui.components.IraqFlagBadge
import com.example.ui.components.NewsArticleCard
import com.example.ui.components.SportsMatchCard
import com.example.ui.theme.LocalBayanColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IraqSportsScreen(
    matches: List<SportsMatch>,
    sportsNews: List<NewsArticle>,
    selectedFilter: SportsFilter,
    isSportsLoading: Boolean,
    hasSportsError: Boolean,
    onSelectFilter: (SportsFilter) -> Unit,
    onRefreshSports: () -> Unit,
    onMatchClick: (SportsMatch) -> Unit,
    onArticleClick: (NewsArticle) -> Unit,
    onToggleSaveArticle: (NewsArticle) -> Unit,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current

    val displayedMatches = when (selectedFilter) {
        SportsFilter.ALL -> matches
        SportsFilter.SCHEDULE -> matches.filter {
            it.status == MatchStatus.SCHEDULED || it.status == MatchStatus.LIVE || it.status == MatchStatus.POSTPONED
        }.sortedBy { it.kickoffEpochMs }
        SportsFilter.RESULTS -> matches.filter {
            it.status == MatchStatus.FINISHED || it.status == MatchStatus.LIVE
        }.sortedByDescending { it.kickoffEpochMs }
    }

    PullToRefreshBox(
        isRefreshing = isSportsLoading && matches.isNotEmpty(),
        onRefresh = onRefreshSports,
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 620.dp

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .testTag("iraq_sports_list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 110.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero Iraqi Sports Banner
                item(key = "sports_hero_header") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = bayanColors.accentBlue.copy(alpha = 0.11f),
                        border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("iraq_sports_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IraqFlagBadge(
                                        width = 34.dp,
                                        height = 22.dp,
                                        cornerRadius = 6.dp
                                    )
                                    Column {
                                        Text(
                                            text = bayanString(R.string.sports_section_title),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = bayanString(R.string.sports_section_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = bayanColors.mutedText
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onRefreshSports,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .testTag("sports_refresh_button")
                                ) {
                                    if (isSportsLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = bayanColors.accentBlue
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = bayanString(R.string.action_refresh),
                                            tint = bayanColors.accentBlue
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InfoPill(
                                    icon = Icons.Rounded.Schedule,
                                    text = bayanString(R.string.sports_baghdad_time_note),
                                    accent = bayanColors.accentBlue
                                )
                            }

                            Text(
                                text = bayanString(R.string.sports_text_stats_only),
                                style = MaterialTheme.typography.labelSmall,
                                color = bayanColors.accentEmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Filter Segmented Switcher (All / Schedule / Final Results)
                item(key = "sports_filter_bar") {
                    Surface(
                        shape = CircleShape,
                        color = bayanColors.pillBackground,
                        border = BorderStroke(1.dp, bayanColors.glassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SportsFilterPill(
                                label = bayanString(R.string.sports_filter_all),
                                selected = selectedFilter == SportsFilter.ALL,
                                onClick = { onSelectFilter(SportsFilter.ALL) },
                                testTag = "sports_filter_all",
                                modifier = Modifier.weight(1f)
                            )
                            SportsFilterPill(
                                label = bayanString(R.string.sports_filter_schedule),
                                selected = selectedFilter == SportsFilter.SCHEDULE,
                                onClick = { onSelectFilter(SportsFilter.SCHEDULE) },
                                testTag = "sports_filter_schedule",
                                modifier = Modifier.weight(1f)
                            )
                            SportsFilterPill(
                                label = bayanString(R.string.sports_filter_results),
                                selected = selectedFilter == SportsFilter.RESULTS,
                                onClick = { onSelectFilter(SportsFilter.RESULTS) },
                                testTag = "sports_filter_results",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                when {
                    isSportsLoading && displayedMatches.isEmpty() -> {
                        item(key = "sports_loading_state") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, bayanColors.glassBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = bayanColors.accentBlue
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = bayanString(R.string.sports_loading_details),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = bayanColors.mutedText
                                    )
                                }
                            }
                        }
                    }

                    displayedMatches.isEmpty() -> {
                        item(key = "sports_empty_state") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, bayanColors.glassBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SportsSoccer,
                                        contentDescription = null,
                                        tint = bayanColors.accentBlue,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = when {
                                            hasSportsError -> bayanString(R.string.sports_error_load)
                                            selectedFilter == SportsFilter.SCHEDULE -> bayanString(R.string.sports_empty_schedule)
                                            selectedFilter == SportsFilter.RESULTS -> bayanString(R.string.sports_empty_results)
                                            else -> bayanString(R.string.sports_empty_all)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = bayanColors.mutedText,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(onClick = onRefreshSports, shape = CircleShape) {
                                        Text(text = bayanString(R.string.action_retry))
                                    }
                                }
                            }
                        }
                    }

                    isWide -> {
                        val pairs = displayedMatches.chunked(2)
                        itemsIndexed(
                            items = pairs,
                            key = { _, pair -> "pair_${pair.joinToString("_") { it.id }}" }
                        ) { _, pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { match ->
                                    SportsMatchCard(
                                        match = match,
                                        onMatchClick = onMatchClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    else -> {
                        items(
                            items = displayedMatches,
                            key = { it.id }
                        ) { match ->
                            SportsMatchCard(
                                match = match,
                                onMatchClick = onMatchClick
                            )
                        }
                    }
                }

                // Show latest Iraqi Sports News beneath matches in ALL view
                if (selectedFilter == SportsFilter.ALL && sportsNews.isNotEmpty()) {
                    item(key = "sports_news_divider_header") {
                        Text(
                            text = bayanString(R.string.sports_latest_news_header),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(
                        items = sportsNews.take(10),
                        key = { "sports_news_${it.id}" }
                    ) { article ->
                        NewsArticleCard(
                            article = article,
                            onArticleClick = { onArticleClick(article) },
                            onToggleSave = { onToggleSaveArticle(article) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accent: Color
) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SportsFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Color.White else bayanColors.mutedText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        )
    }
}
