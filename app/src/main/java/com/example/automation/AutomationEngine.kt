package com.example.automation

import android.util.Log
import com.example.apps.AppResolver
import com.example.device.DeviceController
import kotlinx.coroutines.delay

sealed class AutomationStep {
    data class LaunchApp(val appQuery: String) : AutomationStep()
    data class FindAndTap(val textToTap: String) : AutomationStep()
    data class TypeText(val textToType: String) : AutomationStep()
    data class Scroll(val forward: Boolean) : AutomationStep()
    data class LockDevice(val dummy: Unit = Unit) : AutomationStep()
    data class SearchWeb(val query: String) : AutomationStep()
    data class Wait(val durationMs: Long) : AutomationStep()
}

data class AutomationPlan(
    val title: String,
    val steps: List<AutomationStep>
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
     */
    suspend fun executePlan(
        plan: AutomationPlan,
        onProgress: (String) -> Unit
    ): Boolean {
        Log.d(TAG, "Starting automation plan: ${plan.title} with ${plan.steps.size} steps")

        for ((index, step) in plan.steps.withIndex()) {
            onProgress("Executing step ${index + 1}/${plan.steps.size}: ${describeStep(step)}")
            val stepSuccess = executeStepWithVerification(step)

            if (!stepSuccess) {
                onProgress("Step failed. Initiating recovery engine...")
                val recovered = recoveryEngine.attemptRecovery(
                    targetAction = { executeStepWithVerification(step) },
                    onFallback = { actionExecutor.goBack() }
                )
                if (!recovered) {
                    onProgress("Automation stopped: Step could not be verified.")
                    return false
                }
            }
            delay(400L)
        }

        onProgress("Automation completed successfully.")
        return true
    }

    private suspend fun executeStepWithVerification(step: AutomationStep): Boolean {
        val beforeState = screenObserver.observeCurrentScreen()

        when (step) {
            is AutomationStep.LaunchApp -> {
                val res = appResolver.resolveAndLaunch(step.appQuery)
                delay(1200L) // allow app transition
                return res !is com.example.apps.AppResolutionResult.NotFound
            }
            is AutomationStep.FindAndTap -> {
                val res = actionExecutor.tapText(step.textToTap)
                if (res is ExecutionResult.Success) {
                    return actionVerifier.verifyChange(beforeState, timeoutMs = 1500L)
                }
                return false
            }
            is AutomationStep.TypeText -> {
                val res = actionExecutor.typeText(step.textToType)
                return res is ExecutionResult.Success
            }
            is AutomationStep.Scroll -> {
                val res = actionExecutor.scroll(step.forward)
                return res is ExecutionResult.Success
            }
            is AutomationStep.LockDevice -> {
                val res = actionExecutor.lockDevice()
                return res is ExecutionResult.Success
            }
            is AutomationStep.SearchWeb -> {
                // Launch Chrome or default browser with query
                return appResolver.launchApp("com.android.chrome")
            }
            is AutomationStep.Wait -> {
                delay(step.durationMs)
                return true
            }
        }
    }

    private fun describeStep(step: AutomationStep): String = when (step) {
        is AutomationStep.LaunchApp -> "Launch ${step.appQuery}"
        is AutomationStep.FindAndTap -> "Tap '${step.textToTap}'"
        is AutomationStep.TypeText -> "Type '${step.textToType}'"
        is AutomationStep.Scroll -> "Scroll ${if (step.forward) "down" else "up"}"
        is AutomationStep.LockDevice -> "Lock device"
        is AutomationStep.SearchWeb -> "Search web for '${step.query}'"
        is AutomationStep.Wait -> "Wait ${step.durationMs}ms"
    }
}
