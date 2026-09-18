package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SigmaDao {
    @Query("SELECT * FROM action_audit_logs ORDER BY timestamp DESC LIMIT 50")
    fun getAllLogs(): Flow<List<ActionAuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActionAuditLog): Long

    @Query("DELETE FROM action_audit_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM user_memory ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<UserMemory>>

    @Query("SELECT value FROM user_memory WHERE `key` = :key LIMIT 1")
    suspend fun getMemory(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: UserMemory)

    @Query("DELETE FROM user_memory WHERE `key` = :key")
    suspend fun deleteMemory(key: String)

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 100")
    fun getAllHistory(): Flow<List<CommandHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: CommandHistoryItem): Long

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()

    // WORKFLOW QUERIES
    @Query("SELECT * FROM recorded_workflows ORDER BY isFavorite DESC, lastUsedTime DESC")
    fun getAllWorkflows(): Flow<List<RecordedWorkflow>>

    @Query("SELECT * FROM recorded_workflows WHERE id = :id LIMIT 1")
    suspend fun getWorkflowById(id: Long): RecordedWorkflow?

    @Query("SELECT * FROM recorded_workflows WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getWorkflowByName(name: String): RecordedWorkflow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: RecordedWorkflow): Long

    @Update
    suspend fun updateWorkflow(workflow: RecordedWorkflow)

    @Query("DELETE FROM recorded_workflows WHERE id = :id")
    suspend fun deleteWorkflow(id: Long)

    @Query("UPDATE recorded_workflows SET lastUsedTime = :now, runCount = runCount + 1 WHERE id = :id")
    suspend fun incrementWorkflowRun(id: Long, now: Long = System.currentTimeMillis())
}
