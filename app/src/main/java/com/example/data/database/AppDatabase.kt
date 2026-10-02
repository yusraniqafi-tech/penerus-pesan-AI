package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.ForwardingLogDao
import com.example.data.dao.ForwardingRuleDao
import com.example.data.dao.TelegramBotDao
import com.example.data.model.ForwardingLog
import com.example.data.model.ForwardingRule
import com.example.data.model.TelegramBot

@Database(
    entities = [TelegramBot::class, ForwardingRule::class, ForwardingLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun telegramBotDao(): TelegramBotDao
    abstract fun forwardingRuleDao(): ForwardingRuleDao
    abstract fun forwardingLogDao(): ForwardingLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "teleforwarder_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
