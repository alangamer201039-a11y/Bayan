package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SportsMatchDao {
    @Query("SELECT * FROM sports_matches ORDER BY kickoffEpochMs ASC")
    fun observeAllMatches(): Flow<List<SportsMatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMatches(matches: List<SportsMatchEntity>)

    @Query("DELETE FROM sports_matches WHERE cachedAtMs < :cutoffMs")
    suspend fun pruneOldMatches(cutoffMs: Long)
}
