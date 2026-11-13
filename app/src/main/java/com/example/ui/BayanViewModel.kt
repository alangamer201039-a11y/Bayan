package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BayanDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.BayanRepository
import com.example.model.BayanTab
import com.example.model.IraqRelevanceFilter
import com.example.model.IraqiProvinces
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.ProvinceWeather
import com.example.model.ReadingFontSize
import com.example.model.SourceTrustLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val weather: ProvinceWeather? = null,
    val allArticles: List<NewsArticle> = emptyList(),
    val savedArticles: List<NewsArticle> = emptyList(),
    val filteredArticles: List<NewsArticle> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoadError: Boolean = false,
    val openedArticle: NewsArticle? = null,
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
    val openedArticleId: String? = null,
    val toastMessage: String? = null
)

class BayanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BayanRepository by lazy {
        val db = BayanDatabase.getInstance(application)
        BayanRepository(
            articleDao = db.articleDao(),
            prefsRepo = UserPreferencesRepository(application)
        )
    }

    private val transient = MutableStateFlow(TransientState(isLoading = true))

    val uiState: StateFlow<BayanUiState> = combine(
        repository.userPreferences,
        repository.allArticles,
        repository.savedArticles,
        transient
    ) { prefs, all, saved, tr ->
        val iraqOnly = all.filter { IraqRelevanceFilter.isRelatedToIraq(it) }
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
                if (tr.selectedCategory == NewsCategory.ALL) trustedFiltered
                else trustedFiltered.filter { it.categoryId == tr.selectedCategory.id }
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

        val opened = tr.openedArticleId?.let { id ->
            all.find { it.id == id } ?: saved.find { it.id == id }
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
            weather = tr.weather,
            allArticles = all,
            savedArticles = saved,
            filteredArticles = filtered,
            isLoading = tr.isLoading,
            hasLoadError = tr.hasLoadError && filtered.isEmpty(),
            openedArticle = opened,
            toastMessage = tr.toastMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BayanUiState(isLoading = true)
    )

    init {
        refreshNews(manual = false)
        viewModelScope.launch {
            repository.userPreferences
                .map { it.selectedProvince }
                .distinctUntilChanged()
                .collect { provinceName ->
                    loadWeatherForProvince(provinceName)
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
    }

    fun refreshNews(manual: Boolean = false) {
        val currentCat = transient.value.selectedCategory
        transient.value = transient.value.copy(
            isLoading = true,
            hasLoadError = false,
            toastMessage = if (manual) "جاري تحديث الأخبار من المصادر الرسمية والموثوقة..." else transient.value.toastMessage
        )
        viewModelScope.launch {
            val result = repository.refreshNews(currentCat)
            transient.value = transient.value.copy(
                isLoading = false,
                hasLoadError = result.isFailure,
                toastMessage = when {
                    manual && result.isSuccess -> "تم تحديث الأخبار بنجاح"
                    manual && result.isFailure -> "تعذر التحديث، تحقق من الاتصال بالإنترنت"
                    else -> transient.value.toastMessage
                }
            )
        }
    }

    private fun loadWeatherForProvince(provinceName: String) {
        viewModelScope.launch {
            val province = IraqiProvinces.findByName(provinceName)
            val weather = repository.fetchWeather(province)
            if (weather != null) {
                transient.value = transient.value.copy(weather = weather)
            }
        }
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
            transient.value = transient.value.copy(toastMessage = "تم اختيار محافظة $provinceName")
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
            transient.value = transient.value.copy(
                toastMessage = if (enabled) "تم تفعيل فلتر المصادر الرسمية والموثوقة فقط" else "تم عرض جميع المصادر الإخبارية"
            )
            refreshNews(manual = false)
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoRefreshEnabled(enabled)
            transient.value = transient.value.copy(
                toastMessage = if (enabled) "تم تفعيل التحديث التلقائي السريع" else "تم إيقاف التحديث التلقائي"
            )
        }
    }

    fun toggleSaveArticle(article: NewsArticle) {
        viewModelScope.launch {
            val nowSaved = repository.toggleSave(article)
            transient.value = transient.value.copy(
                toastMessage = if (nowSaved) "تم حفظ الخبر بنجاح" else "أُزيل من المحفوظات"
            )
        }
    }

    fun openArticle(article: NewsArticle) {
        transient.value = transient.value.copy(openedArticleId = article.id)
    }

    fun closeArticle() {
        transient.value = transient.value.copy(openedArticleId = null)
    }

    fun clearToastMessage() {
        transient.value = transient.value.copy(toastMessage = null)
    }
}
