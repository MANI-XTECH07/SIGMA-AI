package com.example.router

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import com.example.ai.ActionPlanner
import com.example.ai.VisionService
import com.example.apps.AppResolutionResult
import com.example.apps.AppResolver
import com.example.automation.AutomationEngine
import com.example.data.ConversationDao
import com.example.data.db.CommandHistoryItem
import com.example.data.db.SigmaRepository
import com.example.device.DeviceController
import com.example.device.LockController
import com.example.service.SigmaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class RouterResult {
    data class Spoken(val speechText: String, val actionType: String = "INFO") : RouterResult()
    data class NeedConfirmation(
        val title: String,
        val description: String,
        val actionType: String,
        val onConfirm: suspend () -> RouterResult
    ) : RouterResult()
    data class ContentPreview(
        val title: String,
        val text: String,
        val imageUrl: String? = null
    ) : RouterResult()
}

class ActionRouter(
    private val context: Context,
    private val repository: SigmaRepository,
    private val conversationDao: ConversationDao,
    private val appResolver: AppResolver,
    private val deviceController: DeviceController,
    private val lockController: LockController,
    private val automationEngine: AutomationEngine,
    private val actionPlanner: ActionPlanner,
    private val visionService: VisionService
) {

    suspend fun executeCommand(
        rawPrompt: String,
        activity: Activity? = null,
        metrics: DisplayMetrics? = null
    ): RouterResult = withContext(Dispatchers.IO) {
        val trimmed = rawPrompt.trim()
        val lower = trimmed.lowercase()

        // 1. DEVICE LOCK (Rule 12: Real Phone Locking)
        if (lower.contains("phone lock") || lower.contains("lock phone") || lower == "lock" || lower.contains("screen lock")) {
            val locked = lockController.lockPhone()
            val message = if (locked) "Locking." else "Accessibility Service is required to lock device."
            logHistory(trimmed, locked, message)
            return@withContext RouterResult.Spoken(message, "DEVICE_LOCK")
        }

        // 2. DEVICE UNLOCK (Rule 12: Explain security limitations honestly)
        if (lower.contains("phone unlock") || lower.contains("unlock phone") || lower == "unlock") {
            if (activity != null) {
                lockController.promptSecureUnlock(
                    activity = activity,
                    onUnlocked = { },
                    onCancelled = { }
                )
                val msg = "Please enter your PIN, pattern, or biometric to unlock."
                logHistory(trimmed, true, msg)
                return@withContext RouterResult.Spoken(msg, "DEVICE_UNLOCK")
            } else {
                val msg = "Please turn on your screen and use your biometric or PIN to unlock."
                logHistory(trimmed, true, msg)
                return@withContext RouterResult.Spoken(msg, "DEVICE_UNLOCK")
            }
        }

        // 3. VOLUME CONTROLS (Rule 13)
        if (lower.contains("volume up") || lower.contains("awaz badhao")) {
            val success = deviceController.adjustVolume(true)
            val msg = if (success) "Volume increased." else "Could not adjust volume."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "VOLUME")
        }
        if (lower.contains("volume down") || lower.contains("awaz kam karo")) {
            val success = deviceController.adjustVolume(false)
            val msg = if (success) "Volume decreased." else "Could not adjust volume."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "VOLUME")
        }
        if (lower.contains("mute")) {
            val success = deviceController.setVolumePercentage(0)
            val msg = if (success) "Muted." else "Could not mute volume."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "VOLUME")
        }

        // 4. SCREEN VISION (Rule 8: Real Screen Analysis)
        if (lower.contains("read screen") || lower.contains("what is on my screen") || lower.contains("screen dekho") || lower.contains("explain screen")) {
            val analysis = if (metrics != null) {
                visionService.captureAndExplain(metrics)
            } else {
                val service = SigmaAccessibilityService.instance
                if (service != null) {
                    val texts = service.extractScreenText()
                    if (texts.isNotEmpty()) "Visible on screen: " + texts.take(5).joinToString(", ")
                    else "No text detected on screen."
                } else {
                    "Please enable Accessibility Service or Screen Capture in settings."
                }
            }
            logHistory(trimmed, true, analysis.take(60))
            return@withContext RouterResult.Spoken(analysis, "SCREEN_READ")
        }

        // 5. NAVIGATION & GESTURES
        if (lower == "go home" || lower == "home screen" || lower.contains("home jao")) {
            val service = SigmaAccessibilityService.instance
            val success = service?.goHome() ?: false
            if (!success) launchHomeIntent()
            logHistory(trimmed, true, "Going home.")
            return@withContext RouterResult.Spoken("Going home.", "NAVIGATION")
        }

        if (lower == "go back" || lower == "back" || lower.contains("peeche jao")) {
            val service = SigmaAccessibilityService.instance
            val success = service?.goBack() ?: false
            val msg = if (success) "Navigating back." else "Accessibility Service needed to navigate back."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "NAVIGATION")
        }

        if (lower.contains("scroll down") || lower.contains("neeche scroll")) {
            val service = SigmaAccessibilityService.instance
            val success = service?.scrollForward() ?: false
            val msg = if (success) "Scrolling." else "Cannot scroll."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "SCROLL")
        }

        if (lower.contains("scroll up") || lower.contains("upar scroll")) {
            val service = SigmaAccessibilityService.instance
            val success = service?.scrollBackward() ?: false
            val msg = if (success) "Scrolling." else "Cannot scroll."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "SCROLL")
        }

        // 6. APP LAUNCHING (Rule 10: Dynamic Resolution)
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.contains("kholo")) {
            val query = trimmed.replace("open", "", ignoreCase = true)
                .replace("launch", "", ignoreCase = true)
                .replace("kholo", "", ignoreCase = true)
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

        // 7. CALLING & CONTACTS (Rule 11: Real Contact Search & Confirmation)
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

        // 8. SEND MESSAGE / SMS (Confirmation required)
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

        // 9. SYSTEM SETTINGS
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

        // 10. MULTI-STEP AUTOMATION BEAST LOOP (Rules 14-17)
        val plan = actionPlanner.planUserCommand(trimmed)
        if (plan.steps.isNotEmpty()) {
            val completed = automationEngine.executePlan(plan) { progressMsg -> }
            val outcome = if (completed) "Done." else "Action failed."
            logHistory(trimmed, completed, outcome)
            return@withContext RouterResult.Spoken(outcome, "AUTOMATION")
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

    private fun launchHomeIntent() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
    }
}
