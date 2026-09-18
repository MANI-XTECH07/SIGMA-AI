package com.example.automation.workflow

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.apps.AppResolver
import com.example.apps.InstalledAppRepository
import com.example.automation.ActionExecutor
import com.example.automation.ActionResult
import com.example.automation.ActionResultStatus
import com.example.automation.AutomationDiagnostics
import com.example.automation.AutomationState
import com.example.automation.ExecutionResult
import com.example.automation.ForegroundAppTracker
import com.example.automation.RecoveryEngine
import com.example.automation.ScreenObserver
import com.example.automation.ScreenUnderstandingEngine
import com.example.automation.TargetingEngine
import com.example.data.db.RecordedWorkflow
import com.example.device.LockController
import com.example.screen.ScreenAnalysisManager
import com.example.service.SigmaAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkflowExecutionReport(
    val success: Boolean,
    val totalSteps: Int,
    val executedSteps: Int,
    val failedStepIndex: Int = -1,
    val failureReason: String = "",
    val durationMs: Long = 0L,
    val message: String = ""
)

object WorkflowPlayer {
    private const val TAG = "WorkflowPlayer"

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentWorkflow = MutableStateFlow<RecordedWorkflow?>(null)
    val currentWorkflow: StateFlow<RecordedWorkflow?> = _currentWorkflow.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    @Volatile
    private var isPaused = false

    @Volatile
    private var isCancelled = false

    fun pause() {
        isPaused = true
        Log.i(TAG, "Workflow paused.")
    }

    fun resume() {
        isPaused = false
        Log.i(TAG, "Workflow resumed.")
    }

    fun stop() {
        isCancelled = true
        _isPlaying.value = false
        Log.i(TAG, "Workflow stopped by user.")
    }

    /**
     * Executes a recorded workflow with smart UI adaptation, verification, and intelligent recovery.
     */
    suspend fun executeWorkflow(
        context: Context,
        workflow: RecordedWorkflow,
        onProgress: (String) -> Unit = {}
    ): WorkflowExecutionReport {
        val steps = WorkflowStep.listFromJson(workflow.actionsJson)
        if (steps.isEmpty()) {
            return WorkflowExecutionReport(
                success = false,
                totalSteps = 0,
                executedSteps = 0,
                failureReason = "Workflow contains no recorded actions.",
                message = "Workflow contains no actions."
            )
        }

        val start = SystemClock.elapsedRealtime()
        _isPlaying.value = true
        _currentWorkflow.value = workflow
        _currentStepIndex.value = 0
        isPaused = false
        isCancelled = false

        val screenUnderstanding = ScreenUnderstandingEngine()
        val appResolver = AppResolver(context, InstalledAppRepository(context))
        val lockController = LockController(context)
        val actionExecutor = ActionExecutor(lockController, context)
        val screenObserver = ScreenObserver(ScreenAnalysisManager())
        val recoveryEngine = RecoveryEngine(actionExecutor, screenObserver)

        AutomationDiagnostics.currentState = AutomationState.IDLE
        AutomationDiagnostics.lastCommand = "Workflow: ${workflow.name}"

        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING WORKFLOW REPLAY: \"${workflow.name}\" (${steps.size} steps)")
        Log.i(TAG, "==================================================")

        for ((index, step) in steps.withIndex()) {
            _currentStepIndex.value = index

            // Check cancellation
            if (isCancelled) {
                Log.w(TAG, "Workflow execution cancelled at step $index.")
                _isPlaying.value = false
                AutomationDiagnostics.lastActionResult = "CANCELLED"
                return WorkflowExecutionReport(
                    success = false,
                    totalSteps = steps.size,
                    executedSteps = index,
                    failedStepIndex = index,
                    failureReason = "Execution was cancelled.",
                    durationMs = SystemClock.elapsedRealtime() - start,
                    message = "Workflow was cancelled."
                )
            }

            // Check paused state
            while (isPaused && !isCancelled) {
                delay(300L)
            }

            val stepLabel = "Step ${index + 1}/${steps.size}: ${step.actionType} - ${step.description.ifEmpty { step.targetText }}"
            onProgress(stepLabel)
            AutomationDiagnostics.lastAction = stepLabel
            Log.d(TAG, "REPLAY_STEP [$index]: ${step.actionType} target='${step.targetText}' resId='${step.targetResourceId}'")

            // Execute step with smart UI adaptation
            val success = executeStepWithAdaptation(
                step = step,
                actionExecutor = actionExecutor,
                appResolver = appResolver,
                screenUnderstanding = screenUnderstanding,
                recoveryEngine = recoveryEngine,
                onProgress = onProgress
            )

            if (!success) {
                val failureMsg = "Failed at step ${index + 1}: ${step.description.ifEmpty { step.actionType.name }} (${step.targetText.ifEmpty { step.targetResourceId }})"
                Log.e(TAG, "WORKFLOW_STEP_FAILED: $failureMsg")
                _isPlaying.value = false
                AutomationDiagnostics.currentState = AutomationState.FAILED
                AutomationDiagnostics.lastActionResult = "FAILED: $failureMsg"
                AutomationDiagnostics.failureReason = failureMsg
                onProgress(failureMsg)

                return WorkflowExecutionReport(
                    success = false,
                    totalSteps = steps.size,
                    executedSteps = index,
                    failedStepIndex = index,
                    failureReason = failureMsg,
                    durationMs = SystemClock.elapsedRealtime() - start,
                    message = failureMsg
                )
            }

            // Post-step state delay
            delay(250L)
        }

        _isPlaying.value = false
        val totalTime = SystemClock.elapsedRealtime() - start
        AutomationDiagnostics.currentState = AutomationState.SUCCESS
        AutomationDiagnostics.lastActionResult = "SUCCESS"
        val completeMsg = "Workflow \"${workflow.name}\" completed successfully in ${totalTime / 1000}s."
        Log.i(TAG, "WORKFLOW_COMPLETED: $completeMsg")
        onProgress(completeMsg)

        // Increment run count in repository
        try {
            val repo = WorkflowRepository(context)
            repo.recordWorkflowRun(workflow.id)
        } catch (e: Exception) {
            // Non-blocking run count update
        }

        return WorkflowExecutionReport(
            success = true,
            totalSteps = steps.size,
            executedSteps = steps.size,
            durationMs = totalTime,
            message = completeMsg
        )
    }

    private suspend fun executeStepWithAdaptation(
        step: WorkflowStep,
        actionExecutor: ActionExecutor,
        appResolver: AppResolver,
        screenUnderstanding: ScreenUnderstandingEngine,
        recoveryEngine: RecoveryEngine,
        onProgress: (String) -> Unit
    ): Boolean {
        val service = SigmaAccessibilityService.instance

        when (step.actionType) {
            WorkflowActionType.APP_LAUNCH -> {
                val target = step.packageName.ifEmpty { step.targetText }
                val res = appResolver.resolveAndLaunch(target)
                delay(1200L)
                return res is com.example.apps.AppResolutionResult.Success || res is com.example.apps.AppResolutionResult.MultipleMatches
            }

            WorkflowActionType.TAP -> {
                // 1. Observe current screen snapshot dynamically
                val snapshot = screenUnderstanding.inspectScreen(service)

                // 2. Try exact text / contentDescription
                val targetQuery = step.targetText.ifEmpty { step.targetDescription }
                if (targetQuery.isNotBlank()) {
                    val clicked = TargetingEngine.performHybridClick(targetQuery, service)
                    if (clicked) return true
                }

                // 3. Try resource ID
                if (step.targetResourceId.isNotBlank()) {
                    val clicked = TargetingEngine.performHybridClick(step.targetResourceId, service)
                    if (clicked) return true
                }

                // 4. Try semantic synonym target match
                if (targetQuery.isNotBlank()) {
                    val match = screenUnderstanding.findSemanticTarget(targetQuery, snapshot)
                    if (match != null) {
                        val clicked = service?.tapCoordinates(match.element.centerX, match.element.centerY) ?: false
                        if (clicked) return true
                    }
                }

                // 5. Intelligent Recovery: Attempt slight scroll to bring target into viewport
                Log.w(TAG, "Target not found directly. Triggering recovery scroll...")
                val recovered = recoveryEngine.attemptRecovery(
                    targetAction = {
                        val retrySnapshot = screenUnderstanding.inspectScreen(service)
                        if (targetQuery.isNotBlank()) {
                            TargetingEngine.performHybridClick(targetQuery, service)
                        } else false
                    },
                    onFallback = { actionExecutor.scroll(forward = true) }
                )
                if (recovered) return true

                // 6. Fallback to normalized coordinate ratios
                if (service != null && step.xRatio in 0.01f..0.99f && step.yRatio in 0.01f..0.99f) {
                    val realX = step.xRatio * 1080f
                    val realY = step.yRatio * 2400f
                    Log.d(TAG, "Coordinate ratio fallback at ($realX, $realY)")
                    return service.tapCoordinates(realX, realY)
                }

                return false
            }

            WorkflowActionType.DOUBLE_TAP -> {
                val res = actionExecutor.doubleTap(label = step.targetText.ifEmpty { null })
                return res is ExecutionResult.Success
            }

            WorkflowActionType.LONG_PRESS -> {
                val res = actionExecutor.longPressText(step.targetText)
                return res is ExecutionResult.Success
            }

            WorkflowActionType.TYPE_TEXT -> {
                // Find and focus editable field
                var res = actionExecutor.typeText(step.textToType)
                if (res !is ExecutionResult.Success) {
                    // Try tapping target input field first, then typing
                    if (step.targetResourceId.isNotBlank()) {
                        TargetingEngine.performHybridClick(step.targetResourceId, service)
                        delay(300L)
                    }
                    res = actionExecutor.typeText(step.textToType)
                }
                return res is ExecutionResult.Success
            }

            WorkflowActionType.CLEAR_TEXT -> {
                val res = actionExecutor.clearText()
                return res is ExecutionResult.Success
            }

            WorkflowActionType.SCROLL -> {
                val res = actionExecutor.scroll(forward = true)
                delay(300L)
                return res is ExecutionResult.Success
            }

            WorkflowActionType.SWIPE -> {
                val dir = step.extraValue.ifEmpty { "UP" }
                val res = actionExecutor.swipeDirection(dir)
                delay(300L)
                return res is ExecutionResult.Success
            }

            WorkflowActionType.DRAG -> {
                val res = actionExecutor.drag(500f, 1500f, 500f, 500f)
                return res is ExecutionResult.Success
            }

            WorkflowActionType.BACK -> {
                val res = actionExecutor.goBack()
                delay(300L)
                return res is ExecutionResult.Success
            }

            WorkflowActionType.HOME -> {
                val res = actionExecutor.goHome()
                delay(500L)
                return res is ExecutionResult.Success
            }

            WorkflowActionType.RECENTS -> {
                val res = actionExecutor.openRecents()
                delay(500L)
                return res is ExecutionResult.Success
            }

            WorkflowActionType.WAIT -> {
                delay(step.timeoutMs)
                return true
            }

            WorkflowActionType.MEDIA_CONTROL -> {
                val action = when (step.extraValue.uppercase()) {
                    "PLAY" -> com.example.automation.MediaAction.PLAY
                    "PAUSE" -> com.example.automation.MediaAction.PAUSE
                    "NEXT" -> com.example.automation.MediaAction.NEXT
                    "PREVIOUS" -> com.example.automation.MediaAction.PREVIOUS
                    else -> com.example.automation.MediaAction.PLAY
                }
                val res = when (action) {
                    com.example.automation.MediaAction.PLAY -> actionExecutor.mediaPlay()
                    com.example.automation.MediaAction.PAUSE -> actionExecutor.mediaPause()
                    com.example.automation.MediaAction.NEXT -> actionExecutor.mediaNext()
                    com.example.automation.MediaAction.PREVIOUS -> actionExecutor.mediaPrevious()
                    com.example.automation.MediaAction.TOGGLE -> actionExecutor.mediaPlay()
                }
                return res is ExecutionResult.Success
            }

            WorkflowActionType.VOLUME_CONTROL -> {
                val percent = step.extraValue.toIntOrNull() ?: 50
                val res = actionExecutor.setVolume(percent)
                return res is ExecutionResult.Success
            }

            WorkflowActionType.VERIFY_ELEMENT -> {
                val target = step.targetText
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < step.timeoutMs) {
                    val snapshot = screenUnderstanding.inspectScreen(service)
                    if (snapshot.findElement(target) != null || snapshot.visibleTexts.any { it.contains(target, ignoreCase = true) }) {
                        return true
                    }
                    delay(250L)
                }
                return false
            }

            WorkflowActionType.VERIFY_PLAYBACK -> {
                val verified = service?.verifyPlayback() ?: false
                return verified
            }
        }
    }
}
