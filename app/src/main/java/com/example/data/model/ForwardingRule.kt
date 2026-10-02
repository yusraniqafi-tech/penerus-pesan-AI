package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forwarding_rules")
data class ForwardingRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sourceBotId: Int, // Refers to TelegramBot.id
    val sourceBotToken: String, // Cached token for quick processing
    val sourceChatId: String = "any", // Specific chat ID or "any"
    val targetType: String, // "TELEGRAM", "DISCORD", "WEBHOOK"
    val targetConfigJson: String, // Configuration parameters (JSON)
    val keywordsInclude: String = "", // Comma-separated inclusion keywords
    val keywordsExclude: String = "", // Comma-separated exclusion keywords
    val messageTypeFilter: String = "ALL", // "ALL", "TEXT_ONLY", "MEDIA_ONLY"
    val aiTransformationType: String = "NONE", // "NONE", "SUMMARIZE", "TRANSLATE", "CUSTOM"
    val aiCustomPrompt: String = "", // Custom prompt for Gemini
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
