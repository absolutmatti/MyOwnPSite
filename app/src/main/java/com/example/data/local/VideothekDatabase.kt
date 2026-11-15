package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PerformerEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VideothekDatabase : RoomDatabase() {
    abstract fun performerDao(): PerformerDao

    companion object {
        @Volatile
        private var instance: VideothekDatabase? = null

        fun getInstance(context: Context): VideothekDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideothekDatabase::class.java,
                    "videothek_tv.db",
                ).build().also { instance = it }
            }
        }
    }
}
