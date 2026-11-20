package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        DocumentEntity::class,
        DocumentPageChunkEntity::class,
        DocumentAnnotationEntity::class,
        MemoryEntity::class,
        PyqEntity::class,
        SavedVideoEntity::class,
        StudyTaskEntity::class,
        StudyGoalEntity::class,
        StudyActivityLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SukoonDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun libraryDao(): LibraryDao
    abstract fun memoryDao(): MemoryDao
    abstract fun pyqDao(): PyqDao
    abstract fun videoDao(): VideoDao
    abstract fun plannerDao(): PlannerDao

    companion object {
        @Volatile
        private var INSTANCE: SukoonDatabase? = null

        fun getInstance(context: Context): SukoonDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SukoonDatabase::class.java,
                    "sukoon_ai_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
