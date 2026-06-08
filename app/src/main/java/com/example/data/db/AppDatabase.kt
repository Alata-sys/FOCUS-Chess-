package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ChessGameEntity
import com.example.data.model.LichessProfile
import com.example.data.model.PuzzleEntity

@Database(
    entities = [
        LichessProfile::class,
        ChessGameEntity::class,
        PuzzleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): LichessProfileDao
    abstract fun gameDao(): ChessGameDao
    abstract fun puzzleDao(): PuzzleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_plus_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
