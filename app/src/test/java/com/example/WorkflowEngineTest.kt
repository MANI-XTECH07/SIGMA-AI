package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.automation.workflow.WorkflowActionType
import com.example.automation.workflow.WorkflowPlayer
import com.example.automation.workflow.WorkflowRecorder
import com.example.automation.workflow.WorkflowRepository
import com.example.automation.workflow.WorkflowStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkflowEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = WorkflowRepository(context)

    @Test
    fun testWorkflowStepSerializationAndDeserialization() {
        val steps = listOf(
            WorkflowStep(
                actionType = WorkflowActionType.APP_LAUNCH,
                packageName = "com.google.android.youtube",
                description = "Open YouTube"
            ),
            WorkflowStep(
                actionType = WorkflowActionType.TAP,
                targetText = "Search",
                targetResourceId = "com.google.android.youtube:id/search_button",
                xRatio = 0.5f,
                yRatio = 0.1f
            ),
            WorkflowStep(
                actionType = WorkflowActionType.TYPE_TEXT,
                textToType = "Lo-fi beats"
            )
        )

        val json = WorkflowStep.listToJson(steps)
        val deserialized = WorkflowStep.listFromJson(json)

        assertEquals(3, deserialized.size)
        assertEquals(WorkflowActionType.APP_LAUNCH, deserialized[0].actionType)
        assertEquals("com.google.android.youtube", deserialized[0].packageName)
        assertEquals("Search", deserialized[1].targetText)
        assertEquals("Lo-fi beats", deserialized[2].textToType)
    }

    @Test
    fun testWorkflowRepositorySaveAndRetrieve() = runBlocking {
        val steps = listOf(
            WorkflowStep(
                actionType = WorkflowActionType.APP_LAUNCH,
                packageName = "com.spotify.music"
            )
        )

        val workflowId = repository.saveWorkflow("Test Spotify Flow", steps = steps)
        assertTrue(workflowId > 0)

        val retrieved = repository.getWorkflowById(workflowId)
        assertNotNull(retrieved)
        assertEquals("Test Spotify Flow", retrieved?.name)

        val retrievedSteps = WorkflowStep.listFromJson(retrieved!!.actionsJson)
        assertEquals(1, retrievedSteps.size)
        assertEquals("com.spotify.music", retrievedSteps[0].packageName)

        // Duplicate test
        repository.duplicateWorkflow(workflowId)
        val dup = repository.getWorkflowByName("Test Spotify Flow (Copy)")
        assertNotNull(dup)
        assertEquals("Test Spotify Flow (Copy)", dup?.name)

        // Delete test
        repository.deleteWorkflow(workflowId)
        if (dup != null) {
            repository.deleteWorkflow(dup.id)
        }
    }

    @Test
    fun testWorkflowRecorderStateLifecycle() {
        WorkflowRecorder.startRecording("My Daily Routine")
        assertTrue(WorkflowRecorder.isRecording.value)
        assertEquals("My Daily Routine", WorkflowRecorder.currentWorkflowName.value)

        val recorded = WorkflowRecorder.stopRecording()
        assertFalse(WorkflowRecorder.isRecording.value)
        assertNotNull(recorded)
    }

    @Test
    fun testWorkflowPlayerStopAndPauseControls() {
        WorkflowPlayer.stop()
        assertFalse(WorkflowPlayer.isPlaying.value)
        WorkflowPlayer.resume()
    }
}
