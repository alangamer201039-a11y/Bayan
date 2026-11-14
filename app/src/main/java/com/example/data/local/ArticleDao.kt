package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedEpochMs DESC")
    fun observeAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isSaved = 1 ORDER BY savedAtMs DESC")
    fun observeSavedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT id FROM articles WHERE isSaved = 1")
    suspend fun getSavedArticleIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isSaved = :isSaved, savedAtMs = :savedAtMs WHERE id = :id")
    suspend fun updateSavedState(id: String, isSaved: Boolean, savedAtMs: Long)

    @Query("UPDATE articles SET title = :title, summary = :summary, languageCode = :languageCode WHERE id = :id")
    suspend fun updateTranslatedContent(id: String, title: String, summary: String, languageCode: String)

    @Query("DELETE FROM articles WHERE isSaved = 0 AND cachedAtMs < :cutoffMs")
    suspend fun pruneOldCache(cutoffMs: Long)
}
