package com.rakshaksetu.app.community.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BlacklistEntry::class],
    version = 1,
    exportSchema = false
)
abstract class BlacklistDatabase : RoomDatabase() {

    abstract fun blacklistDao(): BlacklistDao

    companion object {
        @Volatile
        private var instance: BlacklistDatabase? = null

        fun get(context: Context): BlacklistDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlacklistDatabase::class.java,
                    "rakshak_blacklist.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
