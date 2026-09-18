package com.example.automation

import android.util.Log
import com.example.apps.AppResolutionResult
import com.example.apps.AppResolver
import com.example.device.DeviceController
import com.example.service.SigmaAccessibilityService
import kotlinx.coroutines.delay

enum class AutomationState {
    IDLE,
    APP_OPEN,
    SEARCH_READY,
    QUERY_ENTERED,
    SEARCH_SUBMITTED,
    WAIT_FOR_RESULTS,
    RESULTS_DETECTED,
    RESULT_SELECTED,
    PLAY_TRIGGERED,
    PLAYBACK_VERIFICATION,
    SUCCESS,
    RECOVER,
    FAILED
}

sealed class AutomationStep {
    data class LaunchApp(val appQuery: String) : AutomationStep()
    data class FindAndTapSearch(val fallbackHint: String = "Search") : AutomationStep()
    data class TypeText(val textToType: String) : AutomationStep()
    data class SubmitSearch(val dummy: Unit = Unit) : AutomationStep()
    data class Wait(val durationMs: Long) : AutomationStep()
    data class ObserveScreen(val dummy: Unit = Unit) : AutomationStep()
    data class SelectResult(val query: String) : AutomationStep()
    data class PlayMedia(val dummy: Unit = Unit) : AutomationStep()
    data class VerifyPlayback(val dummy: Unit = Unit) : AutomationStep()
    data class VerifyResultsDetected(val query: String = "") : AutomationStep()
    data class FindAndTapResult(val query: String) : AutomationStep()
    data class FindAndTap(val textToTap: String) : AutomationStep()
    data class Scroll(val forward: Boolean) : AutomationStep()
    data class LockDevice(val dummy: Unit = Unit) : AutomationStep()
    data class SearchWeb(val query: String) : AutomationStep()
    data class GoHome(val dummy: Unit = Unit) : AutomationStep()
    data class GoBack(val dummy: Unit = Unit) : AutomationStep()

    val actionName: String
        get() = when (this) {
            is LaunchApp -> "OPEN_APP (${appQuery})"
            is FindAndTapSearch -> "SEARCH"
            is TypeText -> "TYPE (\"${textToType}\")"
            is SubmitSearch -> "SUBMIT"
            is Wait -> "WAIT (${durationMs}ms)"
            is ObserveScreen -> "OBSERVE_SCREEN"
            is SelectResult -> "SELECT_RESULT (\"${query}\")"
            is PlayMedia -> "PLAY"
            is VerifyPlayback -> "VERIFY"
            is VerifyResultsDetected -> "VERIFY_RESULTS"
            is FindAndTapResult -> "SELECT_RESULT (\"${query}\")"
            is FindAndTap -> "TAP (\"${textToTap}\")"
            is Scroll -> "SCROLL (${if (forward) "DOWN" else "UP"})"
            is LockDevice -> "LOCK_DEVICE"
            is SearchWeb -> "SEARCH_WEB (\"${query}\")"
            is GoHome -> "GO_HOME"
            is GoBack -> "GO_BACK"
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
    val failedStep: AutomationStep? = null
)

/**
 * Real-time runtime diagnostics state holder observed by DiagnosticsScreen.
 */
object AutomationDiagnostics {
    @Volatile var currentState: AutomationState = AutomationState.IDLE
    @Volatile var currentPackage: String = ""
    @Volatile var currentScreenSummary: String = ""
    @Volatile var lastAction: String = "None"
    @Volatile var lastActionResult: String = "None"
    @Volatile var lastSelectedResult: String = ""
    @Volatile var isPlaybackVerified: Boolean = false
    @Volatile var recoveryAttempts: Int = 0
    @Volatile var lastCommand: String = ""
    @Volatile var parsedIntent: String = ""
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
        AutomationDiagnostics.lastCommand = plan.command.ifEmpty { plan.title }
        AutomationDiagnostics.parsedIntent = plan.intent
        AutomationDiagnostics.recoveryAttempts = 0
        AutomationDiagnostics.isPlaybackVerified = false
        currentState = AutomationState.IDLE

        Log.i(TAG, "==================================================")
        Log.i(TAG, "COMMAND = ${AutomationDiagnostics.lastCommand}")
        Log.i(TAG, "PARSED INTENT = ${plan.intent}")
        Log.i(TAG, "PLAN =")
        for ((i, step) in plan.steps.withIndex()) {
            Log.i(TAG, "ACTION[${i + 1}] = ${step.actionName}")
        }
        Log.i(TAG, "==================================================")

        // Section 3: Verify AccessibilityService is actually connected before executing UI actions.
        val nonAccessibilitySteps = plan.steps.all { it is AutomationStep.LaunchApp || it is AutomationStep.Wait }
        if (!SigmaAccessibilityService.isServiceRunning && !nonAccessibilitySteps) {
            currentState = AutomationState.FAILED
            val errMsg = "Accessibility permission is required for screen control."
            Log.w(TAG, "SERVICE_CHECK_FAILED: $errMsg")
            AutomationDiagnostics.lastActionResult = "FAILED: Service not connected"
            onProgress(errMsg)
            return PlanExecutionReport(
                success = false,
                executedSteps = 0,
                totalSteps = plan.steps.size,
                finalMessage = errMsg,
                state = AutomationState.FAILED,
                failedStep = plan.steps.firstOrNull()
            )
        }

        for ((index, step) in plan.steps.withIndex()) {
            val stepLabel = "ACTION[${index + 1}] = ${step.actionName}"
            AutomationDiagnostics.lastAction = step.actionName
            AutomationDiagnostics.currentPackage = SigmaAccessibilityService.currentPackage
            Log.i(TAG, "CURRENT_ACTION: $stepLabel | PACKAGE: ${AutomationDiagnostics.currentPackage}")
            onProgress("Step ${index + 1}/${plan.steps.size}: ${step.actionName}")

            val stepSuccess = executeStep(step, plan, onProgress)

            if (!stepSuccess) {
                currentState = AutomationState.RECOVER
                AutomationDiagnostics.recoveryAttempts++
                Log.w(TAG, "ACTION_FAILED at step ${index + 1}: ${step.actionName}. Triggering Recovery...")
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
                            "I found the result, but the app did not expose a playable control."
                        else -> "Failed at step ${index + 1}: ${step.actionName}"
                    }
                    AutomationDiagnostics.lastActionResult = "FAILED: $failureMsg"
                    Log.e(TAG, "FINAL_RESULT: FAILED: $failureMsg")
                    onProgress(failureMsg)
                    return PlanExecutionReport(
                        success = false,
                        executedSteps = index,
                        totalSteps = plan.steps.size,
                        finalMessage = failureMsg,
                        state = AutomationState.FAILED,
                        failedStep = step
                    )
                } else {
                    Log.i(TAG, "RECOVERY_SUCCEEDED at step ${index + 1}")
                    if (plan.intent == "PLAY_MEDIA") {
                        AutomationDiagnostics.isPlaybackVerified = true
                    }
                }
            }

            AutomationDiagnostics.lastActionResult = "SUCCESS"
            delay(300L)
        }

        currentState = AutomationState.SUCCESS
        val successMsg = when {
            plan.intent == "PLAY_MEDIA" -> "Playing."
            plan.intent == "SEARCH_ONLY" -> "Search completed for ${plan.query.ifEmpty { "query" }}."
            else -> "Completed ${plan.title}"
        }

        Log.i(TAG, "FINAL_RESULT: SUCCESS - $successMsg")
        onProgress(successMsg)
        return PlanExecutionReport(
            success = true,
            executedSteps = plan.steps.size,
            totalSteps = plan.steps.size,
            finalMessage = successMsg,
            state = AutomationState.SUCCESS
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
                delay(1200L)
                val ok = res is AppResolutionResult.Success || res is AppResolutionResult.MultipleMatches
                Log.d(TAG, "APP_OPEN LaunchApp(${step.appQuery}) = $ok, pkg=${SigmaAccessibilityService.currentPackage}")
                ok
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
                delay(500L)
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
                delay(400L)
                val ok = res is ExecutionResult.Success
                Log.d(TAG, "QUERY_ENTERED typeText(\"${step.textToType}\") = $ok")
                ok
            }

            is AutomationStep.SubmitSearch -> {
                currentState = AutomationState.SEARCH_SUBMITTED
                val res = actionExecutor.submitSearch()
                delay(800L)
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
                currentState = AutomationState.RESULTS_DETECTED
                val detected = actionVerifier.verifyResultsDetected(plan.query, timeoutMs = 2500L)
                val nodes = service?.observeScreen() ?: emptyList()
                AutomationDiagnostics.currentScreenSummary = "Screen contains ${nodes.size} nodes"
                Log.i(TAG, "RESULTS_DETECTED = $detected (observed ${nodes.size} nodes)")
                true // Allow next step (SelectResult) to attempt matching
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
                    delay(400L)
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
                delay(600L)
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

            is AutomationStep.Scroll -> {
                val res = actionExecutor.scroll(step.forward)
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
        }
    }
}
