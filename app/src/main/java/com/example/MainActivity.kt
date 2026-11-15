package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppLanguage
import com.example.model.BayanTab
import com.example.model.NewsCategory
import com.example.model.SportsFilter
import com.example.model.SportsMatch
import com.example.ui.BayanUiState
import com.example.ui.BayanViewModel
import com.example.ui.LocalAppLanguage
import com.example.ui.LocalLocalizedResources
import com.example.ui.components.ArticleDetailSheet
import com.example.ui.components.BayanTopHeader
import com.example.ui.components.FloatingNavBar
import com.example.ui.components.MatchDetailSheet
import com.example.ui.resourcesForLanguage
import com.example.ui.screens.IraqSportsScreen
import com.example.ui.screens.NewsFeedScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BayanTheme
import com.example.ui.theme.LocalBayanColors
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: BayanViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.isDarkTheme) {
                if (uiState.isDarkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT
                        )
                    )
                }
            }

            val context = LocalContext.current
            val localizedResources = remember(uiState.language, context) {
                context.resourcesForLanguage(uiState.language)
            }

            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalAppLanguage provides uiState.language,
                LocalLocalizedResources provides localizedResources
            ) {
                BayanTheme(darkTheme = uiState.isDarkTheme) {
                    BayanApp(
                        uiState = uiState,
                        onSelectTab = viewModel::selectTab,
                        onSelectCategory = viewModel::selectCategory,
                        onRefresh = { viewModel.refreshNews(manual = true) },
                        onToggleSearch = viewModel::toggleSearch,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onClearSearch = viewModel::clearSearch,
                        onToggleTheme = viewModel::toggleTheme,
                        onSelectProvince = viewModel::selectProvince,
                        onSelectLanguage = viewModel::selectLanguage,
                        onSelectFontSize = viewModel::selectFontSize,
                        onVerifiedSourcesToggle = viewModel::setVerifiedSourcesOnly,
                        onAutoRefreshToggle = viewModel::setAutoRefreshEnabled,
                        onOpenArticle = viewModel::openArticle,
                        onCloseArticle = viewModel::closeArticle,
                        onToggleSave = viewModel::toggleSaveArticle,
                        onLocateGranted = { viewModel.detectLocationAndRefreshWeather(userInitiated = true) },
                        onLocationPermissionDenied = viewModel::onLocationPermissionDenied,
                        onRefreshWeather = viewModel::refreshWeather,
                        onSelectSportsFilter = viewModel::selectSportsFilter,
                        onRefreshSports = { viewModel.refreshSports(manual = true) },
                        onOpenMatch = viewModel::openMatch,
                        onCloseMatch = viewModel::closeMatch,
                        onClearToast = viewModel::clearToastMessage
                    )
                }
            }
        }
    }
}

@Composable
fun BayanApp(
    uiState: BayanUiState,
    onSelectTab: (BayanTab) -> Unit,
    onSelectCategory: (com.example.model.NewsCategory) -> Unit,
    onRefresh: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectProvince: (String) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectFontSize: (com.example.model.ReadingFontSize) -> Unit,
    onVerifiedSourcesToggle: (Boolean) -> Unit,
    onAutoRefreshToggle: (Boolean) -> Unit,
    onOpenArticle: (com.example.model.NewsArticle) -> Unit,
    onCloseArticle: () -> Unit,
    onToggleSave: (com.example.model.NewsArticle) -> Unit,
    onLocateGranted: () -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onRefreshWeather: () -> Unit,
    onSelectSportsFilter: (SportsFilter) -> Unit = {},
    onRefreshSports: () -> Unit = {},
    onOpenMatch: (SportsMatch) -> Unit = {},
    onCloseMatch: () -> Unit = {},
    onClearToast: () -> Unit
) {
    val bayanColors = LocalBayanColors.current

    BackHandler(
        enabled = uiState.openedMatch != null || uiState.openedArticle != null || uiState.isSearchVisible || uiState.activeTab != BayanTab.HOME
    ) {
        when {
            uiState.openedMatch != null -> onCloseMatch()
            uiState.openedArticle != null -> onCloseArticle()
            uiState.isSearchVisible -> onToggleSearch()
            uiState.activeTab != BayanTab.HOME -> onSelectTab(BayanTab.HOME)
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            delay(2200)
            onClearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BayanTopHeader(
                selectedProvince = uiState.selectedProvince,
                weather = uiState.weather,
                isDarkTheme = uiState.isDarkTheme,
                isLoading = uiState.isLoading,
                isSearchVisible = uiState.isSearchVisible,
                searchQuery = uiState.searchQuery,
                showCategories = uiState.activeTab == BayanTab.HOME,
                selectedCategory = uiState.selectedCategory,
                onBrandClick = { onSelectTab(BayanTab.HOME) },
                onProvinceClick = { onSelectTab(BayanTab.SETTINGS) },
                onRefreshClick = onRefresh,
                onSearchToggleClick = onToggleSearch,
                onThemeToggleClick = onToggleTheme,
                onSearchQueryChange = onSearchQueryChange,
                onClearSearch = onClearSearch,
                onCategorySelected = onSelectCategory
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    uiState.activeTab == BayanTab.SETTINGS -> {
                        SettingsScreen(
                            selectedProvince = uiState.selectedProvince,
                            selectedLanguage = uiState.language,
                            weather = uiState.weather,
                            fontSize = uiState.fontSize,
                            isDarkTheme = uiState.isDarkTheme,
                            verifiedSourcesOnly = uiState.verifiedSourcesOnly,
                            autoRefreshEnabled = uiState.autoRefreshEnabled,
                            onProvinceSelected = onSelectProvince,
                            onLanguageSelected = onSelectLanguage,
                            onFontSizeSelected = onSelectFontSize,
                            onThemeSelected = { dark ->
                                if (dark != uiState.isDarkTheme) onToggleTheme()
                            },
                            onVerifiedSourcesToggle = onVerifiedSourcesToggle,
                            onAutoRefreshToggle = onAutoRefreshToggle,
                            isWeatherLoading = uiState.isWeatherLoading,
                            isDetectingLocation = uiState.isDetectingLocation,
                            onLocateGranted = onLocateGranted,
                            onLocationPermissionDenied = onLocationPermissionDenied,
                            onRefreshWeather = onRefreshWeather
                        )
                    }

                    uiState.activeTab == BayanTab.HOME && uiState.selectedCategory == NewsCategory.SPORTS -> {
                        IraqSportsScreen(
                            matches = uiState.sportsMatches,
                            sportsNews = uiState.sportsNewsArticles,
                            selectedFilter = uiState.selectedSportsFilter,
                            isSportsLoading = uiState.isSportsLoading,
                            hasSportsError = uiState.hasSportsError,
                            onSelectFilter = onSelectSportsFilter,
                            onRefreshSports = onRefreshSports,
                            onMatchClick = onOpenMatch,
                            onArticleClick = onOpenArticle,
                            onToggleSaveArticle = onToggleSave
                        )
                    }

                    else -> {
                        NewsFeedScreen(
                            activeTab = uiState.activeTab,
                            selectedCategory = uiState.selectedCategory,
                            articles = uiState.filteredArticles,
                            isLoading = uiState.isLoading,
                            hasLoadError = uiState.hasLoadError,
                            searchQuery = uiState.searchQuery,
                            onRefresh = onRefresh,
                            onArticleClick = onOpenArticle,
                            onToggleSave = onToggleSave,
                            weather = uiState.weather,
                            selectedProvince = uiState.selectedProvince,
                            isWeatherLoading = uiState.isWeatherLoading,
                            isDetectingLocation = uiState.isDetectingLocation,
                            onLocateGranted = onLocateGranted,
                            onLocationPermissionDenied = onLocationPermissionDenied,
                            onRefreshWeather = onRefreshWeather,
                            onProvinceClick = { onSelectTab(BayanTab.SETTINGS) }
                        )
                    }
                }
            }
        }

        // Floating glass pill navigation bar
        FloatingNavBar(
            activeTab = uiState.activeTab,
            savedCount = uiState.savedArticles.size,
            onTabSelected = onSelectTab,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Floating Arabic toast pill above navigation bar
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 94.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = bayanColors.glassSurface,
                border = BorderStroke(1.dp, bayanColors.accentEmber.copy(alpha = 0.45f)),
                shadowElevation = 10.dp
            ) {
                Text(
                    text = uiState.toastMessage.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        // Article Reader Modal Sheet
        val opened = uiState.openedArticle
        if (opened != null) {
            ArticleDetailSheet(
                article = opened,
                fontSize = uiState.fontSize,
                onDismiss = onCloseArticle,
                onToggleSave = { onToggleSave(opened) },
                isLoadingFullContent = uiState.isFullArticleLoading
            )
        }

        // Sports Match Lineup & Statistics Modal Sheet (Strictly Text, Tables & Stats)
        val openedMatch = uiState.openedMatch
        if (openedMatch != null) {
            MatchDetailSheet(
                match = openedMatch,
                isLoadingDetails = uiState.isMatchDetailLoading,
                onDismiss = onCloseMatch
            )
        }
    }
}
