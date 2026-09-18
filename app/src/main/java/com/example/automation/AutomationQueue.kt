package com.example.automation

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QueueItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String,
    val action: suspend () -> ActionResult
)

object AutomationQueue {
    private const val TAG = "AutomationQueue"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _lastExecutionResult = MutableStateFlow<ActionResult?>(null)
    val lastExecutionResult: StateFlow<ActionResult?> = _lastExecutionResult.asStateFlow()

    @Volatile
    var activeActionName: String = "IDLE"
        private set

    @Volatile
    var queueDepth: Int = 0
        private set

    /**
     * Immediately interrupts and cancels any active automation plan or running action.
     */
    fun interrupt(reason: String = "User stop command") {
        Log.w(TAG, "INTERRUPT TRIGGERED: $reason")
        activeJob?.cancel(CancellationException(reason))
        activeJob = null
        _isExecuting.value = false
        activeActionName = "INTERRUPTED"
        queueDepth = 0
        _lastExecutionResult.value = ActionResult(ActionResultStatus.CANCELLED, "Interrupted: $reason")
        AutomationDiagnostics.lastActionResult = "CANCELLED"
        AutomationDiagnostics.lastAction = "INTERRUPTED ($reason)"
    }

    /**
     * Executes a single or sequential automation task with full cancellation support and verification.
     */
    fun execute(
        name: String,
        onProgress: ((String) -> Unit)? = null,
        block: suspend () -> ActionResult
    ) {
        // Cancel any lingering previous job
        activeJob?.cancel()

        activeJob = scope.launch {
            _isExecuting.value = true
            activeActionName = name
            queueDepth = 1
            AutomationDiagnostics.lastAction = name
            AutomationDiagnostics.currentState = AutomationState.IDLE

            val start = System.currentTimeMillis()
            try {
                onProgress?.invoke("Executing: $name")
                val result = block()
                val totalDuration = System.currentTimeMillis() - start
                _lastExecutionResult.value = result
                AutomationDiagnostics.lastActionResult = result.status.name
                Log.i(TAG, "Queue completed '$name' with status=${result.status} in ${totalDuration}ms")
            } catch (e: CancellationException) {
                Log.w(TAG, "Queue action '$name' cancelled: ${e.message}")
                _lastExecutionResult.value = ActionResult(ActionResultStatus.CANCELLED, "Cancelled")
                AutomationDiagnostics.lastActionResult = "CANCELLED"
            } catch (e: Exception) {
                Log.e(TAG, "Queue action '$name' threw exception: ${e.message}", e)
                _lastExecutionResult.value = ActionResult(ActionResultStatus.FAILED, "Error: ${e.message}")
                AutomationDiagnostics.lastActionResult = "FAILED"
            } finally {
                _isExecuting.value = false
                activeActionName = "IDLE"
                queueDepth = 0
            }
        }
    }
}
