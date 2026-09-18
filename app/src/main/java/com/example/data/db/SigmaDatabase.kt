package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ActionAuditLog::class,
        UserMemory::class,
        CommandHistoryItem::class,
        RecordedWorkflow::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SigmaDatabase : RoomDatabase() {
    abstract fun sigmaDao(): SigmaDao

    companion object {
        @Volatile
        private var INSTANCE: SigmaDatabase? = null

        fun getInstance(context: Context): SigmaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SigmaDatabase::class.java,
                    "sigma_database"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
