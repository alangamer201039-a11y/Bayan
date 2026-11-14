package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AppLanguage
import com.example.model.NewsArticle
import com.example.model.SourceTrustLevel

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val isBreaking: Boolean,
    val source: String,
    val sourceDomain: String = "",
    val trustLevelKey: String = SourceTrustLevel.GENERAL.key,
    val publishedEpochMs: Long,
    val title: String,
    val summary: String,
    val link: String,
    val imageUrl: String? = null,
    val fullContent: String? = null,
    val isSaved: Boolean = false,
    val savedAtMs: Long = 0L,
    val cachedAtMs: Long = System.currentTimeMillis(),
    val languageCode: String = AppLanguage.ARABIC.code,
    val originalTitle: String = title,
    val originalSummary: String = summary,
    val originalFullContent: String? = fullContent,
    val originalLanguageCode: String = languageCode
) {
    fun toDomain(): NewsArticle = NewsArticle(
        id = id,
        categoryId = categoryId,
        isBreaking = isBreaking,
        source = source,
        sourceDomain = sourceDomain,
        trustLevel = SourceTrustLevel.fromKey(trustLevelKey),
        publishedEpochMs = publishedEpochMs,
        title = title,
        summary = summary,
        link = link,
        imageUrl = com.example.data.remote.ArticleContentFetcher.sanitizeArticleImageUrl(
            url = imageUrl,
            allowLocalResource = true
        ),
        fullContent = fullContent,
        isSaved = isSaved,
        languageCode = languageCode,
        originalTitle = originalTitle,
        originalSummary = originalSummary,
        originalFullContent = originalFullContent,
        originalLanguageCode = originalLanguageCode
    )

    companion object {
        fun fromDomain(article: NewsArticle, savedAtMs: Long = 0L): ArticleEntity = ArticleEntity(
            id = article.id,
            categoryId = article.categoryId,
            isBreaking = article.isBreaking,
            source = article.source,
            sourceDomain = article.sourceDomain,
            trustLevelKey = article.trustLevel.key,
            publishedEpochMs = article.publishedEpochMs,
            title = article.title,
            summary = article.summary,
            link = article.link,
            imageUrl = article.imageUrl,
            fullContent = article.fullContent,
            isSaved = article.isSaved,
            savedAtMs = savedAtMs,
            languageCode = article.languageCode,
            originalTitle = article.originalTitle,
            originalSummary = article.originalSummary,
            originalFullContent = article.originalFullContent,
            originalLanguageCode = article.originalLanguageCode
        )
    }
}
