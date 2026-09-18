package com.example.automation.workflow

import android.content.Context
import com.example.data.db.RecordedWorkflow
import com.example.data.db.SigmaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WorkflowRepository(context: Context) {
    private val dao = SigmaDatabase.getInstance(context).sigmaDao()

    val allWorkflows: Flow<List<RecordedWorkflow>> = dao.getAllWorkflows()

    suspend fun getWorkflowById(id: Long): RecordedWorkflow? = withContext(Dispatchers.IO) {
        dao.getWorkflowById(id)
    }

    suspend fun getWorkflowByName(name: String): RecordedWorkflow? = withContext(Dispatchers.IO) {
        dao.getWorkflowByName(name)
    }

    suspend fun saveWorkflow(
        name: String,
        description: String = "",
        steps: List<WorkflowStep>,
        targetPackage: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val workflow = RecordedWorkflow(
            name = name,
            description = description,
            actionsJson = WorkflowStep.listToJson(steps),
            targetPackage = targetPackage,
            targetHints = steps.mapNotNull { it.targetText.ifEmpty { it.targetDescription.ifEmpty { null } } }.joinToString(", "),
            createdTime = System.currentTimeMillis(),
            lastUsedTime = System.currentTimeMillis()
        )
        dao.insertWorkflow(workflow)
    }

    suspend fun updateWorkflow(workflow: RecordedWorkflow) = withContext(Dispatchers.IO) {
        dao.updateWorkflow(workflow)
    }

    suspend fun deleteWorkflow(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteWorkflow(id)
    }

    suspend fun duplicateWorkflow(id: Long) = withContext(Dispatchers.IO) {
        val original = dao.getWorkflowById(id) ?: return@withContext
        val duplicate = original.copy(
            id = 0,
            name = "${original.name} (Copy)",
            createdTime = System.currentTimeMillis(),
            lastUsedTime = System.currentTimeMillis(),
            runCount = 0
        )
        dao.insertWorkflow(duplicate)
    }

    suspend fun recordWorkflowRun(id: Long) = withContext(Dispatchers.IO) {
        dao.incrementWorkflowRun(id)
    }
}
