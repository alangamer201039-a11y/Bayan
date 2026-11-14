package com.example.data.repository

import com.example.data.local.ArticleDao
import com.example.data.local.ArticleEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.ArticleContentFetcher
import com.example.data.remote.NewsTranslationFilter
import com.example.data.remote.OpenMeteoApi
import com.example.data.remote.RssNewsService
import com.example.data.remote.WeatherClient
import com.example.model.AppLanguage
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
    private val translationFilter: NewsTranslationFilter = NewsTranslationFilter(),
    private val articleContentFetcher: ArticleContentFetcher = ArticleContentFetcher(),
    private val rssService: RssNewsService = RssNewsService(
        translationFilter = translationFilter,
        articleContentFetcher = articleContentFetcher
    ),
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
                listOf(
                    NewsCategory.BREAKING,
                    NewsCategory.HOLIDAYS,
                    NewsCategory.DECISIONS_PROD,
                    NewsCategory.GOVERNORATES,
                    NewsCategory.ECONOMY
                )
            } else {
                listOf(category)
            }

            val fetched = coroutineScope {
                targetCategories.map { cat ->
                    async {
                        runCatching {
                            rssService.fetchCategoryFeed(
                                category = cat,
                                verifiedSourcesOnly = prefs.verifiedSourcesOnly,
                                language = prefs.language
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
                .distinctBy { it.id }
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

    suspend fun retranslateCachedArticles(targetLanguage: AppLanguage) {
        val current = allArticles.first()
        val needingTranslation = current.filter {
            it.languageCode != targetLanguage.code ||
                (!it.originalFullContent.isNullOrBlank() && it.fullContent.isNullOrBlank())
        }
        if (needingTranslation.isEmpty()) return

        val translated = translationFilter.translateArticles(needingTranslation, targetLanguage)
        translated.forEach { updated ->
            articleDao.updateTranslatedContent(
                id = updated.id,
                source = updated.source,
                title = updated.title,
                summary = updated.summary,
                fullContent = updated.fullContent,
                languageCode = updated.languageCode
            )
        }
    }

    suspend fun fetchAndCacheFullArticle(
        article: NewsArticle,
        targetLanguage: AppLanguage
    ): NewsArticle? {
        if (article.link.isBlank()) return null
        val extracted = runCatching {
            articleContentFetcher.fetchFullArticle(
                articleUrl = article.link,
                titleHint = article.originalTitle.ifBlank { article.title }
            )
        }.getOrNull() ?: return null

        if (extracted.fullText.isBlank() && extracted.imageUrl.isNullOrBlank()) return null

        val hasNewText = extracted.fullText.isNotBlank()
        val bestFullRaw = if (hasNewText && (article.originalFullContent.isNullOrBlank() || extracted.fullText.length > article.originalFullContent.length)) {
            extracted.fullText
        } else {
            article.originalFullContent ?: article.fullContent
        }
        val detectedSourceLang = if (!bestFullRaw.isNullOrBlank()) {
            NewsTranslationFilter.detectLanguage(bestFullRaw)
        } else {
            AppLanguage.fromCode(article.originalLanguageCode)
        }
        val rawOriginalSummary = article.originalSummary.ifBlank { extracted.leadSummary }
        val enrichedRaw = article.copy(
            summary = article.summary.ifBlank { extracted.leadSummary },
            fullContent = bestFullRaw,
            originalSummary = rawOriginalSummary,
            originalFullContent = bestFullRaw,
            originalLanguageCode = detectedSourceLang.code,
            imageUrl = article.imageUrl ?: extracted.imageUrl
        )
        val translated = translationFilter.translateArticle(enrichedRaw, targetLanguage)
        val finalFull = translated.fullContent ?: bestFullRaw.orEmpty()

        articleDao.updateFullContent(
            id = translated.id,
            summary = translated.summary,
            fullContent = finalFull,
            originalSummary = translated.originalSummary,
            originalFullContent = bestFullRaw.orEmpty(),
            originalLanguageCode = detectedSourceLang.code,
            languageCode = translated.languageCode,
            imageUrl = translated.imageUrl
        )
        return translated.copy(fullContent = finalFull.ifBlank { article.fullContent })
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

    suspend fun fetchWeather(province: IraqiProvince): ProvinceWeather? =
        fetchWeatherByCoordinates(
            latitude = province.lat,
            longitude = province.lon,
            provinceName = province.nameAr,
            isFromGpsLocation = false
        )

    suspend fun fetchWeatherByCoordinates(
        latitude: Double,
        longitude: Double,
        provinceName: String,
        isFromGpsLocation: Boolean
    ): ProvinceWeather? {
        return try {
            val response = weatherApi.getCurrentWeather(latitude, longitude)
            val current = response.current
            val temp = current?.temperature2m?.roundToInt() ?: return null
            val condition = WeatherClient.weatherCodeToArabic(current.weatherCode)
            ProvinceWeather(
                provinceName = provinceName,
                temperatureCelsius = temp,
                conditionAr = condition,
                weatherCode = current.weatherCode,
                apparentTempCelsius = current.apparentTemperature?.roundToInt(),
                humidityPercent = current.relativeHumidity2m,
                windSpeedKmh = current.windSpeed10m?.roundToInt(),
                isDay = (current.isDay ?: 1) == 1,
                isFromGpsLocation = isFromGpsLocation
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

    suspend fun setLanguage(language: AppLanguage) {
        prefsRepo.setLanguage(language)
    }
}
