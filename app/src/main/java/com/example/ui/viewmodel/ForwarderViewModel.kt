package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ForwarderService
import com.example.data.api.GeminiService
import com.example.data.api.MessageForwardingEngine
import com.example.data.database.AppDatabase
import com.example.data.model.ForwardingLog
import com.example.data.model.ForwardingRule
import com.example.data.model.TelegramBot
import com.example.data.repository.ForwarderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForwarderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ForwarderRepository
    
    val bots: StateFlow<List<TelegramBot>>
    val rules: StateFlow<List<ForwardingRule>>
    val logs: StateFlow<List<ForwardingLog>>
    val isPolling: StateFlow<Boolean> = MessageForwardingEngine.isPolling

    // Validation state for Bot addition
    private val _botValidationState = MutableStateFlow<ValidationState>(ValidationState.Idle)
    val botValidationState: StateFlow<ValidationState> = _botValidationState

    // General messages (toasts/alerts)
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ForwarderRepository(
            database.telegramBotDao(),
            database.forwardingRuleDao(),
            database.forwardingLogDao()
        )

        bots = repository.allBots
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        rules = repository.allRules
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        logs = repository.allLogs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Start forwarding engine automatically if we have active bots/rules and it was on
        viewModelScope.launch {
            if (repository.getActiveBots().isNotEmpty() && repository.getActiveRules().isNotEmpty()) {
                MessageForwardingEngine.startForwarding(repository)
            }
        }
    }

    fun toggleForwarding() {
        viewModelScope.launch {
            if (isPolling.value) {
                MessageForwardingEngine.stopForwarding()
                _toastMessage.emit("Mesin penerusan dinonaktifkan")
            } else {
                val activeBots = repository.getActiveBots()
                if (activeBots.isEmpty()) {
                    _toastMessage.emit("Tambahkan setidaknya 1 Bot Telegram aktif terlebih dahulu!")
                    return@launch
                }
                MessageForwardingEngine.startForwarding(repository)
                _toastMessage.emit("Mesin penerusan diaktifkan!")
            }
        }
    }

    // --- BOT METHODS ---
    
    fun validateAndAddBot(token: String) {
        if (token.isBlank()) {
            viewModelScope.launch { _botValidationState.value = ValidationState.Error("Token tidak boleh kosong") }
            return
        }

        _botValidationState.value = ValidationState.Checking

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ForwarderService.validateBotToken(token.trim())
            }

            if (result != null) {
                val existingBots = bots.value
                val alreadyExists = existingBots.any { it.token == token.trim() }
                
                if (alreadyExists) {
                    _botValidationState.value = ValidationState.Error("Bot ini sudah ditambahkan sebelumnya")
                } else {
                    val newBot = TelegramBot(
                        token = token.trim(),
                        username = result.username,
                        name = result.name
                    )
                    repository.insertBot(newBot)
                    _botValidationState.value = ValidationState.Success(result.name)
                    _toastMessage.emit("Bot @${result.username} berhasil ditambahkan!")
                }
            } else {
                _botValidationState.value = ValidationState.Error("Token bot tidak valid atau koneksi internet terganggu")
            }
        }
    }

    fun deleteBot(bot: TelegramBot) {
        viewModelScope.launch {
            // Check if there are active rules using this bot
            val rulesUsingBot = rules.value.filter { it.sourceBotId == bot.id }
            for (rule in rulesUsingBot) {
                repository.deleteRule(rule)
            }
            repository.deleteBot(bot)
            _toastMessage.emit("Bot @${bot.username} dan aturan terkait berhasil dihapus")
            
            // Check if we should stop engine
            if (repository.getActiveBots().isEmpty()) {
                MessageForwardingEngine.stopForwarding()
            }
        }
    }

    fun toggleBotStatus(bot: TelegramBot) {
        viewModelScope.launch {
            val updatedBot = bot.copy(isActive = !bot.isActive)
            repository.updateBot(updatedBot)
            
            // Sync all rules using this bot to disabled/enabled
            val botRules = rules.value.filter { it.sourceBotId == bot.id }
            for (rule in botRules) {
                repository.updateRule(rule.copy(isActive = updatedBot.isActive))
            }
            
            _toastMessage.emit("Status Bot @${bot.username} diubah")
            
            // Sync polling engine
            if (repository.getActiveBots().isEmpty()) {
                MessageForwardingEngine.stopForwarding()
            }
        }
    }

    fun resetBotValidationState() {
        _botValidationState.value = ValidationState.Idle
    }

    // --- RULE METHODS ---

    fun addRule(
        name: String,
        sourceBot: TelegramBot,
        sourceChatId: String,
        targetType: String,
        targetConfigJson: String,
        keywordsInclude: String,
        keywordsExclude: String,
        messageTypeFilter: String,
        aiTransformationType: String,
        aiCustomPrompt: String
    ) {
        viewModelScope.launch {
            val rule = ForwardingRule(
                name = name.ifBlank { "Aturan Baru" },
                sourceBotId = sourceBot.id,
                sourceBotToken = sourceBot.token,
                sourceChatId = sourceChatId.ifBlank { "any" },
                targetType = targetType,
                targetConfigJson = targetConfigJson,
                keywordsInclude = keywordsInclude,
                keywordsExclude = keywordsExclude,
                messageTypeFilter = messageTypeFilter,
                aiTransformationType = aiTransformationType,
                aiCustomPrompt = aiCustomPrompt,
                isActive = sourceBot.isActive
            )
            repository.insertRule(rule)
            _toastMessage.emit("Aturan '${rule.name}' berhasil disimpan!")

            // If the engine wasn't running, start it
            if (!isPolling.value && repository.getActiveBots().isNotEmpty()) {
                MessageForwardingEngine.startForwarding(repository)
            }
        }
    }

    fun deleteRule(rule: ForwardingRule) {
        viewModelScope.launch {
            repository.deleteRule(rule)
            _toastMessage.emit("Aturan '${rule.name}' dihapus")
        }
    }

    fun toggleRuleStatus(rule: ForwardingRule) {
        viewModelScope.launch {
            val updatedRule = rule.copy(isActive = !rule.isActive)
            repository.updateRule(updatedRule)
            _toastMessage.emit("Aturan '${rule.name}' " + if (updatedRule.isActive) "diaktifkan" else "dinonaktifkan")
        }
    }

    // --- LOG METHODS ---

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
            _toastMessage.emit("Riwayat log berhasil dibersihkan")
        }
    }

    fun deleteLog(log: ForwardingLog) {
        viewModelScope.launch {
            repository.deleteLogById(log.id)
            _toastMessage.emit("Log dihapus")
        }
    }

    fun retryForwarding(log: ForwardingLog) {
        viewModelScope.launch {
            val rule = repository.getRuleById(log.ruleId)
            if (rule == null) {
                _toastMessage.emit("Gagal: Aturan terkait sudah dihapus!")
                return@launch
            }

            _toastMessage.emit("Mencoba kirim ulang...")

            val formattedTelegramText = buildString {
                append("<b>📢 FORWARDED MESSAGE (RETRY)</b>\n")
                append("<b>👥 Source:</b> Retried from history\n")
                append("\n<b>📝 Text content:</b>\n")
                append(log.processedMessage)
            }

            var success = false
            var errorMsg: String? = null

            try {
                val config = org.json.JSONObject(rule.targetConfigJson)
                when (rule.targetType) {
                    "TELEGRAM" -> {
                        val targetChatId = config.optString("targetChatId", "").trim()
                        val customToken = config.optString("targetBotToken", "").trim()
                        val tokenToUse = if (customToken.isNotEmpty()) customToken else rule.sourceBotToken

                        if (targetChatId.isNotEmpty()) {
                            val result = withContext(Dispatchers.IO) {
                                ForwarderService.sendTelegramMessage(tokenToUse, targetChatId, formattedTelegramText)
                            }
                            success = result.first
                            errorMsg = result.second
                        } else {
                            errorMsg = "Target configuration error: Chat ID is empty"
                        }
                    }
                    "DISCORD" -> {
                        val webhookUrl = config.optString("discordWebhookUrl", "").trim()
                        if (webhookUrl.isNotEmpty()) {
                            val markdownText = formattedTelegramText
                                .replace("<b>", "**").replace("</b>", "**")
                                .replace("<code>", "`").replace("</code>", "`")
                            val result = withContext(Dispatchers.IO) {
                                ForwarderService.sendDiscordWebhook(webhookUrl, markdownText)
                            }
                            success = result.first
                            errorMsg = result.second
                        } else {
                            errorMsg = "Target configuration error: Discord webhook URL is empty"
                        }
                    }
                    "WEBHOOK" -> {
                        val webhookUrl = config.optString("webhookUrl", "").trim()
                        if (webhookUrl.isNotEmpty()) {
                            val result = withContext(Dispatchers.IO) {
                                ForwarderService.sendCustomWebhook(webhookUrl, formattedTelegramText)
                            }
                            success = result.first
                            errorMsg = result.second
                        } else {
                            errorMsg = "Target configuration error: Webhook URL is empty"
                        }
                    }
                }
            } catch (e: Exception) {
                errorMsg = "Retry error: ${e.localizedMessage ?: "Unknown error"}"
            }

            repository.insertLog(
                ForwardingLog(
                    ruleId = rule.id,
                    ruleName = "${rule.name} (Retry)",
                    sourceMessage = log.sourceMessage,
                    sourceSender = log.senderName(), // Safe fallback helper
                    processedMessage = log.processedMessage,
                    status = if (success) "SUCCESS" else "FAILED",
                    errorMessage = errorMsg
                )
            )

            if (success) {
                _toastMessage.emit("Berhasil mengirim ulang pesan!")
            } else {
                _toastMessage.emit("Kirim ulang gagal: $errorMsg")
            }
        }
    }

    private fun ForwardingLog.senderName(): String {
        return if (this.sourceSender.isNotBlank()) this.sourceSender else "Unknown"
    }

    // --- GEMINI CHECK ---
    fun isGeminiConfigured(): Boolean {
        return GeminiService.isApiKeyConfigured()
    }

    sealed interface ValidationState {
        object Idle : ValidationState
        object Checking : ValidationState
        data class Success(val botName: String) : ValidationState
        data class Error(val message: String) : ValidationState
    }
}
