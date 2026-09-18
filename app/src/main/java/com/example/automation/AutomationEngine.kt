package com.example.automation

import android.os.SystemClock
import android.util.Log
import com.example.apps.AppResolutionResult
import com.example.apps.AppResolver
import com.example.device.DeviceController
import com.example.service.SigmaAccessibilityService
import kotlinx.coroutines.delay

enum class AutomationState {
    IDLE,
    OBSERVE,
    UNDERSTAND,
    PLANNING,
    APP_OPEN,
    SEARCH_READY,
    QUERY_ENTERED,
    SEARCH_SUBMITTED,
    WAIT_FOR_RESULTS,
    RESULTS_DETECTED,
    RESULT_SELECTED,
    PLAY_TRIGGERED,
    PLAYBACK_VERIFICATION,
    VERIFY,
    RECOVER,
    SUCCESS,
    FAILED,
    CANCELLED
}

sealed class AutomationStep {
    data class LaunchApp(val appQuery: String) : AutomationStep()
    data class WaitForPackage(val expectedPkg: String, val timeoutMs: Long = 4000L) : AutomationStep()
    data class WaitForElement(val targetText: String, val timeoutMs: Long = 3500L) : AutomationStep()
    data class FindAndTapSearch(val fallbackHint: String = "Search") : AutomationStep()
    data class TypeText(val textToType: String) : AutomationStep()
    data class ClearText(val dummy: Unit = Unit) : AutomationStep()
    data class SubmitSearch(val dummy: Unit = Unit) : AutomationStep()
    data class Wait(val durationMs: Long) : AutomationStep()
    data class ObserveScreen(val dummy: Unit = Unit) : AutomationStep()
    data class SelectResult(val query: String) : AutomationStep()
    data class PlayMedia(val dummy: Unit = Unit) : AutomationStep()
    data class VerifyPlayback(val dummy: Unit = Unit) : AutomationStep()
    data class VerifyResultsDetected(val query: String = "") : AutomationStep()
    data class FindAndTapResult(val query: String) : AutomationStep()
    data class FindAndTap(val textToTap: String) : AutomationStep()
    data class DoubleTap(val label: String? = null, val x: Float? = null, val y: Float? = null) : AutomationStep()
    data class Scroll(val forward: Boolean) : AutomationStep()
    data class Swipe(val direction: String) : AutomationStep()
    data class Drag(val startX: Float, val startY: Float, val endX: Float, val endY: Float) : AutomationStep()
    data class LongPress(val targetText: String) : AutomationStep()
    data class LockDevice(val dummy: Unit = Unit) : AutomationStep()
    data class SearchWeb(val query: String) : AutomationStep()
    data class GoHome(val dummy: Unit = Unit) : AutomationStep()
    data class GoBack(val dummy: Unit = Unit) : AutomationStep()
    data class OpenRecents(val dummy: Unit = Unit) : AutomationStep()
    data class CloseApp(val dummy: Unit = Unit) : AutomationStep()
    data class SetVolume(val percent: Int) : AutomationStep()
    data class AdjustVolume(val up: Boolean) : AutomationStep()
    data class SetMute(val mute: Boolean) : AutomationStep()
    data class MediaControl(val action: MediaAction) : AutomationStep()
    data class TakeScreenshot(val dummy: Unit = Unit) : AutomationStep()

    val actionName: String
        get() = when (this) {
            is LaunchApp -> "OPEN_APP (${appQuery})"
            is WaitForPackage -> "WAIT_FOR_PACKAGE (${expectedPkg})"
            is WaitForElement -> "WAIT_FOR_ELEMENT (${targetText})"
            is FindAndTapSearch -> "TAP_SEARCH"
            is TypeText -> "TYPE (\"${textToType}\")"
            is ClearText -> "CLEAR_TEXT"
            is SubmitSearch -> "SUBMIT"
            is Wait -> "WAIT (${durationMs}ms)"
            is ObserveScreen -> "OBSERVE_SCREEN"
            is SelectResult -> "SELECT_RESULT (\"${query}\")"
            is PlayMedia -> "PLAY"
            is VerifyPlayback -> "VERIFY_PLAYBACK"
            is VerifyResultsDetected -> "VERIFY_RESULTS (\"${query}\")"
            is FindAndTapResult -> "SELECT_RESULT (\"${query}\")"
            is FindAndTap -> "TAP (\"${textToTap}\")"
            is DoubleTap -> "DOUBLE_TAP (${label ?: "($x, $y)"})"
            is Scroll -> "SCROLL (${if (forward) "DOWN" else "UP"})"
            is Swipe -> "SWIPE (${direction})"
            is Drag -> "DRAG (($startX,$startY)->($endX,$endY))"
            is LongPress -> "LONG_PRESS (\"${targetText}\")"
            is LockDevice -> "LOCK_DEVICE"
            is SearchWeb -> "SEARCH_WEB (\"${query}\")"
            is GoHome -> "GO_HOME"
            is GoBack -> "GO_BACK"
            is OpenRecents -> "RECENTS"
            is CloseApp -> "CLOSE_APP"
            is SetVolume -> "SET_VOLUME (${percent}%)"
            is AdjustVolume -> "VOLUME_${if (up) "UP" else "DOWN"}"
            is SetMute -> if (mute) "MUTE" else "UNMUTE"
            is MediaControl -> "MEDIA_${action.name}"
            is TakeScreenshot -> "SCREENSHOT"
        }
}

data class AutomationPlan(
    val title: String,
    val steps: List<AutomationStep>,
    val intent: String = "GENERAL",
    val query: String = "",
    val command: String = ""
)

data class PlanExecutionReport(
    val success: Boolean,
    val executedSteps: Int,
    val totalSteps: Int,
    val finalMessage: String,
    val state: AutomationState = AutomationState.SUCCESS,
    val failedStep: AutomationStep? = null,
    val durationMs: Long = 0L
)

/**
 * Real-time runtime diagnostics state holder observed by DiagnosticsScreen.
 */
object AutomationDiagnostics {
    @Volatile var currentState: AutomationState = AutomationState.IDLE
    @Volatile var currentPackage: String = ""
    @Volatile var currentActivity: String = ""
    @Volatile var isAccessibilityConnected: Boolean = false
    @Volatile var rootNodeAvailable: Boolean = false
    @Volatile var activeNodeCount: Int = 0
    @Volatile var currentScreenSummary: String = ""
    @Volatile var lastAction: String = "None"
    @Volatile var lastActionDurationMs: Long = 0L
    @Volatile var lastActionResult: String = "None"
    @Volatile var lastSelectedResult: String = ""
    @Volatile var isPlaybackVerified: Boolean = false
    @Volatile var recoveryAttempts: Int = 0
    @Volatile var failureReason: String = "None"
    @Volatile var aiLatencyMs: Long = 0L
    @Volatile var totalCommandLatencyMs: Long = 0L
    @Volatile var lastCommand: String = ""
    @Volatile var parsedIntent: String = ""

    fun updateNodeMetrics(service: SigmaAccessibilityService?) {
        isAccessibilityConnected = service != null && SigmaAccessibilityService.isServiceRunning
        val root = service?.getActiveRoot()
        rootNodeAvailable = root != null
        if (root != null) {
            val nodes = service.dumpScreenNodes()
            activeNodeCount = nodes.size
        } else {
            activeNodeCount = 0
        }
        currentPackage = SigmaAccessibilityService.currentPackage.ifEmpty { ForegroundAppTracker.currentPackageName }
        currentActivity = ForegroundAppTracker.currentActivityName
    }
}

class AutomationEngine(
    private val appResolver: AppResolver,
    private val actionExecutor: ActionExecutor,
    private val screenObserver: ScreenObserver,
    private val actionVerifier: ActionVerifier,
    private val recoveryEngine: RecoveryEngine,
    private val deviceController: DeviceController
) {

    companion object {
        private const val TAG = "SIGMA_AUTOMATION"
    }

    var currentState: AutomationState
        get() = AutomationDiagnostics.currentState
        private set(value) {
            AutomationDiagnostics.currentState = value
        }

    suspend fun executePlan(
        plan: AutomationPlan,
        onProgress: (String) -> Unit
    ): Boolean {
        val report = executePlanWithReport(plan, onProgress)
        return report.success
    }

    suspend fun executePlanWithReport(
        plan: AutomationPlan,
        onProgress: (String) -> Unit
    ): PlanExecutionReport {
        val overallStart = SystemClock.elapsedRealtime()
        AutomationDiagnostics.lastCommand = plan.command.ifEmpty { plan.title }
        AutomationDiagnostics.parsedIntent = plan.intent
        AutomationDiagnostics.recoveryAttempts = 0
        AutomationDiagnostics.isPlaybackVerified = false
        AutomationDiagnostics.failureReason = "None"
        currentState = AutomationState.IDLE

        val service = SigmaAccessibilityService.instance
        AutomationDiagnostics.updateNodeMetrics(service)

        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING AUTOMATION EXECUTION LOOP")
        Log.i(TAG, "COMMAND = ${AutomationDiagnostics.lastCommand}")
        Log.i(TAG, "PARSED INTENT = ${plan.intent}")
        Log.i(TAG, "PLAN = ${plan.steps.size} steps")
        for ((i, step) in plan.steps.withIndex()) {
            Log.i(TAG, "ACTION[${i + 1}] = ${step.actionName}")
        }
        Log.i(TAG, "==================================================")

        // Check if Accessibility is connected for non-pure-app-launch steps
        val nonAccessibilitySteps = plan.steps.all { it is AutomationStep.LaunchApp || it is AutomationStep.Wait }
        if (!SigmaAccessibilityService.isServiceRunning && !nonAccessibilitySteps) {
            currentState = AutomationState.FAILED
            val errMsg = "Accessibility permission is required for screen control."
            Log.w(TAG, "SERVICE_CHECK_FAILED: $errMsg")
            AutomationDiagnostics.lastActionResult = "FAILED: Service not connected"
            AutomationDiagnostics.failureReason = errMsg
            onProgress(errMsg)
            return PlanExecutionReport(
                success = false,
                executedSteps = 0,
                totalSteps = plan.steps.size,
                finalMessage = errMsg,
                state = AutomationState.FAILED,
                failedStep = plan.steps.firstOrNull(),
                durationMs = SystemClock.elapsedRealtime() - overallStart
            )
        }

        // Execute sequential loop: OBSERVE -> UNDERSTAND -> PLAN -> ACT -> OBSERVE -> VERIFY -> RECOVER
        for ((index, step) in plan.steps.withIndex()) {
            val stepStart = SystemClock.elapsedRealtime()
            val stepLabel = "ACTION[${index + 1}] = ${step.actionName}"
            AutomationDiagnostics.lastAction = step.actionName
            AutomationDiagnostics.currentPackage = SigmaAccessibilityService.currentPackage.ifEmpty { ForegroundAppTracker.currentPackageName }
            AutomationDiagnostics.currentActivity = ForegroundAppTracker.currentActivityName

            Log.i(TAG, "CURRENT_ACTION: $stepLabel | PKG: ${AutomationDiagnostics.currentPackage}")
            onProgress("Step ${index + 1}/${plan.steps.size}: ${step.actionName}")

            val stepSuccess = executeStep(step, plan, onProgress)
            val stepDuration = SystemClock.elapsedRealtime() - stepStart
            AutomationDiagnostics.lastActionDurationMs = stepDuration

            if (!stepSuccess) {
                currentState = AutomationState.RECOVER
                AutomationDiagnostics.recoveryAttempts++
                Log.w(TAG, "ACTION_FAILED at step ${index + 1}: ${step.actionName}. Triggering Self-Healing Recovery...")
                onProgress("Recovering step ${index + 1}...")

                val recovered = if (step is AutomationStep.SelectResult || step is AutomationStep.FindAndTapResult ||
                    step is AutomationStep.PlayMedia || step is AutomationStep.VerifyPlayback) {
                    recoveryEngine.recoverResultSelectionAndPlay(plan.query)
                } else {
                    recoveryEngine.attemptRecovery(
                        targetAction = { executeStep(step, plan, onProgress) },
                        onFallback = { actionExecutor.scroll(forward = true) }
                    )
                }

                if (!recovered) {
                    currentState = AutomationState.FAILED
                    val failureMsg = when (step) {
                        is AutomationStep.SelectResult, is AutomationStep.FindAndTapResult,
                        is AutomationStep.PlayMedia, is AutomationStep.VerifyPlayback ->
                            "Found results, but could not trigger playback on the target item."
                        is AutomationStep.FindAndTap -> "Target element '${step.textToTap}' was not found on screen."
                        is AutomationStep.TypeText -> "Could not focus editable field to type text."
                        else -> "Failed at step ${index + 1}: ${step.actionName}"
                    }
                    AutomationDiagnostics.lastActionResult = "FAILED: $failureMsg"
                    AutomationDiagnostics.failureReason = failureMsg
                    Log.e(TAG, "FINAL_RESULT: FAILED: $failureMsg")
                    onProgress(failureMsg)

                    // Record context failure
                    CommandContext.recordTurn(
                        rawCommand = plan.command,
                        intent = plan.intent,
                        targetApp = AutomationDiagnostics.currentPackage,
                        query = plan.query,
                        success = false
                    )

                    val totalDuration = SystemClock.elapsedRealtime() - overallStart
                    AutomationDiagnostics.totalCommandLatencyMs = totalDuration

                    return PlanExecutionReport(
                        success = false,
                        executedSteps = index,
                        totalSteps = plan.steps.size,
                        finalMessage = failureMsg,
                        state = AutomationState.FAILED,
                        failedStep = step,
                        durationMs = totalDuration
                    )
                } else {
                    Log.i(TAG, "RECOVERY_SUCCEEDED at step ${index + 1}")
                    if (plan.intent == "PLAY_MEDIA") {
                        AutomationDiagnostics.isPlaybackVerified = true
                    }
                }
            }

            AutomationDiagnostics.lastActionResult = "SUCCESS"
            delay(150L)
        }

        currentState = AutomationState.SUCCESS
        val successMsg = when {
            plan.intent == "PLAY_MEDIA" -> "Playing."
            plan.intent == "SEARCH_ONLY" -> "Search completed for ${plan.query.ifEmpty { "query" }}."
            plan.intent == "VOLUME" -> "Volume adjusted."
            else -> "Completed ${plan.title}"
        }

        val totalDuration = SystemClock.elapsedRealtime() - overallStart
        AutomationDiagnostics.totalCommandLatencyMs = totalDuration

        // Record successful context turn
        CommandContext.recordTurn(
            rawCommand = plan.command,
            intent = plan.intent,
            targetApp = AutomationDiagnostics.currentPackage,
            query = plan.query,
            selectedItem = AutomationDiagnostics.lastSelectedResult,
            success = true
        )

        Log.i(TAG, "FINAL_RESULT: SUCCESS - $successMsg (total: ${totalDuration}ms)")
        onProgress(successMsg)
        return PlanExecutionReport(
            success = true,
            executedSteps = plan.steps.size,
            totalSteps = plan.steps.size,
            finalMessage = successMsg,
            state = AutomationState.SUCCESS,
            durationMs = totalDuration
        )
    }

    private suspend fun executeStep(
        step: AutomationStep,
        plan: AutomationPlan,
        onProgress: (String) -> Unit
    ): Boolean {
        val service = SigmaAccessibilityService.instance

        return when (step) {
            is AutomationStep.LaunchApp -> {
                currentState = AutomationState.APP_OPEN
                var res = appResolver.resolveAndLaunch(step.appQuery)
                if (res is AppResolutionResult.NotFound) {
                    val normalized = step.appQuery.replace("_", " ").trim()
                    if (normalized != step.appQuery) {
                        res = appResolver.resolveAndLaunch(normalized)
                    }
                }
                delay(1000L)
                val ok = res is AppResolutionResult.Success || res is AppResolutionResult.MultipleMatches
                Log.d(TAG, "APP_OPEN LaunchApp(${step.appQuery}) = $ok, pkg=${SigmaAccessibilityService.currentPackage}")
                ok
            }

            is AutomationStep.WaitForPackage -> {
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < step.timeoutMs) {
                    if (SigmaAccessibilityService.currentPackage.contains(step.expectedPkg, ignoreCase = true)) {
                        return true
                    }
                    delay(200L)
                }
                false
            }

            is AutomationStep.WaitForElement -> {
                screenObserver.waitForElement(step.targetText, timeoutMs = step.timeoutMs)
            }

            is AutomationStep.FindAndTapSearch -> {
                currentState = AutomationState.SEARCH_READY
                var res: ExecutionResult = ExecutionResult.Failure("Search not found")
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 3500L) {
                    res = actionExecutor.tapSearch()
                    if (res is ExecutionResult.Success) break
                    delay(300L)
                }
                delay(400L)
                val ok = res is ExecutionResult.Success
                Log.d(TAG, "SEARCH_READY tapSearch = $ok")
                ok
            }

            is AutomationStep.TypeText -> {
                currentState = AutomationState.QUERY_ENTERED
                var res: ExecutionResult = ExecutionResult.Failure("No editable field")
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 3000L) {
                    res = actionExecutor.typeText(step.textToType)
                    if (res is ExecutionResult.Success) break
                    delay(300L)
                }
                delay(300L)
                val ok = res is ExecutionResult.Success
                Log.d(TAG, "QUERY_ENTERED typeText(\"${step.textToType}\") = $ok")
                ok
            }

            is AutomationStep.ClearText -> {
                val res = actionExecutor.clearText()
                res is ExecutionResult.Success
            }

            is AutomationStep.SubmitSearch -> {
                currentState = AutomationState.SEARCH_SUBMITTED
                val res = actionExecutor.submitSearch()
                delay(600L)
                val ok = res is ExecutionResult.Success
                Log.d(TAG, "SEARCH_SUBMITTED submitSearch = $ok")
                ok
            }

            is AutomationStep.Wait -> {
                currentState = AutomationState.WAIT_FOR_RESULTS
                delay(step.durationMs)
                true
            }

            is AutomationStep.ObserveScreen -> {
                currentState = AutomationState.OBSERVE
                val detected = actionVerifier.verifyResultsDetected(plan.query, timeoutMs = 2500L)
                val nodes = service?.observeScreen() ?: emptyList()
                AutomationDiagnostics.currentScreenSummary = "Screen contains ${nodes.size} nodes"
                Log.i(TAG, "RESULTS_DETECTED = $detected (observed ${nodes.size} nodes)")
                true
            }

            is AutomationStep.VerifyResultsDetected -> {
                currentState = AutomationState.RESULTS_DETECTED
                val detected = actionVerifier.verifyResultsDetected(step.query, timeoutMs = 3000L)
                Log.i(TAG, "VERIFY_RESULTS = $detected")
                detected
            }

            is AutomationStep.SelectResult, is AutomationStep.FindAndTapResult -> {
                currentState = AutomationState.RESULT_SELECTED
                val q = if (step is AutomationStep.SelectResult) step.query else (step as AutomationStep.FindAndTapResult).query
                var res: ExecutionResult = ExecutionResult.Failure("Result not found")
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 4000L) {
                    res = actionExecutor.selectResult(q)
                    if (res is ExecutionResult.Success) break
                    delay(350L)
                }
                val ok = res is ExecutionResult.Success
                if (ok) {
                    AutomationDiagnostics.lastSelectedResult = q
                }
                Log.i(TAG, "RESULT_SELECTED query='$q' = $ok")
                ok
            }

            is AutomationStep.PlayMedia -> {
                currentState = AutomationState.PLAY_TRIGGERED
                delay(500L)
                val res = actionExecutor.playMedia()
                val ok = res is ExecutionResult.Success
                Log.i(TAG, "PLAY_TRIGGERED = $ok")
                ok
            }

            is AutomationStep.VerifyPlayback -> {
                currentState = AutomationState.PLAYBACK_VERIFICATION
                val verified = actionVerifier.verifyPlayback(timeoutMs = 3500L)
                AutomationDiagnostics.isPlaybackVerified = verified
                Log.i(TAG, "PLAYBACK_VERIFIED = $verified")
                verified
            }

            is AutomationStep.FindAndTap -> {
                val res = actionExecutor.tapText(step.textToTap)
                res is ExecutionResult.Success
            }

            is AutomationStep.DoubleTap -> {
                val res = actionExecutor.doubleTap(step.label, step.x, step.y)
                res is ExecutionResult.Success
            }

            is AutomationStep.Scroll -> {
                val res = actionExecutor.scroll(step.forward)
                res is ExecutionResult.Success
            }

            is AutomationStep.Swipe -> {
                val res = actionExecutor.swipeDirection(step.direction)
                res is ExecutionResult.Success
            }

            is AutomationStep.Drag -> {
                val res = actionExecutor.drag(step.startX, step.startY, step.endX, step.endY)
                res is ExecutionResult.Success
            }

            is AutomationStep.LongPress -> {
                val res = actionExecutor.longPressText(step.targetText)
                res is ExecutionResult.Success
            }

            is AutomationStep.LockDevice -> {
                val res = actionExecutor.lockDevice()
                res is ExecutionResult.Success
            }

            is AutomationStep.SearchWeb -> {
                val res = appResolver.resolveAndLaunch("browser")
                res is AppResolutionResult.Success
            }

            is AutomationStep.GoHome -> {
                val res = actionExecutor.goHome()
                res is ExecutionResult.Success
            }

            is AutomationStep.GoBack -> {
                val res = actionExecutor.goBack()
                res is ExecutionResult.Success
            }

            is AutomationStep.OpenRecents -> {
                val res = actionExecutor.openRecents()
                res is ExecutionResult.Success
            }

            is AutomationStep.CloseApp -> {
                val res = actionExecutor.closeCurrentApp()
                res is ExecutionResult.Success
            }

            is AutomationStep.SetVolume -> {
                val res = actionExecutor.setVolume(step.percent)
                res is ExecutionResult.Success
            }

            is AutomationStep.AdjustVolume -> {
                val res = actionExecutor.adjustVolume(step.up)
                res is ExecutionResult.Success
            }

            is AutomationStep.SetMute -> {
                val res = actionExecutor.setMute(step.mute)
                res is ExecutionResult.Success
            }

            is AutomationStep.MediaControl -> {
                val res = when (step.action) {
                    MediaAction.PLAY -> actionExecutor.mediaPlay()
                    MediaAction.PAUSE -> actionExecutor.mediaPause()
                    MediaAction.NEXT -> actionExecutor.mediaNext()
                    MediaAction.PREVIOUS -> actionExecutor.mediaPrevious()
                    MediaAction.TOGGLE -> actionExecutor.mediaPlay()
                }
                res is ExecutionResult.Success
            }

            is AutomationStep.TakeScreenshot -> {
                service?.takeScreenshotCompat { }
                true
            }
        }
    }
}
