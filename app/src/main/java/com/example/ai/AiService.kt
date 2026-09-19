package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class AiService(
    private var customApiKey: String? = null
) : AiProvider {

    companion object {
        private const val TAG = "AiService"
        private val MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-flash-8b",
            "gemini-1.5-pro"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun updateApiKey(apiKey: String) {
        customApiKey = apiKey.trim()
    }

    private fun getResolvedApiKey(): String {
        val custom = customApiKey
        if (!custom.isNullOrBlank()) return custom
        return BuildConfig.GEMINI_API_KEY
    }

    override suspend fun generateText(prompt: String, systemInstruction: String?): Result<String> =
        withContext(Dispatchers.IO) {
            val key = getResolvedApiKey()
            if (key.isBlank() || key == "DEFAULT_KEY") {
                return@withContext Result.failure(
                    IllegalStateException("AI functionality requires an API key. Please configure your key in Settings.")
                )
            }

            for (model in MODELS) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                    val rootJson = JSONObject()
                    val contentsArray = JSONArray()
                    val contentObj = JSONObject()
                    val partsArray = JSONArray()

                    val promptPart = JSONObject().put("text", prompt)
                    partsArray.put(promptPart)
                    contentObj.put("parts", partsArray)
                    contentsArray.put(contentObj)
                    rootJson.put("contents", contentsArray)

                    if (!systemInstruction.isNullOrBlank()) {
                        val sysInstructionObj = JSONObject()
                        val sysParts = JSONArray().put(JSONObject().put("text", systemInstruction))
                        sysInstructionObj.put("parts", sysParts)
                        rootJson.put("systemInstruction", sysInstructionObj)
                    }

                    val request = Request.Builder()
                        .url(url)
                        .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val parsed = JSONObject(responseBody)
                        val text = parsed.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        return@withContext Result.success(text.trim())
                    } else if (response.code == 429 || response.code == 503 || response.code == 500 || response.code == 502 || response.code == 504) {
                        Log.w(TAG, "Model $model temporary error (${response.code}), trying next model fallback...")
                        continue
                    } else {
                        Log.e(TAG, "Gemini API error ($model): ${response.code} $responseBody")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Request to $model failed: ${e.message}")
                }
            }

            Result.failure(Exception("All AI model endpoints are currently unavailable. Please check your connection or key."))
        }

    override suspend fun analyzeImage(bitmap: Bitmap, prompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            val key = getResolvedApiKey()
            if (key.isBlank() || key == "DEFAULT_KEY") {
                return@withContext Result.failure(
                    IllegalStateException("Vision analysis requires a configured API key.")
                )
            }

            val base64Image = bitmapToBase64(bitmap)
            for (model in MODELS) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                    val rootJson = JSONObject()
                    val contentsArray = JSONArray()
                    val contentObj = JSONObject()
                    val partsArray = JSONArray()

                    partsArray.put(JSONObject().put("text", prompt))
                    val inlineData = JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Image)
                    }
                    partsArray.put(JSONObject().put("inlineData", inlineData))

                    contentObj.put("parts", partsArray)
                    contentsArray.put(contentObj)
                    rootJson.put("contents", contentsArray)

                    val request = Request.Builder()
                        .url(url)
                        .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val parsed = JSONObject(responseBody)
                        val text = parsed.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        return@withContext Result.success(text.trim())
                    } else if (response.code == 429 || response.code == 503 || response.code == 500 || response.code == 502 || response.code == 504) {
                        Log.w(TAG, "Vision model $model temporary error (${response.code}), trying next fallback...")
                        continue
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Vision request failed for $model: ${e.message}")
                }
            }
            Result.failure(Exception("Could not analyze image with vision model."))
        }

    override suspend fun planAction(userQuery: String, screenContext: String?): Result<AiActionPlan> {
        val systemPrompt = """
            You are SIGMA, an Android Assistant planner.
            Analyze the user command and return ONLY a JSON object with:
            {
               "intent": "LAUNCH_APP" | "SEARCH" | "SCREEN_ACTION" | "DEVICE_CONTROL" | "CALL" | "SMS" | "GENERAL",
               "target": "string",
               "parameters": {},
               "steps": ["step1", "step2"],
               "requires_confirmation": boolean,
               "confidence": float
            }
            Do not include markdown or explanations. Return valid JSON only.
        """.trimIndent()

        val fullPrompt = if (screenContext != null) {
            "User command: $userQuery\nVisible screen context: $screenContext"
        } else {
            "User command: $userQuery"
        }

        val result = generateText(fullPrompt, systemPrompt)
        return result.mapCatching { jsonText ->
            val cleanJson = jsonText.substringAfter("{").substringBeforeLast("}")
            val fullObj = JSONObject("{$cleanJson}")

            val intent = fullObj.optString("intent", "GENERAL")
            val target = fullObj.optString("target", "")
            val paramsObj = fullObj.optJSONObject("parameters")
            val params = mutableMapOf<String, String>()
            paramsObj?.keys()?.forEach { key ->
                params[key] = paramsObj.getString(key)
            }

            val stepsArray = fullObj.optJSONArray("steps")
            val steps = mutableListOf<String>()
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    steps.add(stepsArray.getString(i))
                }
            }

            val requiresConf = fullObj.optBoolean("requires_confirmation", false)
            val conf = fullObj.optDouble("confidence", 0.9).toFloat()

            AiActionPlan(
                intent = intent,
                target = target,
                parameters = params,
                steps = steps,
                requiresConfirmation = requiresConf,
                confidence = conf
            )
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
