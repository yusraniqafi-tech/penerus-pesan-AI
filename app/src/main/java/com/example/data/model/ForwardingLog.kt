package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forwarding_logs")
data class ForwardingLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ruleId: Int,
    val ruleName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceMessage: String,
    val sourceSender: String,
    val processedMessage: String,
    val status: String, // "SUCCESS", "FAILED", "FILTERED"
    val errorMessage: String? = null
)
