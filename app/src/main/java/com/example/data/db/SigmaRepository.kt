package com.example.data.db

import kotlinx.coroutines.flow.Flow

class SigmaRepository(private val dao: SigmaDao) {
    val allLogs: Flow<List<ActionAuditLog>> = dao.getAllLogs()
    val allMemories: Flow<List<UserMemory>> = dao.getAllMemories()

    suspend fun logAction(
        command: String,
        actionType: String,
        target: String,
        status: String,
        details: String = ""
    ) {
        dao.insertLog(
            ActionAuditLog(
                command = command,
                actionType = actionType,
                target = target,
                status = status,
                details = details
            )
        )
    }

    suspend fun clearLogs() = dao.clearLogs()

    suspend fun remember(key: String, value: String) {
        dao.saveMemory(UserMemory(key = key, value = value))
    }

    suspend fun recall(key: String): String? = dao.getMemory(key)

    suspend fun forget(key: String) = dao.deleteMemory(key)
}
