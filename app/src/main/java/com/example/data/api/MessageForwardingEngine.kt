package com.example.data.api

import android.util.Log
import com.example.data.model.ForwardingLog
import com.example.data.model.ForwardingRule
import com.example.data.repository.ForwarderRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

object MessageForwardingEngine {
    private const val TAG = "ForwardingEngine"

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling

    // In-memory tracker for botId -> last processed updateId
    private val lastUpdateIds = mutableMapOf<Int, Long>()

    /**
     * Starts the automated long-polling forwarding thread in the background.
     */
    fun startForwarding(repository: ForwarderRepository) {
        if (_isPolling.value) return
        _isPolling.value = true

        job = scope.launch {
            while (isActive) {
                try {
                    val activeBots = repository.getActiveBots()
                    for (bot in activeBots) {
                        val rules = repository.getActiveRulesForBot(bot.id)
                        if (rules.isEmpty()) continue

                        val lastId = lastUpdateIds[bot.id] ?: 0L
                        // Run network request on IO context
                        val updates = withContext(Dispatchers.IO) {
                            ForwarderService.getUpdates(bot.token, lastId)
                        }
                        
                        for (update in updates) {
                            // Only process if update is newer than last processed update ID
                            if (update.updateId > lastId) {
                                lastUpdateIds[bot.id] = update.updateId
                                
                                val message = update.message ?: continue
                                
                                for (rule in rules) {
                                    processRuleForMessage(repository, rule, message, bot.token)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in forwarding loop", e)
                }
                delay(3000) // Poll every 3 seconds
            }
        }
    }

    /**
     * Stops the background forwarding engine.
     */
    fun stopForwarding() {
        job?.cancel()
        job = null
        _isPolling.value = false
    }

    private suspend fun processRuleForMessage(
        repository: ForwarderRepository,
        rule: ForwardingRule,
        message: ForwarderService.TelegramMessage,
        botToken: String
    ) {
        val originalText = message.text

        // 1. Chat ID filtering
        if (rule.sourceChatId != "any" && rule.sourceChatId.trim().isNotEmpty()) {
            if (rule.sourceChatId.trim() != message.chatId.trim()) {
                return
            }
        }

        // 2. Message type filtering
        if (rule.messageTypeFilter == "TEXT_ONLY" && message.type != "TEXT") {
            logFiltered(repository, rule, message, "Filtered out: Message is not TEXT (type: ${message.type})")
            return
        }
        if (rule.messageTypeFilter == "MEDIA_ONLY" && (message.type == "TEXT" || message.type == "OTHER")) {
            logFiltered(repository, rule, message, "Filtered out: Message contains no visual media (type: ${message.type})")
            return
        }

        // 3. Word inclusion check
        if (rule.keywordsInclude.trim().isNotEmpty()) {
            val includes = rule.keywordsInclude.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (includes.isNotEmpty()) {
                val matched = includes.any { originalText.lowercase().contains(it) }
                if (!matched) {
                    logFiltered(repository, rule, message, "Filtered out: Missing inclusion keywords [${rule.keywordsInclude}]")
                    return
                }
            }
        }

        // 4. Word exclusion check
        if (rule.keywordsExclude.trim().isNotEmpty()) {
            val excludes = rule.keywordsExclude.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (excludes.isNotEmpty()) {
                val matched = excludes.any { originalText.lowercase().contains(it) }
                if (matched) {
                    logFiltered(repository, rule, message, "Filtered out: Contains forbidden keywords [${rule.keywordsExclude}]")
                    return
                }
            }
        }

        // 5. Apply Gemini AI transformations
        val transformedText = if (rule.aiTransformationType != "NONE") {
            withContext(Dispatchers.IO) {
                GeminiService.transformMessage(originalText, rule.aiTransformationType, rule.aiCustomPrompt)
            }
        } else {
            originalText
        }

        // 6. Build a beautiful Telegram message card
        val formattedTelegramText = buildString {
            append("<b>📢 FORWARDED MESSAGE</b>\n")
            append("<b>👥 Source Chat:</b> ${message.chatTitle} (ID: <code>${message.chatId}</code>)\n")
            if (message.senderName.isNotEmpty()) {
                append("<b>👤 Sender:</b> ${message.senderName}\n")
            }
            append("\n<b>📝 Text content:</b>\n")
            append(transformedText)
        }

        var success = false
        var errorMsg: String? = null

        // 7. Dispatch based on destination type
        try {
            val config = JSONObject(rule.targetConfigJson)
            when (rule.targetType) {
                "TELEGRAM" -> {
                    val targetChatId = config.optString("targetChatId", "").trim()
                    val customToken = config.optString("targetBotToken", "").trim()
                    val tokenToUse = if (customToken.isNotEmpty()) customToken else botToken

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
                        // Discord requires basic markdown instead of HTML
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
            errorMsg = "Dispatch error: ${e.localizedMessage ?: "Unknown JSON error"}"
        }

        // 8. Log the process
        repository.insertLog(
            ForwardingLog(
                ruleId = rule.id,
                ruleName = rule.name,
                sourceMessage = originalText,
                sourceSender = message.senderName,
                processedMessage = transformedText,
                status = if (success) "SUCCESS" else "FAILED",
                errorMessage = errorMsg
            )
        )
    }

    private suspend fun logFiltered(
        repository: ForwarderRepository,
        rule: ForwardingRule,
        message: ForwarderService.TelegramMessage,
        reason: String
    ) {
        repository.insertLog(
            ForwardingLog(
                ruleId = rule.id,
                ruleName = rule.name,
                sourceMessage = message.text,
                sourceSender = message.senderName,
                processedMessage = message.text,
                status = "FILTERED",
                errorMessage = reason
            )
        )
    }
}
