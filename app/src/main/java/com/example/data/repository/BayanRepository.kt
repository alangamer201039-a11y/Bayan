package com.example.data.repository

import com.example.data.local.ArticleDao
import com.example.data.local.ArticleEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.OpenMeteoApi
import com.example.data.remote.RssNewsService
import com.example.data.remote.WeatherClient
import com.example.model.IraqiProvince
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.ProvinceWeather
import com.example.model.ReadingFontSize
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class BayanRepository(
    private val articleDao: ArticleDao,
    private val prefsRepo: UserPreferencesRepository,
    private val rssService: RssNewsService = RssNewsService(),
    private val weatherApi: OpenMeteoApi = WeatherClient.api
) {
    val userPreferences: Flow<UserPreferences> = prefsRepo.userPreferences

    val allArticles: Flow<List<NewsArticle>> = articleDao.observeAllArticles()
        .map { entities -> entities.map { it.toDomain() } }

    val savedArticles: Flow<List<NewsArticle>> = articleDao.observeSavedArticles()
        .map { entities -> entities.map { it.toDomain() } }

    suspend fun refreshNews(category: NewsCategory): Result<Int> {
        return try {
            val prefs = prefsRepo.userPreferences.first()
            val targetCategories = if (category == NewsCategory.ALL) {
                listOf(NewsCategory.BREAKING, NewsCategory.GOVERNORATES, NewsCategory.ECONOMY)
            } else {
                listOf(category)
            }

            val fetched = coroutineScope {
                targetCategories.map { cat ->
                    async {
                        runCatching {
                            rssService.fetchCategoryFeed(
                                category = cat,
                                verifiedSourcesOnly = prefs.verifiedSourcesOnly
                            )
                        }.getOrNull().orEmpty()
                    }
                }.awaitAll().flatten()
            }

            if (fetched.isEmpty()) {
                return Result.failure(IllegalStateException("لم يتم العثور على أخبار حالياً"))
            }

            val savedIds = articleDao.getSavedArticleIds().toSet()
            val nowMs = System.currentTimeMillis()
            val deduplicated = fetched
                .distinctBy { it.title }
                .map { article ->
                    ArticleEntity.fromDomain(
                        article = article.copy(isSaved = article.id in savedIds),
                        savedAtMs = if (article.id in savedIds) nowMs else 0L
                    )
                }

            articleDao.upsertArticles(deduplicated)
            articleDao.pruneOldCache(nowMs - 48L * 60L * 60L * 1000L)
            Result.success(deduplicated.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleSave(article: NewsArticle): Boolean {
        val nextSaved = !article.isSaved
        val savedAt = if (nextSaved) System.currentTimeMillis() else 0L
        articleDao.upsertArticles(
            listOf(
                ArticleEntity.fromDomain(
                    article = article.copy(isSaved = nextSaved),
                    savedAtMs = savedAt
                )
            )
        )
        return nextSaved
    }

    suspend fun fetchWeather(province: IraqiProvince): ProvinceWeather? {
        return try {
            val response = weatherApi.getCurrentWeather(province.lat, province.lon)
            val temp = response.current?.temperature2m?.roundToInt() ?: return null
            val condition = WeatherClient.weatherCodeToArabic(response.current.weatherCode)
            ProvinceWeather(
                provinceName = province.nameAr,
                temperatureCelsius = temp,
                conditionAr = condition
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setSelectedProvince(provinceName: String) {
        prefsRepo.setSelectedProvince(provinceName)
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        prefsRepo.setDarkTheme(isDark)
    }

    suspend fun setFontSize(fontSize: ReadingFontSize) {
        prefsRepo.setFontSize(fontSize)
    }

    suspend fun setVerifiedSourcesOnly(enabled: Boolean) {
        prefsRepo.setVerifiedSourcesOnly(enabled)
    }

    suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        prefsRepo.setAutoRefreshEnabled(enabled)
    }
}
