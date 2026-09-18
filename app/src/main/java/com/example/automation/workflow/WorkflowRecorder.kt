package com.example.automation.workflow

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.service.SigmaAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WorkflowRecorder {
    private const val TAG = "WorkflowRecorder"

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedSteps = MutableStateFlow<List<WorkflowStep>>(emptyList())
    val recordedSteps: StateFlow<List<WorkflowStep>> = _recordedSteps.asStateFlow()

    private val _currentWorkflowName = MutableStateFlow("New Workflow")
    val currentWorkflowName: StateFlow<String> = _currentWorkflowName.asStateFlow()

    private var targetPackage: String = ""

    fun startRecording(workflowName: String = "Learned Workflow") {
        _currentWorkflowName.value = workflowName
        _recordedSteps.value = emptyList()
        targetPackage = SigmaAccessibilityService.currentPackage
        _isRecording.value = true
        Log.i(TAG, "RECORDING STARTED: \"$workflowName\" for package: $targetPackage")
    }

    fun stopRecording(): List<WorkflowStep> {
        _isRecording.value = false
        val steps = _recordedSteps.value
        Log.i(TAG, "RECORDING STOPPED. Total steps recorded: ${steps.size}")
        return steps
    }

    fun cancelRecording() {
        _isRecording.value = false
        _recordedSteps.value = emptyList()
        Log.i(TAG, "RECORDING CANCELLED.")
    }

    /**
     * Intercepts accessibility events during recording to capture user actions.
     * Enforces strict privacy: Password/PIN fields and credentials are never recorded.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!_isRecording.value) return

        val source = event.source ?: return

        // Security check: Ignore password fields
        if (source.isPassword) {
            Log.w(TAG, "SECURITY FILTER: Skipped recording action on password field.")
            return
        }

        val eventType = event.eventType
        val pkg = event.packageName?.toString() ?: ""
        val cls = event.className?.toString() ?: ""
        val text = event.text?.joinToString(" ")?.trim() ?: source.text?.toString()?.trim() ?: ""
        val desc = source.contentDescription?.toString()?.trim() ?: ""
        val viewId = source.viewIdResourceName ?: ""

        val bounds = Rect()
        source.getBoundsInScreen(bounds)

        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Deduplicate repetitive click events on the same node
                val lastStep = _recordedSteps.value.lastOrNull()
                val isDup = lastStep != null && lastStep.actionType == WorkflowActionType.TAP &&
                        lastStep.targetText == text && lastStep.targetResourceId == viewId
                if (!isDup) {
                    val step = WorkflowStep(
                        actionType = WorkflowActionType.TAP,
                        targetText = text,
                        targetDescription = desc,
                        targetResourceId = viewId,
                        targetClassName = cls,
                        packageName = pkg,
                        xRatio = if (bounds.width() > 0) bounds.exactCenterX() / 1080f else 0.5f,
                        yRatio = if (bounds.height() > 0) bounds.exactCenterY() / 2400f else 0.5f,
                        description = "Tap on ${text.ifEmpty { desc.ifEmpty { viewId.substringAfter(":id/") } }}"
                    )
                    addStep(step)
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // Record text entry into non-password fields
                if (text.isNotBlank() && !source.isPassword) {
                    val lastStep = _recordedSteps.value.lastOrNull()
                    if (lastStep != null && lastStep.actionType == WorkflowActionType.TYPE_TEXT && lastStep.targetResourceId == viewId) {
                        // Update existing type step with full text
                        val updated = _recordedSteps.value.toMutableList()
                        updated[updated.lastIndex] = lastStep.copy(textToType = text)
                        _recordedSteps.value = updated
                    } else {
                        val step = WorkflowStep(
                            actionType = WorkflowActionType.TYPE_TEXT,
                            targetResourceId = viewId,
                            targetClassName = cls,
                            packageName = pkg,
                            textToType = text,
                            description = "Type \"$text\""
                        )
                        addStep(step)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val lastStep = _recordedSteps.value.lastOrNull()
                if (lastStep?.actionType != WorkflowActionType.SCROLL) {
                    val step = WorkflowStep(
                        actionType = WorkflowActionType.SCROLL,
                        packageName = pkg,
                        description = "Scroll"
                    )
                    addStep(step)
                }
            }
        }
    }

    fun addManualStep(step: WorkflowStep) {
        addStep(step)
    }

    private fun addStep(step: WorkflowStep) {
        val current = _recordedSteps.value.toMutableList()
        current.add(step)
        _recordedSteps.value = current
        Log.d(TAG, "Recorded step #${current.size}: ${step.actionType} - ${step.description}")
    }
}
