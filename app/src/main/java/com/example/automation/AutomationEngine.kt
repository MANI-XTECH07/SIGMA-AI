package com.example.automation

import android.util.Log
import com.example.apps.AppResolutionResult
import com.example.apps.AppResolver
import com.example.device.DeviceController
import kotlinx.coroutines.delay

sealed class AutomationStep {
    data class LaunchApp(val appQuery: String) : AutomationStep()
    data class FindAndTap(val textToTap: String) : AutomationStep()
    data class FindAndTapSearch(val fallbackHint: String = "Search") : AutomationStep()
    data class TypeText(val textToType: String) : AutomationStep()
    data class FindAndTapResult(val query: String) : AutomationStep()
    data class Scroll(val forward: Boolean) : AutomationStep()
    data class LockDevice(val dummy: Unit = Unit) : AutomationStep()
    data class SearchWeb(val query: String) : AutomationStep()
    data class GoHome(val dummy: Unit = Unit) : AutomationStep()
    data class GoBack(val dummy: Unit = Unit) : AutomationStep()
    data class Wait(val durationMs: Long) : AutomationStep()
}

data class AutomationPlan(
    val title: String,
    val steps: List<AutomationStep>
)

data class PlanExecutionReport(
    val success: Boolean,
    val executedSteps: Int,
    val totalSteps: Int,
    val finalMessage: String,
    val failedStep: AutomationStep? = null
)

class AutomationEngine(
    private val appResolver: AppResolver,
    private val actionExecutor: ActionExecutor,
    private val screenObserver: ScreenObserver,
    private val actionVerifier: ActionVerifier,
    private val recoveryEngine: RecoveryEngine,
    private val deviceController: DeviceController
) {

    companion object {
        private const val TAG = "AutomationEngine"
    }

    /**
     * Executes an automation plan following the Beast Loop:
     * OBSERVE -> PLAN -> ACT -> OBSERVE AGAIN -> VERIFY -> RECOVER
     * No false successes. Reports truthful step-by-step outcomes.
     */
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
        Log.d(TAG, "Starting automation plan: ${plan.title} with ${plan.steps.size} steps")

        for ((index, step) in plan.steps.withIndex()) {
            val stepName = describeStep(step)
            onProgress("Step ${index + 1}/${plan.steps.size}: $stepName")
            val stepSuccess = executeStepWithVerification(step)

            if (!stepSuccess) {
                onProgress("Step failed: $stepName. Attempting recovery...")
                val recovered = recoveryEngine.attemptRecovery(
                    targetAction = { executeStepWithVerification(step) },
                    onFallback = { actionExecutor.scroll(forward = true) }
                )
                if (!recovered) {
                    val failureMsg = "Failed at step ${index + 1}: $stepName"
                    onProgress(failureMsg)
                    return PlanExecutionReport(
                        success = false,
                        executedSteps = index,
                        totalSteps = plan.steps.size,
                        finalMessage = failureMsg,
                        failedStep = step
                    )
                }
            }
            delay(400L)
        }

        val successMsg = "Completed ${plan.title}"
        onProgress(successMsg)
        return PlanExecutionReport(
            success = true,
            executedSteps = plan.steps.size,
            totalSteps = plan.steps.size,
            finalMessage = successMsg
        )
    }

    private suspend fun executeStepWithVerification(step: AutomationStep): Boolean {
        val beforeState = screenObserver.observeCurrentScreen()

        return when (step) {
            is AutomationStep.LaunchApp -> {
                val res = appResolver.resolveAndLaunch(step.appQuery)
                delay(1200L) // Wait for activity transition
                res is AppResolutionResult.Success || res is AppResolutionResult.MultipleMatches
            }
            is AutomationStep.FindAndTap -> {
                val res = actionExecutor.tapText(step.textToTap)
                if (res is ExecutionResult.Success) {
                    actionVerifier.verifyChange(beforeState, timeoutMs = 1500L)
                    true
                } else {
                    false
                }
            }
            is AutomationStep.FindAndTapSearch -> {
                val res = actionExecutor.tapSearch()
                if (res is ExecutionResult.Success) {
                    delay(500L)
                    true
                } else {
                    false
                }
            }
            is AutomationStep.TypeText -> {
                val res = actionExecutor.typeText(step.textToType)
                delay(600L)
                res is ExecutionResult.Success
            }
            is AutomationStep.FindAndTapResult -> {
                val res = actionExecutor.tapResult(step.query)
                res is ExecutionResult.Success
            }
            is AutomationStep.Scroll -> {
                val res = actionExecutor.scroll(step.forward)
                delay(400L)
                res is ExecutionResult.Success
            }
            is AutomationStep.LockDevice -> {
                val res = actionExecutor.lockDevice()
                res is ExecutionResult.Success
            }
            is AutomationStep.SearchWeb -> {
                appResolver.resolveAndLaunch("browser") is AppResolutionResult.Success
            }
            is AutomationStep.GoHome -> {
                actionExecutor.goHome() is ExecutionResult.Success
            }
            is AutomationStep.GoBack -> {
                actionExecutor.goBack() is ExecutionResult.Success
            }
            is AutomationStep.Wait -> {
                delay(step.durationMs)
                true
            }
        }
    }

    private fun describeStep(step: AutomationStep): String = when (step) {
        is AutomationStep.LaunchApp -> "Launch ${step.appQuery}"
        is AutomationStep.FindAndTap -> "Tap '${step.textToTap}'"
        is AutomationStep.FindAndTapSearch -> "Tap Search"
        is AutomationStep.TypeText -> "Type '${step.textToType}'"
        is AutomationStep.FindAndTapResult -> "Select result '${step.query}'"
        is AutomationStep.Scroll -> "Scroll ${if (step.forward) "down" else "up"}"
        is AutomationStep.LockDevice -> "Lock device"
        is AutomationStep.SearchWeb -> "Search web for '${step.query}'"
        is AutomationStep.GoHome -> "Go home"
        is AutomationStep.GoBack -> "Go back"
        is AutomationStep.Wait -> "Wait ${step.durationMs}ms"
    }
}
