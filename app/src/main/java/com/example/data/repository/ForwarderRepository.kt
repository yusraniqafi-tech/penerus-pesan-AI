package com.example.data.repository

import com.example.data.dao.ForwardingLogDao
import com.example.data.dao.ForwardingRuleDao
import com.example.data.dao.TelegramBotDao
import com.example.data.model.ForwardingLog
import com.example.data.model.ForwardingRule
import com.example.data.model.TelegramBot
import kotlinx.coroutines.flow.Flow

class ForwarderRepository(
    private val telegramBotDao: TelegramBotDao,
    private val forwardingRuleDao: ForwardingRuleDao,
    private val forwardingLogDao: ForwardingLogDao
) {
    // Bots
    val allBots: Flow<List<TelegramBot>> = telegramBotDao.getAllBotsFlow()
    
    suspend fun getActiveBots(): List<TelegramBot> = telegramBotDao.getActiveBots()
    suspend fun getBotById(id: Int): TelegramBot? = telegramBotDao.getBotById(id)
    suspend fun insertBot(bot: TelegramBot): Long = telegramBotDao.insertBot(bot)
    suspend fun updateBot(bot: TelegramBot) = telegramBotDao.updateBot(bot)
    suspend fun deleteBot(bot: TelegramBot) = telegramBotDao.deleteBot(bot)
    suspend fun deleteBotById(id: Int) = telegramBotDao.deleteBotById(id)

    // Rules
    val allRules: Flow<List<ForwardingRule>> = forwardingRuleDao.getAllRulesFlow()
    
    suspend fun getActiveRules(): List<ForwardingRule> = forwardingRuleDao.getActiveRules()
    suspend fun getActiveRulesForBot(botId: Int): List<ForwardingRule> = forwardingRuleDao.getActiveRulesForBot(botId)
    suspend fun getRuleById(id: Int): ForwardingRule? = forwardingRuleDao.getRuleById(id)
    suspend fun insertRule(rule: ForwardingRule): Long = forwardingRuleDao.insertRule(rule)
    suspend fun updateRule(rule: ForwardingRule) = forwardingRuleDao.updateRule(rule)
    suspend fun deleteRule(rule: ForwardingRule) = forwardingRuleDao.deleteRule(rule)
    suspend fun deleteRuleById(id: Int) = forwardingRuleDao.deleteRuleById(id)

    // Logs
    val allLogs: Flow<List<ForwardingLog>> = forwardingLogDao.getAllLogsFlow()
    
    suspend fun insertLog(log: ForwardingLog): Long = forwardingLogDao.insertLog(log)
    suspend fun clearAllLogs() = forwardingLogDao.clearAllLogs()
    suspend fun deleteLogById(id: Int) = forwardingLogDao.deleteLogById(id)
}
