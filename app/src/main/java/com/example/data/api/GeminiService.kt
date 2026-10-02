package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Checks if the Gemini API key is validly configured.
     */
    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Uses Gemini AI to transform, translate or summarize the message before forwarding.
     */
    fun transformMessage(
        messageText: String,
        transformationType: String,
        customPrompt: String
    ): String {
        if (transformationType == "NONE" || messageText.isEmpty()) {
            return messageText
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API Key is not configured. Returning original text.")
            return messageText
        }

        val prompt = when (transformationType) {
            "SUMMARIZE" -> "Summarize the following message briefly, keeping critical details intact. Do not add introductory comments or preambles, just return the exact summary text:\n\n$messageText"
            "TRANSLATE" -> {
                val targetLang = if (customPrompt.trim().isNotEmpty()) customPrompt.trim() else "English"
                "Translate the following message into $targetLang. Keep links, hashtags, and formatting. Do not add explanatory remarks, just return the translated text:\n\n$messageText"
            }
            "CUSTOM" -> {
                "$customPrompt\n\nOriginal Message:\n$messageText"
            }
            else -> return messageText
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        return try {
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            val partObj = JSONObject().apply {
                                put("text", prompt)
                            }
                            put(partObj)
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
            }

            val body = requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val candidates = json.getJSONArray("candidates")
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val textResult = parts.getJSONObject(0).getString("text")
                    textResult.trim()
                } else {
                    Log.e(TAG, "Gemini API call failed with code ${response.code}: $bodyStr")
                    messageText
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call Gemini API", e)
            messageText
        }
    }
}
