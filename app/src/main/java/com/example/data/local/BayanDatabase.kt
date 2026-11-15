package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ArticleEntity::class, SportsMatchEntity::class], version = 7, exportSchema = false)
abstract class BayanDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun sportsMatchDao(): SportsMatchDao

    companion object {
        @Volatile
        private var INSTANCE: BayanDatabase? = null

        fun getInstance(context: Context): BayanDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BayanDatabase::class.java,
                    "bayan_iraq_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
