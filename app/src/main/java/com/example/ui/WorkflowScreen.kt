package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.automation.workflow.WorkflowPlayer
import com.example.automation.workflow.WorkflowRecorder
import com.example.automation.workflow.WorkflowRepository
import com.example.automation.workflow.WorkflowStep
import com.example.data.db.RecordedWorkflow
import com.example.ui.components.SigmaButton
import com.example.ui.components.SigmaGlassCard
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaRedDark
import com.example.ui.theme.SigmaStatusExecuting
import com.example.ui.theme.SigmaStatusOnline
import com.example.ui.theme.SigmaStatusThinking
import com.example.ui.theme.SigmaWhite
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkflowScreen(
    repository: WorkflowRepository,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val workflows by repository.allWorkflows.collectAsState(initial = emptyList())
    val isRecording by WorkflowRecorder.isRecording.collectAsState()
    val recordedSteps by WorkflowRecorder.recordedSteps.collectAsState()
    val isPlaying by WorkflowPlayer.isPlaying.collectAsState()
    val currentWorkflow by WorkflowPlayer.currentWorkflow.collectAsState()
    val currentStepIndex by WorkflowPlayer.currentStepIndex.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var workflowToRename by remember { mutableStateOf<RecordedWorkflow?>(null) }
    var newWorkflowName by remember { mutableStateOf("") }
    var executionStatusText by remember { mutableStateOf("") }
    var expandedWorkflowId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SigmaBlack)
            .padding(16.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TEACH & REPLAY WORKFLOWS",
                    color = SigmaWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Automate multi-step routines with smart UI adaptation",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // TEACH SIGMA HERO RECORDER CARD
        SigmaGlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (isRecording) SigmaNeonRed else SigmaNeonRed.copy(alpha = 0.4f)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.FiberManualRecord else Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = if (isRecording) SigmaNeonRed else SigmaStatusThinking,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRecording) "RECORDING MODE ACTIVE" else "TEACH SIGMA",
                            color = if (isRecording) SigmaNeonRed else SigmaWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SigmaNeonRed.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${recordedSteps.size} actions",
                                color = SigmaNeonRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isRecording)
                        "SIGMA is observing your gestures. Password inputs and credentials are automatically filtered out for security."
                    else
                        "Record your app interactions by demonstrating them once. SIGMA will learn the steps and adapt to UI changes automatically.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isRecording) {
                        SigmaButton(
                            text = "START RECORDING",
                            onClick = {
                                showCreateDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        SigmaButton(
                            text = "SAVE WORKFLOW",
                            onClick = {
                                scope.launch {
                                    val name = WorkflowRecorder.currentWorkflowName.value
                                    val steps = WorkflowRecorder.stopRecording()
                                    if (steps.isNotEmpty()) {
                                        repository.saveWorkflow(name = name, steps = steps)
                                        executionStatusText = "Saved \"$name\" (${steps.size} steps)"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        SigmaButton(
                            text = "CANCEL",
                            onClick = {
                                WorkflowRecorder.cancelRecording()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ACTIVE EXECUTION STATUS BANNER
        if (isPlaying || executionStatusText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isPlaying) Color(0xFF1E0A10) else Color(0xFF101B12))
                    .border(1.dp, if (isPlaying) SigmaNeonRed else SigmaStatusOnline, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPlaying) "REPLAYING: ${currentWorkflow?.name ?: ""}" else "STATUS",
                            color = SigmaWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = executionStatusText.ifEmpty { "Step ${currentStepIndex + 1} executing..." },
                            color = if (isPlaying) SigmaNeonRed else SigmaStatusOnline,
                            fontSize = 11.sp
                        )
                    }

                    if (isPlaying) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { WorkflowPlayer.pause() }) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = SigmaWhite)
                            }
                            IconButton(onClick = { WorkflowPlayer.stop() }) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = SigmaNeonRed)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SAVED WORKFLOWS LIST
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SAVED ROUTINES (${workflows.size})",
                color = SigmaWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (workflows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No saved workflows yet.",
                        color = Color(0xFF777777),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Say \"Sigma, learn this\" or tap Start Recording above.",
                        color = Color(0xFF555555),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(workflows, key = { it.id }) { workflow ->
                    val steps = remember(workflow.actionsJson) {
                        WorkflowStep.listFromJson(workflow.actionsJson)
                    }
                    val isExpanded = expandedWorkflowId == workflow.id

                    WorkflowCardItem(
                        workflow = workflow,
                        steps = steps,
                        isExpanded = isExpanded,
                        isPlaying = isPlaying && currentWorkflow?.id == workflow.id,
                        onToggleExpand = {
                            expandedWorkflowId = if (isExpanded) null else workflow.id
                        },
                        onRun = {
                            scope.launch {
                                executionStatusText = "Executing \"${workflow.name}\"..."
                                val report = WorkflowPlayer.executeWorkflow(context, workflow) { progress ->
                                    executionStatusText = progress
                                }
                                executionStatusText = if (report.success) {
                                    "Completed \"${workflow.name}\" successfully."
                                } else {
                                    "Execution halted: ${report.failureReason}"
                                }
                            }
                        },
                        onRename = {
                            workflowToRename = workflow
                            newWorkflowName = workflow.name
                        },
                        onDuplicate = {
                            scope.launch {
                                repository.duplicateWorkflow(workflow.id)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                repository.deleteWorkflow(workflow.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // CREATE / RECORD NAME DIALOG
    if (showCreateDialog) {
        var workflowNameInput by remember { mutableStateOf("Daily YouTube") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Teach SIGMA New Workflow", color = SigmaWhite) },
            text = {
                Column {
                    Text(
                        "Give this workflow a recognizable name. You can trigger it later by saying \"Sigma, run [Name]\".",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = workflowNameInput,
                        onValueChange = { workflowNameInput = it },
                        label = { Text("Workflow Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        WorkflowRecorder.startRecording(workflowNameInput.ifEmpty { "Learned Routine" })
                    }
                ) {
                    Text("START RECORDING", color = SigmaNeonRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("CANCEL", color = Color(0xFF888888))
                }
            },
            containerColor = Color(0xFF14080B)
        )
    }

    // RENAME DIALOG
    workflowToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { workflowToRename = null },
            title = { Text("Rename Workflow", color = SigmaWhite) },
            text = {
                OutlinedTextField(
                    value = newWorkflowName,
                    onValueChange = { newWorkflowName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newWorkflowName.isNotBlank()) {
                            scope.launch {
                                repository.updateWorkflow(target.copy(name = newWorkflowName.trim()))
                            }
                        }
                        workflowToRename = null
                    }
                ) {
                    Text("SAVE", color = SigmaNeonRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { workflowToRename = null }) {
                    Text("CANCEL", color = Color(0xFF888888))
                }
            },
            containerColor = Color(0xFF14080B)
        )
    }
}

@Composable
private fun WorkflowCardItem(
    workflow: RecordedWorkflow,
    steps: List<WorkflowStep>,
    isExpanded: Boolean,
    isPlaying: Boolean,
    onToggleExpand: () -> Unit,
    onRun: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(workflow.lastUsedTime) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(workflow.lastUsedTime))
    }

    SigmaGlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isPlaying) SigmaNeonRed else SigmaNeonRed.copy(alpha = 0.25f)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.name,
                        color = SigmaWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${steps.size} steps • Run ${workflow.runCount} times • Last used: $dateStr",
                        color = Color(0xFF999999),
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // RUN BUTTON
                    IconButton(
                        onClick = onRun,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SigmaNeonRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run Workflow",
                            tint = SigmaWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = Color(0xFFCCCCCC)
                        )
                    }
                }
            }

            // EXPANDED ACTION DETAILS
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "ACTION SEQUENCE",
                        color = SigmaStatusThinking,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    steps.forEachIndexed { idx, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF14080A))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(SigmaRedDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = SigmaWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${step.actionType.name}: ${step.description.ifEmpty { step.targetText.ifEmpty { step.targetResourceId } }}",
                                    color = SigmaWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (step.textToType.isNotBlank()) {
                                    Text(
                                        text = "Text: \"${step.textToType}\"",
                                        color = SigmaStatusThinking,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // WORKFLOW MANAGEMENT ACTIONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onRename) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color(0xFFCCCCCC))
                        }
                        IconButton(onClick = onDuplicate) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color(0xFFCCCCCC))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SigmaNeonRed)
                        }
                    }
                }
            }
        }
    }
}
