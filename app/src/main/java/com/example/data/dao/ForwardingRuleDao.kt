package com.example.data.dao

import androidx.room.*
import com.example.data.model.ForwardingRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardingRuleDao {
    @Query("SELECT * FROM forwarding_rules ORDER BY createdAt DESC")
    fun getAllRulesFlow(): Flow<List<ForwardingRule>>

    @Query("SELECT * FROM forwarding_rules WHERE isActive = 1")
    suspend fun getActiveRules(): List<ForwardingRule>

    @Query("SELECT * FROM forwarding_rules WHERE sourceBotId = :botId AND isActive = 1")
    suspend fun getActiveRulesForBot(botId: Int): List<ForwardingRule>

    @Query("SELECT * FROM forwarding_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Int): ForwardingRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ForwardingRule): Long

    @Update
    suspend fun updateRule(rule: ForwardingRule)

    @Delete
    suspend fun deleteRule(rule: ForwardingRule)

    @Query("DELETE FROM forwarding_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)
}
