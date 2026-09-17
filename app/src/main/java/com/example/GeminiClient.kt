package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val customApiKey: String? = null
) {
    companion object {
        private const val TAG = "GeminiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        // Candidate models in priority order.
        // 'gemini-3.1-flash-lite-preview' has significantly higher free-tier RPM and ultra-low latency.
        // If a model encounters a 429 rate limit, the client automatically fails over to the next.
        private val CANDIDATE_MODELS = listOf(
            "gemini-3.1-flash-lite-preview",
            "gemini-flash-latest",
            "gemini-3.5-flash"
        )
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getEffectiveApiKey(): String {
        if (!customApiKey.isNullOrBlank()) {
            return customApiKey.trim()
        }
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Throwable) {
            ""
        }
    }

    suspend fun generateAssistantReply(
        userPrompt: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.success(
                "Hello! I am SIGMA. To connect to full AI intelligence, please provide your Gemini API key in the AI Studio Secrets panel."
            )
        }

        val requestJson = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put(
                            "text",
                            "You are SIGMA, an advanced, intelligent Android voice assistant. " +
                            "Respond concisely, naturally, and warmly in 1 to 2 spoken sentences suitable for text-to-speech. " +
                            "Never use Markdown symbols, bullet points, asterisks, or bold tags because your response is read aloud. " +
                            "Be helpful, direct, and conversational."
                        )
                    })
                })
            })

            val contentsArray = JSONArray()
            for ((role, text) in conversationHistory.takeLast(4)) {
                val geminiRole = if (role.equals("user", ignoreCase = true)) "user" else "model"
                contentsArray.put(JSONObject().apply {
                    put("role", geminiRole)
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    })
                })
            }

            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", userPrompt) })
                })
            })
            put("contents", contentsArray)

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("topP", 0.95)
                put("maxOutputTokens", 150)
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        var lastError: Exception? = null
        var isQuotaExhaustedAcrossModels = false

        // Attempt each candidate model in sequence
        for (model in CANDIDATE_MODELS) {
            try {
                val endpoint = "$BASE_URL/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.code == 429) {
                    Log.w(TAG, "Model $model hit rate limit (429). Trying fallback model...")
                    isQuotaExhaustedAcrossModels = true
                    continue
                }

                if (!response.isSuccessful || responseBody == null) {
                    val errorMsg = "Gemini API error ${response.code}: ${response.message}"
                    Log.e(TAG, "$errorMsg -> $responseBody")
                    lastError = Exception(errorMsg)
                    continue
                }

                val jsonObject = JSONObject(responseBody)
                val candidates = jsonObject.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext Result.success("I processed that, but got an empty response.")
                }

                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val textPart = parts?.optJSONObject(0)?.optString("text")

                val cleanText = textPart
                    ?.replace(Regex("[*#_`~]"), "")
                    ?.trim()
                    ?: "I heard your request."

                return@withContext Result.success(cleanText)
            } catch (e: Exception) {
                Log.e(TAG, "Error trying model $model", e)
                lastError = e
            }
        }

        if (isQuotaExhaustedAcrossModels) {
            return@withContext Result.success(
                "I have temporarily reached the free tier rate limit. Please wait 30 seconds before asking again."
            )
        }

        Result.failure(lastError ?: Exception("Unable to reach Gemini assistant service."))
    }
}
