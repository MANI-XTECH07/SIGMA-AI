package com.example.router

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.example.ai.ActionPlanner
import com.example.ai.AiProvider
import com.example.apps.AppResolutionResult
import com.example.apps.AppResolver
import com.example.automation.AutomationEngine
import com.example.automation.AutomationPlan
import com.example.data.ConversationDao
import com.example.data.db.CommandHistoryItem
import com.example.data.db.SigmaRepository
import com.example.device.DeviceController
import com.example.device.TorchController
import com.example.screen.ScreenAnalysisManager
import com.example.service.SigmaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class RouterResult {
    data class Spoken(val text: String, val actionType: String? = null) : RouterResult()
    data class NeedConfirmation(
        val title: String,
        val description: String,
        val actionType: String,
        val onConfirm: suspend () -> RouterResult
    ) : RouterResult()
}

class ActionRouter(
    private val context: Context,
    private val appResolver: AppResolver,
    private val deviceController: DeviceController,
    private val torchController: TorchController,
    private val screenAnalysisManager: ScreenAnalysisManager,
    private val actionPlanner: ActionPlanner,
    private val automationEngine: AutomationEngine,
    private val aiProvider: AiProvider,
    private val repository: SigmaRepository,
    private val conversationDao: ConversationDao
) {

    companion object {
        private const val TAG = "ActionRouter"
    }

    suspend fun routeUserSpeech(rawTranscript: String): RouterResult = withContext(Dispatchers.IO) {
        val trimmed = rawTranscript.trim()
        val lower = trimmed.lowercase()

        Log.d(TAG, "Routing transcript: \"$trimmed\"")

        // 1. SCREEN VISION & ANALYSIS
        if (lower.contains("analyze screen") || lower.contains("what is on my screen") ||
            lower.contains("kya dikh raha hai") || lower.contains("screen dekho") ||
            lower.contains("what's on my screen") || lower.contains("read screen")
        ) {
            val result = screenAnalysisManager.analyzeCurrentScreen()
            logHistory(trimmed, true, result.summary)
            return@withContext RouterResult.Spoken(result.summary, "SCREEN_ANALYSIS")
        }

        if (lower.contains("take screenshot") || lower.contains("screenshot lo") || lower.contains("screen capture")) {
            val service = SigmaAccessibilityService.instance
            if (service != null) {
                service.takeScreenshotCompat { bitmap ->
                    Log.d(TAG, "Screenshot captured via accessibility: ${bitmap != null}")
                }
                val msg = "Taking screenshot."
                logHistory(trimmed, true, msg)
                return@withContext RouterResult.Spoken(msg, "SCREENSHOT")
            } else {
                val msg = "Accessibility service required to capture screenshot."
                logHistory(trimmed, false, msg)
                return@withContext RouterResult.Spoken(msg, "SCREENSHOT")
            }
        }

        // 2. HARDWARE & DEVICE CONTROLS (Flashlight, Volume, Brightness)
        if (lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("torch jalao")) {
            val success = torchController.turnOnTorch()
            val msg = if (success) "Flashlight turned on." else "Could not enable flashlight."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "TORCH_ON")
        }

        if (lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("torch band karo")) {
            val success = torchController.turnOffTorch()
            val msg = if (success) "Flashlight turned off." else "Could not disable flashlight."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "TORCH_OFF")
        }

        if (lower.contains("volume up") || lower.contains("awaz badhao")) {
            val success = deviceController.adjustVolume(increase = true)
            val msg = if (success) "Volume increased." else "Volume adjusted."
            logHistory(trimmed, true, msg)
            return@withContext RouterResult.Spoken(msg, "VOLUME")
        }

        if (lower.contains("volume down") || lower.contains("awaz kam karo")) {
            val success = deviceController.adjustVolume(increase = false)
            val msg = if (success) "Volume decreased." else "Volume adjusted."
            logHistory(trimmed, true, msg)
            return@withContext RouterResult.Spoken(msg, "VOLUME")
        }

        // 3. CALLING & CONTACTS (Confirmation required)
        if (lower.startsWith("call ") || lower.contains("ko call karo")) {
            val target = trimmed.replace("call", "", ignoreCase = true)
                .replace("ko call karo", "", ignoreCase = true)
                .trim()

            val foundPhone = deviceController.findContactPhoneNumber(target) ?: target

            return@withContext RouterResult.NeedConfirmation(
                title = "Confirm Phone Call",
                description = "Do you want SIGMA to call $target ($foundPhone)?",
                actionType = "CALL",
                onConfirm = {
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${Uri.encode(foundPhone)}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(dialIntent)
                    logHistory(trimmed, true, "Placing call to $target.")
                    RouterResult.Spoken("Placing call to $target.")
                }
            )
        }

        // 4. SEND MESSAGE / SMS (Confirmation required)
        if (lower.startsWith("message ") || lower.startsWith("sms ") || lower.contains("ko message bhejo")) {
            val payload = trimmed.substringAfter(" ").trim()
            return@withContext RouterResult.NeedConfirmation(
                title = "Confirm Outgoing Message",
                description = "Send message: \"$payload\"?",
                actionType = "MESSAGE",
                onConfirm = {
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:")
                        putExtra("sms_body", payload)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(smsIntent)
                    logHistory(trimmed, true, "Opening message composer.")
                    RouterResult.Spoken("Opening message composer.")
                }
            )
        }

        // 5. STRUCTURED AUTOMATION BEAST LOOP (Chained multi-step or deep UI actions)
        val plan = actionPlanner.planUserCommand(trimmed)
        if (plan.steps.isNotEmpty()) {
            val report = automationEngine.executePlanWithReport(plan) { progress ->
                Log.d(TAG, "Plan progress: $progress")
            }
            logHistory(trimmed, report.success, report.finalMessage)
            return@withContext RouterResult.Spoken(report.finalMessage, "AUTOMATION")
        }

        // 6. DIRECT APP LAUNCHING (Dynamic resolution using Android PackageManager)
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.contains("kholo") || lower.contains("chalao")) {
            val query = trimmed.replace("open", "", ignoreCase = true)
                .replace("launch", "", ignoreCase = true)
                .replace("kholo", "", ignoreCase = true)
                .replace("chalao", "", ignoreCase = true)
                .replace("sigma", "", ignoreCase = true)
                .trim()

            val resolution = appResolver.resolveAndLaunch(query)
            val (success, speech) = when (resolution) {
                is AppResolutionResult.Success -> true to "Opening ${resolution.app.label}."
                is AppResolutionResult.MultipleMatches -> true to "Opening ${resolution.candidates.first().label}."
                is AppResolutionResult.NotFound -> false to "I couldn't find an installed app named $query."
            }
            logHistory(trimmed, success, speech)
            return@withContext RouterResult.Spoken(speech, "APP_LAUNCH")
        }

        // 7. SYSTEM SETTINGS
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            logHistory(trimmed, true, "Opening Wi-Fi settings.")
            return@withContext RouterResult.Spoken("Opening Wi-Fi settings.", "SETTINGS")
        }

        if (lower.contains("bluetooth")) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            logHistory(trimmed, true, "Opening Bluetooth settings.")
            return@withContext RouterResult.Spoken("Opening Bluetooth settings.", "SETTINGS")
        }

        // Fallback: AI response
        return@withContext RouterResult.Spoken("")
    }

    private suspend fun logHistory(command: String, isSuccess: Boolean, shortResult: String) {
        try {
            conversationDao.insertHistory(
                CommandHistoryItem(
                    command = command,
                    timestamp = System.currentTimeMillis(),
                    isSuccess = isSuccess,
                    shortResult = shortResult
                )
            )
            repository.logAction(
                command = command,
                actionType = "EXECUTION",
                target = "SIGMA",
                status = if (isSuccess) "SUCCESS" else "FAILED",
                details = shortResult
            )
        } catch (e: Exception) {
            // Non-blocking log failure
        }
    }
}
