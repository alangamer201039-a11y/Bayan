package com.example.ui

import android.app.Application
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.BayanDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.location.LocationTracker
import com.example.data.repository.BayanRepository
import com.example.model.AppLanguage
import com.example.model.BayanTab
import com.example.model.IraqRelevanceFilter
import com.example.model.IraqSportsLocalizer
import com.example.model.IraqiProvinces
import com.example.model.MatchStatus
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.ProvinceWeather
import com.example.model.ReadingFontSize
import com.example.model.SourceTrustLevel
import com.example.model.SportsFilter
import com.example.model.SportsMatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BayanUiState(
    val activeTab: BayanTab = BayanTab.HOME,
    val selectedCategory: NewsCategory = NewsCategory.ALL,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val isDarkTheme: Boolean = true,
    val selectedProvince: String = "صلاح الدين",
    val fontSize: ReadingFontSize = ReadingFontSize.MEDIUM,
    val verifiedSourcesOnly: Boolean = true,
    val autoRefreshEnabled: Boolean = true,
    val language: AppLanguage = AppLanguage.ARABIC,
    val weather: ProvinceWeather? = null,
    val isWeatherLoading: Boolean = false,
    val isDetectingLocation: Boolean = false,
    val allArticles: List<NewsArticle> = emptyList(),
    val savedArticles: List<NewsArticle> = emptyList(),
    val filteredArticles: List<NewsArticle> = emptyList(),
    val sportsMatches: List<SportsMatch> = emptyList(),
    val sportsNewsArticles: List<NewsArticle> = emptyList(),
    val selectedSportsFilter: SportsFilter = SportsFilter.ALL,
    val isSportsLoading: Boolean = false,
    val hasSportsError: Boolean = false,
    val openedMatch: SportsMatch? = null,
    val isMatchDetailLoading: Boolean = false,
    val isLoading: Boolean = false,
    val hasLoadError: Boolean = false,
    val openedArticle: NewsArticle? = null,
    val isFullArticleLoading: Boolean = false,
    val toastMessage: String? = null
)

private data class TransientState(
    val activeTab: BayanTab = BayanTab.HOME,
    val selectedCategory: NewsCategory = NewsCategory.ALL,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val hasLoadError: Boolean = false,
    val weather: ProvinceWeather? = null,
    val isWeatherLoading: Boolean = false,
    val isDetectingLocation: Boolean = false,
    val selectedSportsFilter: SportsFilter = SportsFilter.ALL,
    val isSportsLoading: Boolean = false,
    val hasSportsError: Boolean = false,
    val openedMatchId: String? = null,
    val isMatchDetailLoading: Boolean = false,
    val openedArticleId: String? = null,
    val isFullArticleLoading: Boolean = false,
    val toastMessage: String? = null
)

class BayanViewModel(application: Application) : AndroidViewModel(application) {
    private val locationTracker: LocationTracker by lazy {
        LocationTracker(application)
    }

    private val repository: BayanRepository by lazy {
        val db = BayanDatabase.getInstance(application)
        BayanRepository(
            articleDao = db.articleDao(),
            prefsRepo = UserPreferencesRepository(application),
            sportsMatchDao = db.sportsMatchDao()
        )
    }

    private val transient = MutableStateFlow(TransientState(isLoading = true, isWeatherLoading = true, isSportsLoading = true))

    private fun localizedRes(language: AppLanguage = uiState.value.language): Resources =
        getApplication<Application>().resourcesForLanguage(language)

    private fun localizedStr(
        @StringRes id: Int,
        language: AppLanguage = uiState.value.language,
        vararg args: Any
    ): String {
        val res = localizedRes(language)
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    val uiState: StateFlow<BayanUiState> = combine(
        repository.userPreferences,
        repository.allArticles,
        repository.savedArticles,
        repository.sportsMatches,
        transient
    ) { prefs, all, saved, matches, tr ->
        val langArticles = all.filter { it.languageCode == prefs.language.code }
        val iraqOnly = langArticles.filter { IraqRelevanceFilter.isRelatedToIraq(it) }
        val trustedFiltered = if (prefs.verifiedSourcesOnly && tr.activeTab != BayanTab.SAVED) {
            val verified = iraqOnly.filter { it.trustLevel != SourceTrustLevel.GENERAL }
            verified.ifEmpty { iraqOnly }
        } else {
            iraqOnly
        }

        val baseList = when (tr.activeTab) {
            BayanTab.SAVED -> saved
            BayanTab.BREAKING -> trustedFiltered.filter {
                it.isBreaking || it.categoryId == NewsCategory.BREAKING.id
            }
            BayanTab.HOME -> {
                when (tr.selectedCategory) {
                    NewsCategory.ALL -> trustedFiltered
                    NewsCategory.HOLIDAYS -> trustedFiltered.filter { article ->
                        article.categoryId == NewsCategory.HOLIDAYS.id ||
                            "${article.title} ${article.summary}".let { txt ->
                                txt.contains("عطلة") || txt.contains("تعطيل") || txt.contains("العطل") ||
                                    txt.contains("التربية") || txt.contains("إجازة رسمية") ||
                                    txt.contains("پشوو") || txt.contains("ڕاگرتنی دەوام") ||
                                    txt.contains("وەزارەتی پەروەردە")
                            }
                    }
                    NewsCategory.DECISIONS_PROD -> trustedFiltered.filter { article ->
                        article.categoryId == NewsCategory.DECISIONS_PROD.id ||
                            "${article.title} ${article.summary}".let { txt ->
                                txt.contains("مجلس الوزراء") || txt.contains("مجلس وزراء") ||
                                    txt.contains("دائرة الإعلام والمعلومات") || txt.contains("قرار") ||
                                    txt.contains("إنتاج") || txt.contains("انتاج") ||
                                    txt.contains("مشروع") || txt.contains("صناعة") ||
                                    txt.contains("زراعة") || txt.contains("كهرباء") ||
                                    txt.contains("ئەنجومەنی وەزیران") || txt.contains("حکومەتی هەرێم") ||
                                    txt.contains("فەرمانگەی میدیا و زانیاری") || txt.contains("بڕیار") ||
                                    txt.contains("پڕۆژە") || txt.contains("بەرهەم")
                            }
                    }
                    NewsCategory.GOVERNORATES -> trustedFiltered.filter { article ->
                        article.categoryId == NewsCategory.GOVERNORATES.id ||
                            "${article.title} ${article.summary}".let { txt ->
                                IraqiProvinces.all.any { prov ->
                                    val primaryAr = prov.nameAr.substringBefore(" (")
                                    val primaryCkb = prov.nameCkb.substringBefore(" (")
                                    txt.contains(primaryAr) || txt.contains(primaryCkb) ||
                                        (prov.nameAr.contains("نينوى") && (txt.contains("نينوى") || txt.contains("نەینەوا"))) ||
                                        (prov.nameAr.contains("الديوانية") && (txt.contains("الديوانية") || txt.contains("دیوانیە")))
                                }
                            }
                    }
                    else -> trustedFiltered.filter { it.categoryId == tr.selectedCategory.id }
                }
            }
            BayanTab.SETTINGS -> emptyList()
        }

        val query = tr.searchQuery.trim()
        val filtered = if (query.isEmpty()) {
            baseList
        } else {
            baseList.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.summary.contains(query, ignoreCase = true) ||
                    it.source.contains(query, ignoreCase = true)
            }
        }

        val rawSportsNews = trustedFiltered.filter { it.categoryId == NewsCategory.SPORTS.id }
        val sportsNews = if (query.isEmpty()) {
            rawSportsNews
        } else {
            rawSportsNews.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.summary.contains(query, ignoreCase = true) ||
                    it.source.contains(query, ignoreCase = true)
            }
        }
        val sortedMatches = matches.sortedWith(
            compareBy<SportsMatch> {
                when (it.status) {
                    MatchStatus.LIVE -> 0
                    MatchStatus.SCHEDULED -> 1
                    MatchStatus.FINISHED -> 2
                    MatchStatus.POSTPONED -> 3
                }
            }.thenBy {
                if (it.status == MatchStatus.FINISHED) -it.kickoffEpochMs else it.kickoffEpochMs
            }
        )
        val filteredMatches = if (query.isEmpty()) {
            sortedMatches
        } else {
            sortedMatches.filter { m ->
                m.homeTeamRaw.contains(query, ignoreCase = true) ||
                    m.awayTeamRaw.contains(query, ignoreCase = true) ||
                    m.localizedHomeTeam(prefs.language).contains(query, ignoreCase = true) ||
                    m.localizedAwayTeam(prefs.language).contains(query, ignoreCase = true) ||
                    m.localizedCompetition(prefs.language).contains(query, ignoreCase = true)
            }
        }

        val opened = tr.openedArticleId?.let { id ->
            all.find { it.id == id } ?: saved.find { it.id == id }
        }
        val openedMatch = tr.openedMatchId?.let { id ->
            matches.find { it.id == id }
        }

        BayanUiState(
            activeTab = tr.activeTab,
            selectedCategory = tr.selectedCategory,
            isSearchVisible = tr.isSearchVisible,
            searchQuery = tr.searchQuery,
            isDarkTheme = prefs.isDarkTheme,
            selectedProvince = prefs.selectedProvince,
            fontSize = prefs.fontSize,
            verifiedSourcesOnly = prefs.verifiedSourcesOnly,
            autoRefreshEnabled = prefs.autoRefreshEnabled,
            language = prefs.language,
            weather = tr.weather,
            isWeatherLoading = tr.isWeatherLoading,
            isDetectingLocation = tr.isDetectingLocation,
            allArticles = langArticles,
            savedArticles = saved,
            filteredArticles = filtered,
            sportsMatches = filteredMatches,
            sportsNewsArticles = sportsNews,
            selectedSportsFilter = tr.selectedSportsFilter,
            isSportsLoading = tr.isSportsLoading,
            hasSportsError = tr.hasSportsError && filteredMatches.isEmpty(),
            openedMatch = openedMatch,
            isMatchDetailLoading = tr.isMatchDetailLoading && openedMatch != null,
            isLoading = tr.isLoading,
            hasLoadError = tr.hasLoadError && filtered.isEmpty(),
            openedArticle = opened,
            isFullArticleLoading = tr.isFullArticleLoading && opened != null,
            toastMessage = tr.toastMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BayanUiState(isLoading = true, isWeatherLoading = true, isSportsLoading = true)
    )

    init {
        viewModelScope.launch {
            val initialLang = repository.userPreferences.first().language
            repository.retranslateCachedArticles(initialLang)
        }
        refreshNews(manual = false)
        refreshSports(manual = false)
        viewModelScope.launch {
            if (locationTracker.hasLocationPermission()) {
                detectLocationAndRefreshWeather(userInitiated = false)
            }
        }
        viewModelScope.launch {
            repository.userPreferences
                .map { it.selectedProvince }
                .distinctUntilChanged()
                .collect { provinceName ->
                    val currentWeather = transient.value.weather
                    if (currentWeather == null || !currentWeather.isFromGpsLocation || currentWeather.provinceName != provinceName) {
                        loadWeatherForProvince(provinceName)
                    }
                }
        }
        // Fast periodic background refresh every 3 minutes when enabled
        viewModelScope.launch {
            repository.userPreferences
                .map { it.autoRefreshEnabled }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    while (enabled && isActive) {
                        delay(3L * 60_000L)
                        refreshNews(manual = false)
                        refreshSports(manual = false)
                    }
                }
        }
        // Poll every 60s when Sports category is active & matches are live or recently kicked off to catch final whistle immediately
        viewModelScope.launch {
            transient
                .map { it.activeTab == BayanTab.HOME && it.selectedCategory == NewsCategory.SPORTS }
                .distinctUntilChanged()
                .collectLatest { watchingSports ->
                    while (watchingSports && isActive) {
                        delay(60_000L)
                        val nowMs = System.currentTimeMillis()
                        val needsLiveScoreSync = uiState.value.sportsMatches.any {
                            IraqSportsLocalizer.isRecentlyPlayedOrLive(it, nowMs)
                        }
                        if (needsLiveScoreSync) {
                            refreshSports(manual = false)
                        }
                    }
                }
        }
    }

    fun selectTab(tab: BayanTab) {
        transient.value = transient.value.copy(activeTab = tab)
    }

    fun selectCategory(category: NewsCategory) {
        transient.value = transient.value.copy(selectedCategory = category)
        if (category != NewsCategory.ALL) {
            refreshNews(manual = false)
        }
        if (category == NewsCategory.SPORTS) {
            refreshSports(manual = false)
        }
    }

    fun selectSportsFilter(filter: SportsFilter) {
        transient.value = transient.value.copy(selectedSportsFilter = filter)
    }

    fun refreshSports(manual: Boolean = false) {
        transient.value = transient.value.copy(
            isSportsLoading = true,
            hasSportsError = false
        )
        viewModelScope.launch {
            val res = repository.refreshSportsMatches()
            val lang = uiState.value.language
            transient.value = transient.value.copy(
                isSportsLoading = false,
                hasSportsError = res.isFailure,
                toastMessage = if (manual && res.isFailure && uiState.value.sportsMatches.isEmpty()) {
                    localizedStr(R.string.sports_error_load, lang)
                } else {
                    transient.value.toastMessage
                }
            )
        }
    }

    fun openMatch(match: SportsMatch) {
        transient.value = transient.value.copy(
            openedMatchId = match.id,
            isMatchDetailLoading = true
        )
        viewModelScope.launch {
            repository.refreshMatchDetails(match)
            if (transient.value.openedMatchId == match.id) {
                transient.value = transient.value.copy(isMatchDetailLoading = false)
            }
        }
    }

    fun closeMatch() {
        transient.value = transient.value.copy(
            openedMatchId = null,
            isMatchDetailLoading = false
        )
    }

    fun refreshNews(manual: Boolean = false) {
        val currentCat = transient.value.selectedCategory
        val currentLang = uiState.value.language
        transient.value = transient.value.copy(
            isLoading = true,
            hasLoadError = false,
            toastMessage = if (manual) localizedStr(R.string.toast_refreshing, currentLang) else transient.value.toastMessage
        )
        viewModelScope.launch {
            val result = repository.refreshNews(currentCat)
            val activeLang = uiState.value.language
            transient.value = transient.value.copy(
                isLoading = false,
                hasLoadError = result.isFailure,
                toastMessage = when {
                    manual && result.isSuccess -> localizedStr(R.string.toast_refresh_success, activeLang)
                    manual && result.isFailure -> localizedStr(R.string.toast_refresh_error, activeLang)
                    else -> transient.value.toastMessage
                }
            )
        }
    }

    private fun loadWeatherForProvince(provinceName: String) {
        viewModelScope.launch {
            transient.value = transient.value.copy(isWeatherLoading = true)
            val province = IraqiProvinces.findByName(provinceName)
            val weather = repository.fetchWeather(province)
            transient.value = transient.value.copy(
                weather = weather ?: transient.value.weather,
                isWeatherLoading = false
            )
        }
    }

    fun refreshWeather() {
        val currentProvince = uiState.value.selectedProvince
        if (locationTracker.hasLocationPermission() && transient.value.weather?.isFromGpsLocation == true) {
            detectLocationAndRefreshWeather(userInitiated = false)
        } else {
            loadWeatherForProvince(currentProvince)
        }
    }

    fun detectLocationAndRefreshWeather(userInitiated: Boolean = true) {
        viewModelScope.launch {
            transient.value = transient.value.copy(
                isDetectingLocation = true,
                isWeatherLoading = true
            )
            val coords = locationTracker.getCurrentCoordinates()
            val lang = uiState.value.language

            if (coords != null && IraqiProvinces.isWithinIraqBounds(coords.latitude, coords.longitude)) {
                val nearest = IraqiProvinces.nearestProvince(coords.latitude, coords.longitude)
                val weather = repository.fetchWeatherByCoordinates(
                    latitude = coords.latitude,
                    longitude = coords.longitude,
                    provinceName = nearest.nameAr,
                    isFromGpsLocation = true
                )
                repository.setSelectedProvince(nearest.nameAr)
                transient.value = transient.value.copy(
                    weather = weather ?: transient.value.weather,
                    isDetectingLocation = false,
                    isWeatherLoading = false,
                    toastMessage = if (userInitiated && weather != null) {
                        localizedStr(R.string.toast_location_detected, lang, nearest.displayName(lang))
                    } else {
                        transient.value.toastMessage
                    }
                )
            } else {
                val fallbackProvince = IraqiProvinces.findByName(uiState.value.selectedProvince)
                val weather = repository.fetchWeather(fallbackProvince)
                transient.value = transient.value.copy(
                    weather = weather ?: transient.value.weather,
                    isDetectingLocation = false,
                    isWeatherLoading = false,
                    toastMessage = when {
                        userInitiated && coords != null -> localizedStr(
                            R.string.toast_location_outside_iraq,
                            lang,
                            fallbackProvince.displayName(lang)
                        )
                        userInitiated -> localizedStr(R.string.toast_location_unavailable, lang)
                        else -> transient.value.toastMessage
                    }
                )
            }
        }
    }

    fun onLocationPermissionDenied() {
        val lang = uiState.value.language
        transient.value = transient.value.copy(
            isDetectingLocation = false,
            toastMessage = localizedStr(R.string.toast_location_permission_denied, lang)
        )
    }

    fun toggleSearch() {
        val nextVisible = !transient.value.isSearchVisible
        transient.value = transient.value.copy(
            isSearchVisible = nextVisible,
            searchQuery = if (!nextVisible) "" else transient.value.searchQuery
        )
    }

    fun updateSearchQuery(query: String) {
        transient.value = transient.value.copy(searchQuery = query)
    }

    fun clearSearch() {
        transient.value = transient.value.copy(searchQuery = "")
    }

    fun toggleTheme() {
        val nextDark = !uiState.value.isDarkTheme
        viewModelScope.launch {
            repository.setDarkTheme(nextDark)
        }
    }

    fun selectProvince(provinceName: String) {
        viewModelScope.launch {
            repository.setSelectedProvince(provinceName)
            val lang = uiState.value.language
            val displayProvince = IraqiProvinces.findByName(provinceName).displayName(lang)
            transient.value = transient.value.copy(
                toastMessage = localizedStr(R.string.toast_province_selected, lang, displayProvince)
            )
        }
    }

    fun selectLanguage(language: AppLanguage) {
        if (language == uiState.value.language) return
        viewModelScope.launch {
            repository.setLanguage(language)
            transient.value = transient.value.copy(
                isLoading = true,
                hasLoadError = false,
                toastMessage = localizedStr(R.string.toast_language_changed, language, language.labelNative)
            )
            repository.retranslateCachedArticles(language)
            val result = repository.refreshNews(transient.value.selectedCategory)
            transient.value = transient.value.copy(
                isLoading = false,
                hasLoadError = result.isFailure && uiState.value.filteredArticles.isEmpty()
            )
        }
    }

    fun selectFontSize(fontSize: ReadingFontSize) {
        viewModelScope.launch {
            repository.setFontSize(fontSize)
        }
    }

    fun setVerifiedSourcesOnly(enabled: Boolean) {
        viewModelScope.launch {
            repository.setVerifiedSourcesOnly(enabled)
            val lang = uiState.value.language
            transient.value = transient.value.copy(
                toastMessage = localizedStr(
                    if (enabled) R.string.toast_verified_enabled else R.string.toast_verified_disabled,
                    lang
                )
            )
            refreshNews(manual = false)
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoRefreshEnabled(enabled)
            val lang = uiState.value.language
            transient.value = transient.value.copy(
                toastMessage = localizedStr(
                    if (enabled) R.string.toast_auto_refresh_enabled else R.string.toast_auto_refresh_disabled,
                    lang
                )
            )
        }
    }

    fun toggleSaveArticle(article: NewsArticle) {
        viewModelScope.launch {
            val nowSaved = repository.toggleSave(article)
            val lang = uiState.value.language
            transient.value = transient.value.copy(
                toastMessage = localizedStr(
                    if (nowSaved) R.string.toast_saved else R.string.toast_unsaved,
                    lang
                )
            )
        }
    }

    fun openArticle(article: NewsArticle) {
        val needsFullText = article.fullContent.isNullOrBlank() && article.link.isNotBlank()
        val needsEnrichment = (article.fullContent.isNullOrBlank() || article.imageUrl.isNullOrBlank()) && article.link.isNotBlank()
        transient.value = transient.value.copy(
            openedArticleId = article.id,
            isFullArticleLoading = needsFullText
        )
        if (needsEnrichment) {
            val targetLanguage = uiState.value.language
            viewModelScope.launch {
                repository.fetchAndCacheFullArticle(article, targetLanguage)
                if (transient.value.openedArticleId == article.id) {
                    transient.value = transient.value.copy(isFullArticleLoading = false)
                }
            }
        }
    }

    fun closeArticle() {
        transient.value = transient.value.copy(
            openedArticleId = null,
            isFullArticleLoading = false
        )
    }

    fun clearToastMessage() {
        transient.value = transient.value.copy(toastMessage = null)
    }
}
