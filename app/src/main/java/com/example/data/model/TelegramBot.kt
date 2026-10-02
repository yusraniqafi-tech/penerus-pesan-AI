package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telegram_bots")
data class TelegramBot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val token: String,
    val username: String,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
