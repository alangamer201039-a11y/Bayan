package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BayanTab
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.ProvinceWeather
import com.example.ui.bayanString
import com.example.ui.components.BreakingPulseBadge
import com.example.ui.components.FeaturedHeroCard
import com.example.ui.components.NewsArticleCard
import com.example.ui.components.WeatherWidget
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalBayanColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeedScreen(
    activeTab: BayanTab,
    articles: List<NewsArticle>,
    isLoading: Boolean,
    hasLoadError: Boolean,
    searchQuery: String,
    onRefresh: () -> Unit,
    onArticleClick: (NewsArticle) -> Unit,
    onToggleSave: (NewsArticle) -> Unit,
    modifier: Modifier = Modifier,
    selectedCategory: NewsCategory = NewsCategory.ALL,
    weather: ProvinceWeather? = null,
    selectedProvince: String = "صلاح الدين",
    isWeatherLoading: Boolean = false,
    isDetectingLocation: Boolean = false,
    onLocateGranted: () -> Unit = {},
    onLocationPermissionDenied: () -> Unit = {},
    onRefreshWeather: () -> Unit = {},
    onProvinceClick: () -> Unit = {}
) {
    val bayanColors = LocalBayanColors.current

    PullToRefreshBox(
        isRefreshing = isLoading && articles.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 620.dp

            when {
                isLoading && articles.isEmpty() -> {
                    LoadingSkeletonList(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize()
                    )
                }

                hasLoadError && articles.isEmpty() && activeTab != BayanTab.SAVED -> {
                    EmptyOrErrorState(
                        title = bayanString(R.string.error_load_title),
                        subtitle = bayanString(R.string.error_load_subtitle),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = bayanColors.accentEmber,
                                modifier = Modifier.size(48.dp)
                            )
                        },
                        actionButton = {
                            Button(
                                onClick = onRefresh,
                                shape = CircleShape,
                                modifier = Modifier.testTag("retry_load_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(text = bayanString(R.string.action_retry))
                            }
                        }
                    )
                }

                articles.isEmpty() -> {
                    when {
                        searchQuery.isNotBlank() -> {
                            EmptyOrErrorState(
                                title = bayanString(R.string.empty_search_title),
                                subtitle = bayanString(R.string.empty_search_subtitle),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.SearchOff,
                                        contentDescription = null,
                                        tint = bayanColors.accentBlue,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            )
                        }

                        activeTab == BayanTab.SAVED -> {
                            EmptySavedIllustrationState()
                        }

                        activeTab == BayanTab.BREAKING -> {
                            EmptyOrErrorState(
                                title = bayanString(R.string.empty_breaking_title),
                                subtitle = bayanString(R.string.empty_breaking_subtitle),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = bayanColors.accentEmber,
                                        modifier = Modifier.size(48.dp)
                                    )
                                },
                                actionButton = {
                                    Button(onClick = onRefresh, shape = CircleShape) {
                                        Text(text = bayanString(R.string.action_refresh))
                                    }
                                }
                            )
                        }

                        else -> {
                            EmptyOrErrorState(
                                title = bayanString(R.string.empty_category_title),
                                subtitle = bayanString(R.string.empty_category_subtitle),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Newspaper,
                                        contentDescription = null,
                                        tint = bayanColors.accentBlue,
                                        modifier = Modifier.size(48.dp)
                                    )
                                },
                                actionButton = {
                                    Button(
                                        onClick = onRefresh,
                                        shape = CircleShape,
                                        modifier = Modifier.testTag("empty_category_refresh_button")
                                    ) {
                                        Text(text = bayanString(R.string.action_refresh))
                                    }
                                }
                            )
                        }
                    }
                }

                else -> {
                    val remainingArticles = if (activeTab == BayanTab.HOME && searchQuery.isBlank() && articles.isNotEmpty()) {
                        articles.drop(1)
                    } else {
                        articles
                    }

                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 14.dp,
                            bottom = 110.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (activeTab == BayanTab.HOME && searchQuery.isBlank()) {
                            item(key = "home_weather_widget") {
                                WeatherWidget(
                                    weather = weather,
                                    selectedProvince = selectedProvince,
                                    isWeatherLoading = isWeatherLoading,
                                    isDetectingLocation = isDetectingLocation,
                                    onLocateGranted = onLocateGranted,
                                    onLocationPermissionDenied = onLocationPermissionDenied,
                                    onRefreshWeather = onRefreshWeather,
                                    onProvinceClick = onProvinceClick
                                )
                            }
                        }

                        if (activeTab == BayanTab.BREAKING) {
                            item(key = "breaking_banner") {
                                BreakingHeaderBanner(count = articles.size)
                            }
                        } else if (activeTab == BayanTab.HOME && searchQuery.isBlank() &&
                            (selectedCategory == NewsCategory.HOLIDAYS ||
                                selectedCategory == NewsCategory.DECISIONS_PROD ||
                                selectedCategory == NewsCategory.GOVERNORATES)
                        ) {
                            item(key = "category_banner_${selectedCategory.id}") {
                                OfficialCategoryBanner(
                                    category = selectedCategory,
                                    count = articles.size
                                )
                            }
                        }

                        if (activeTab == BayanTab.HOME && searchQuery.isBlank() && articles.isNotEmpty()) {
                            val lead = articles.first()
                            item(key = "featured_${lead.id}") {
                                FeaturedHeroCard(
                                    article = lead,
                                    onArticleClick = { onArticleClick(lead) },
                                    onToggleSave = { onToggleSave(lead) }
                                )
                            }
                        }

                        if (isWide) {
                            val pairs = remainingArticles.chunked(2)
                            itemsIndexed(
                                items = pairs,
                                key = { _, pair -> pair.joinToString("_") { it.id } }
                            ) { _, pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    pair.forEach { article ->
                                        NewsArticleCard(
                                            article = article,
                                            onArticleClick = { onArticleClick(article) },
                                            onToggleSave = { onToggleSave(article) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = remainingArticles,
                                key = { _, item -> item.id }
                            ) { _, article ->
                                NewsArticleCard(
                                    article = article,
                                    onArticleClick = { onArticleClick(article) },
                                    onToggleSave = { onToggleSave(article) }
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
private fun BreakingHeaderBanner(count: Int) {
    val bayanColors = LocalBayanColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bayanColors.accentEmber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, bayanColors.accentEmber.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = bayanString(R.string.breaking_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = bayanString(R.string.breaking_banner_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = bayanColors.mutedText
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BreakingPulseBadge()
                Text(
                    text = bayanString(R.string.count_news_format, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = bayanColors.mutedText
                )
            }
        }
    }
}

@Composable
private fun OfficialCategoryBanner(
    category: NewsCategory,
    count: Int
) {
    val bayanColors = LocalBayanColors.current
    val (titleRes, subtitleRes, icon, accent) = when (category) {
        NewsCategory.HOLIDAYS -> Quadruple(
            R.string.banner_holidays_title,
            R.string.banner_holidays_subtitle,
            Icons.Rounded.EventAvailable,
            EmeraldLive
        )
        NewsCategory.DECISIONS_PROD -> Quadruple(
            R.string.banner_decisions_title,
            R.string.banner_decisions_subtitle,
            Icons.Rounded.AccountBalance,
            bayanColors.accentBlue
        )
        else -> Quadruple(
            R.string.banner_governorates_title,
            R.string.banner_governorates_subtitle,
            Icons.Rounded.LocationCity,
            bayanColors.accentEmber
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_banner_${category.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = bayanString(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = bayanString(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = bayanColors.mutedText
                )
            }
            Surface(
                shape = CircleShape,
                color = bayanColors.pillBackground,
                border = BorderStroke(1.dp, bayanColors.glassBorder)
            ) {
                Text(
                    text = bayanString(R.string.count_news_format, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
private fun EmptySavedIllustrationState() {
    val bayanColors = LocalBayanColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, bayanColors.glassBorder),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .height(190.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_bookmarks),
                contentDescription = bayanString(R.string.cd_empty_saved),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = bayanString(R.string.empty_saved_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = bayanString(R.string.empty_saved_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = bayanColors.mutedText,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyOrErrorState(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    actionButton: (@Composable () -> Unit)? = null
) {
    val bayanColors = LocalBayanColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = bayanColors.pillBackground,
            border = BorderStroke(1.dp, bayanColors.glassBorder),
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = bayanColors.mutedText,
            textAlign = TextAlign.Center
        )
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(20.dp))
            actionButton()
        }
    }
}

@Composable
private fun LoadingSkeletonList(modifier: Modifier = Modifier) {
    val bayanColors = LocalBayanColors.current
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(4) { index ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, bayanColors.glassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (index == 0) 200.dp else 132.dp)
                    .alpha(alpha)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 92.dp, height = 22.dp)
                            .clip(CircleShape)
                            .background(bayanColors.pillBackground)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bayanColors.pillBackground)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bayanColors.pillBackground)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bayanColors.pillBackground)
                    )
                }
            }
        }
    }
}
