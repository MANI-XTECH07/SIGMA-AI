package com.example.ai

import android.graphics.Bitmap

data class AiActionPlan(
    val intent: String,
    val target: String,
    val parameters: Map<String, String>,
    val steps: List<String>,
    val requiresConfirmation: Boolean,
    val confidence: Float
)

interface AiProvider {
    suspend fun generateText(prompt: String, systemInstruction: String? = null): Result<String>
    suspend fun analyzeImage(bitmap: Bitmap, prompt: String): Result<String>
    suspend fun planAction(userQuery: String, screenContext: String? = null): Result<AiActionPlan>
}
