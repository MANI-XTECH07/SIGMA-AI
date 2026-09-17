package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_audit_logs")
data class ActionAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val command: String,
    val actionType: String,
    val target: String,
    val status: String, // "SUCCESS", "CONFIRMED", "CANCELLED", "FAILED"
    val details: String = ""
)

@Entity(tableName = "user_memory")
data class UserMemory(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_history")
data class CommandHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean,
    val shortResult: String
)

