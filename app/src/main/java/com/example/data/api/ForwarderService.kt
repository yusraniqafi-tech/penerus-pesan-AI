package com.example.data.api

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ForwarderService {
    private const val TAG = "ForwarderService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    data class BotInfo(val username: String, val name: String)
    
    data class TelegramMessage(
        val messageId: Long,
        val chatId: String,
        val chatTitle: String,
        val senderName: String,
        val text: String,
        val date: Long,
        val type: String // "TEXT", "PHOTO", "DOCUMENT", "OTHER"
    )

    data class TelegramUpdate(
        val updateId: Long,
        val message: TelegramMessage?
    )

    /**
     * Validates a Telegram Bot Token by calling the official getMe endpoint.
     * Returns BotInfo if valid, otherwise null.
     */
    suspend fun validateBotToken(token: String): BotInfo? {
        val url = "https://api.telegram.org/bot$token/getMe"
        val request = Request.Builder().url(url).build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                val json = JSONObject(bodyString)
                if (json.optBoolean("ok")) {
                    val result = json.getJSONObject("result")
                    val username = result.getString("username")
                    val firstName = result.getString("first_name")
                    BotInfo(username, firstName)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating bot token", e)
            null
        }
    }

    /**
     * Polls updates from Telegram getUpdates.
     */
    fun getUpdates(token: String, offset: Long): List<TelegramUpdate> {
        val url = "https://api.telegram.org/bot$token/getUpdates?offset=$offset&timeout=5"
        val request = Request.Builder().url(url).build()
        val updates = mutableListOf<TelegramUpdate>()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyString = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyString)
                if (json.optBoolean("ok")) {
                    val resultArray = json.getJSONArray("result")
                    for (i in 0 until resultArray.length()) {
                        val updateObj = resultArray.getJSONObject(i)
                        val updateId = updateObj.getLong("update_id")
                        
                        // Extract message or channel_post
                        val messageObj = updateObj.optJSONObject("message") 
                            ?: updateObj.optJSONObject("channel_post")
                            ?: updateObj.optJSONObject("edited_message")
                            ?: updateObj.optJSONObject("edited_channel_post")

                        if (messageObj != null) {
                            val messageId = messageObj.getLong("message_id")
                            val chatObj = messageObj.getJSONObject("chat")
                            val chatId = chatObj.getLong("id").toString()
                            val chatTitle = chatObj.optString("title", chatObj.optString("username", "Private Chat"))
                            
                            val fromObj = messageObj.optJSONObject("from")
                            val senderName = if (fromObj != null) {
                                "${fromObj.optString("first_name", "")} ${fromObj.optString("last_name", "")}".trim()
                            } else {
                                chatTitle
                            }
                            
                            var text = messageObj.optString("text", "")
                            if (text.isEmpty()) {
                                text = messageObj.optString("caption", "")
                            }
                            
                            val date = messageObj.getLong("date")
                            
                            var type = "TEXT"
                            if (messageObj.has("photo")) {
                                type = "PHOTO"
                            } else if (messageObj.has("document") || messageObj.has("video") || messageObj.has("audio")) {
                                type = "DOCUMENT"
                            } else if (text.isEmpty()) {
                                type = "OTHER"
                            }

                            updates.add(
                                TelegramUpdate(
                                    updateId = updateId,
                                    message = TelegramMessage(
                                        messageId = messageId,
                                        chatId = chatId,
                                        chatTitle = chatTitle,
                                        senderName = senderName,
                                        text = text,
                                        date = date,
                                        type = type
                                    )
                                )
                            )
                        } else {
                            // Non-message updates
                            updates.add(TelegramUpdate(updateId, null))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Telegram updates", e)
        }
        return updates
    }

    /**
     * Forwards a message to another Telegram channel or chat.
     */
    fun sendTelegramMessage(token: String, chatId: String, text: String): Pair<Boolean, String?> {
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val payload = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text)
            put("parse_mode", "HTML")
        }
        
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(url).post(body).build()
        
        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    if (json.optBoolean("ok")) {
                        Pair(true, null)
                    } else {
                        Pair(false, json.optString("description", "Unknown API error"))
                    }
                } else {
                    Pair(false, "HTTP ${response.code}: $bodyStr")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Telegram message", e)
            Pair(false, e.localizedMessage ?: "Network error")
        }
    }

    /**
     * Forwards a message to a Discord Webhook.
     */
    fun sendDiscordWebhook(webhookUrl: String, text: String): Pair<Boolean, String?> {
        val payload = JSONObject().apply {
            put("content", text)
        }
        
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(webhookUrl).post(body).build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204) {
                    Pair(true, null)
                } else {
                    Pair(false, "HTTP ${response.code}: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Discord webhook", e)
            Pair(false, e.localizedMessage ?: "Network error")
        }
    }

    /**
     * Forwards a message to a custom HTTP POST webhook.
     */
    fun sendCustomWebhook(url: String, text: String): Pair<Boolean, String?> {
        val payload = JSONObject().apply {
            put("message", text)
            put("timestamp", System.currentTimeMillis())
        }
        
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(url).post(body).build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Pair(true, null)
                } else {
                    Pair(false, "HTTP ${response.code}: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Custom Webhook", e)
            Pair(false, e.localizedMessage ?: "Network error")
        }
    }
}
