package com.example.data.dao

import androidx.room.*
import com.example.data.model.TelegramBot
import kotlinx.coroutines.flow.Flow

@Dao
interface TelegramBotDao {
    @Query("SELECT * FROM telegram_bots ORDER BY createdAt DESC")
    fun getAllBotsFlow(): Flow<List<TelegramBot>>

    @Query("SELECT * FROM telegram_bots WHERE isActive = 1")
    suspend fun getActiveBots(): List<TelegramBot>

    @Query("SELECT * FROM telegram_bots WHERE id = :id LIMIT 1")
    suspend fun getBotById(id: Int): TelegramBot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBot(bot: TelegramBot): Long

    @Update
    suspend fun updateBot(bot: TelegramBot)

    @Delete
    suspend fun deleteBot(bot: TelegramBot)

    @Query("DELETE FROM telegram_bots WHERE id = :id")
    suspend fun deleteBotById(id: Int)
}
