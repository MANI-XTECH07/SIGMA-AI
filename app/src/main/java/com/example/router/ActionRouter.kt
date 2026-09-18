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

        Log.i(TAG, "COMMAND_RECEIVED: \"$trimmed\"")
        Log.i(TAG, "TRANSCRIPT: \"$rawTranscript\"")

        // 0. IMMEDIATE INTERRUPT CHECK
        val classified = com.example.automation.CommandClassifier.classify(trimmed)
        if (classified is com.example.automation.ClassifiedIntent.Interrupt) {
            com.example.automation.AutomationQueue.interrupt("Voice command: ${classified.phrase}")
            com.example.automation.workflow.WorkflowPlayer.stop()
            logHistory(trimmed, true, "Stopped.")
            return@withContext RouterResult.Spoken("Stopped.", "INTERRUPT")
        }

        // 0b. SCREEN UNDERSTANDING & READING: "Sigma, read this screen"
        if (classified is com.example.automation.ClassifiedIntent.ReadScreen) {
            val engine = com.example.automation.ScreenUnderstandingEngine()
            val snapshot = engine.inspectScreen(SigmaAccessibilityService.instance)
            val summary = engine.generateConciseSummary(snapshot)
            logHistory(trimmed, true, summary)
            return@withContext RouterResult.Spoken(summary, "SCREEN_READOUT")
        }

        // 0c. TEACH SIGMA / WORKFLOW RECORDING: "Sigma, learn this"
        if (classified is com.example.automation.ClassifiedIntent.LearnWorkflow) {
            com.example.automation.workflow.WorkflowRecorder.startRecording(classified.name)
            val msg = "Recording started for \"${classified.name}\". Perform your actions on screen, and say \"Sigma, save workflow\" when finished."
            logHistory(trimmed, true, msg)
            return@withContext RouterResult.Spoken(msg, "WORKFLOW_LEARN")
        }

        // 0d. SAVE / STOP WORKFLOW RECORDING: "Sigma, save workflow"
        if (classified is com.example.automation.ClassifiedIntent.StopRecordingWorkflow) {
            val workflowName = com.example.automation.workflow.WorkflowRecorder.currentWorkflowName.value
            val steps = com.example.automation.workflow.WorkflowRecorder.stopRecording()
            if (steps.isEmpty()) {
                val msg = "No actions were recorded during this session."
                logHistory(trimmed, false, msg)
                return@withContext RouterResult.Spoken(msg, "WORKFLOW_SAVE")
            }
            val repo = com.example.automation.workflow.WorkflowRepository(context)
            repo.saveWorkflow(name = workflowName, steps = steps)
            val msg = "Saved workflow \"$workflowName\" with ${steps.size} actions."
            logHistory(trimmed, true, msg)
            return@withContext RouterResult.Spoken(msg, "WORKFLOW_SAVE")
        }

        // 0e. REPLAY WORKFLOW: "Sigma run Daily YouTube"
        if (classified is com.example.automation.ClassifiedIntent.RunWorkflow) {
            val repo = com.example.automation.workflow.WorkflowRepository(context)
            val workflow = repo.getWorkflowByName(classified.workflowName)
            if (workflow == null) {
                val msg = "I couldn't find a saved workflow named \"${classified.workflowName}\"."
                logHistory(trimmed, false, msg)
                return@withContext RouterResult.Spoken(msg, "WORKFLOW_RUN")
            }
            val report = com.example.automation.workflow.WorkflowPlayer.executeWorkflow(context, workflow)
            logHistory(trimmed, report.success, report.message)
            return@withContext RouterResult.Spoken(report.message, "WORKFLOW_RUN")
        }

        // 0f. SMART RESULT SELECTION: "first result", "open the result about Naruto"
        if (classified is com.example.automation.ClassifiedIntent.SmartResultSelection) {
            val service = SigmaAccessibilityService.instance
            if (service == null) {
                val msg = "Accessibility service is required for smart item selection."
                logHistory(trimmed, false, msg)
                return@withContext RouterResult.Spoken(msg, "SMART_SELECTION")
            }
            val engine = com.example.automation.ScreenUnderstandingEngine()
            val snapshot = engine.inspectScreen(service)
            val target = engine.resolveSmartResult(classified.query, snapshot)
            if (target != null) {
                val tapped = service.tapCoordinates(target.centerX, target.centerY)
                val msg = if (tapped) "Opening ${target.displayLabel.take(30)}." else "Could not open target."
                logHistory(trimmed, tapped, msg)
                return@withContext RouterResult.Spoken(msg, "SMART_SELECTION")
            } else {
                val msg = "No matching item was found on the screen."
                logHistory(trimmed, false, msg)
                return@withContext RouterResult.Spoken(msg, "SMART_SELECTION")
            }
        }

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
                val msg = "Accessibility service is not connected. Please enable SIGMA in Accessibility Settings to capture screenshots."
                logHistory(trimmed, false, msg)
                return@withContext RouterResult.Spoken(msg, "SCREENSHOT")
            }
        }

        // 2. HARDWARE & DEVICE CONTROLS (Flashlight, Volume, Brightness)
        if (lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("torch jalao") || lower == "flashlight" || lower == "torch") {
            val success = torchController.turnOnTorch()
            val msg = if (success) "Flashlight turned on." else "Could not enable flashlight on this device."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "TORCH_ON")
        }

        if (lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("torch band karo") || lower.contains("torch band")) {
            val success = torchController.turnOffTorch()
            val msg = if (success) "Flashlight turned off." else "Could not disable flashlight."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "TORCH_OFF")
        }

        if (lower.contains("volume up") || lower.contains("awaz badhao") || lower.contains("increase volume")) {
            val success = deviceController.adjustVolume(increase = true)
            val msg = if (success) "Volume increased." else "Volume adjusted."
            logHistory(trimmed, true, msg)
            return@withContext RouterResult.Spoken(msg, "VOLUME")
        }

        if (lower.contains("volume down") || lower.contains("awaz kam karo") || lower.contains("decrease volume")) {
            val success = deviceController.adjustVolume(increase = false)
            val msg = if (success) "Volume decreased." else "Volume adjusted."
            logHistory(trimmed, true, msg)
            return@withContext RouterResult.Spoken(msg, "VOLUME")
        }

        if (lower == "mute" || lower.contains("mute volume") || lower.contains("mute phone") || lower.contains("mute karo")) {
            val success = deviceController.muteVolume()
            val msg = if (success) "Volume muted." else "Could not mute volume."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "VOLUME_MUTE")
        }

        if (lower == "unmute" || lower.contains("unmute volume") || lower.contains("unmute karo")) {
            val success = deviceController.unmuteVolume()
            val msg = if (success) "Volume unmuted." else "Could not unmute volume."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "VOLUME_UNMUTE")
        }

        // Direct media next / previous
        if (lower == "next" || lower == "next song" || lower == "next video" || lower == "agla gana" || lower == "agla video") {
            val success = deviceController.dispatchMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            val msg = if (success) "Skipped to next." else "Could not skip media."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "MEDIA_NEXT")
        }

        if (lower == "previous" || lower == "previous song" || lower == "previous video" || lower == "pichla gana") {
            val success = deviceController.dispatchMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            val msg = if (success) "Going to previous." else "Could not go to previous media."
            logHistory(trimmed, success, msg)
            return@withContext RouterResult.Spoken(msg, "MEDIA_PREVIOUS")
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
            Log.i(TAG, "SIGMA_AUTOMATION: Routing plan '${plan.title}' with ${plan.steps.size} steps (intent=${plan.intent})")
            val report = automationEngine.executePlanWithReport(plan) { progress ->
                Log.d(TAG, "SIGMA_AUTOMATION: Plan progress: $progress")
            }
            val spokenMessage = if (report.success) {
                if (plan.intent == "PLAY_MEDIA") "Playing." else report.finalMessage
            } else {
                report.finalMessage
            }
            logHistory(trimmed, report.success, spokenMessage)
            return@withContext RouterResult.Spoken(spokenMessage, "AUTOMATION")
        }

        // 5b. DIRECT PLAYBACK CONTROLS (Pause, Resume, Stop)
        if (lower == "pause" || lower == "pause video" || lower == "pause music" || lower == "rok do" || lower == "roko") {
            val service = SigmaAccessibilityService.instance
            val paused = service?.findAndClickPlayControl() ?: false
            val msg = if (paused) "Paused." else "Could not find active playback to pause."
            logHistory(trimmed, paused, msg)
            return@withContext RouterResult.Spoken(msg, "PLAYBACK_CONTROL")
        }
        if (lower == "resume" || lower == "resume video" || lower == "resume music" || lower == "chalao wapis") {
            val service = SigmaAccessibilityService.instance
            val resumed = service?.findAndClickPlayControl() ?: false
            val msg = if (resumed) "Resumed." else "Could not find playback control to resume."
            logHistory(trimmed, resumed, msg)
            return@withContext RouterResult.Spoken(msg, "PLAYBACK_CONTROL")
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
                is AppResolutionResult.NotFound -> false to "I couldn't find an installed app named $query on this phone."
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

        // 8. FALLBACK TO GEMINI AI CONVERSATION
        try {
            val aiResult = aiProvider.generateText(trimmed)
            val text = aiResult.getOrNull()
            if (!text.isNullOrBlank()) {
                Log.i(TAG, "AI_RESPONSE generated: \"${text.take(60)}...\"")
                logHistory(trimmed, true, text)
                return@withContext RouterResult.Spoken(text, "AI_CHAT")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini fallback failed: ${e.message}")
        }

        return@withContext RouterResult.Spoken("I have received your command: \"$trimmed\".")
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
