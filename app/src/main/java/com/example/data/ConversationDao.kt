package com.example.data

import com.example.data.db.CommandHistoryItem
import com.example.data.db.SigmaDao
import kotlinx.coroutines.flow.Flow

class ConversationDao(private val sigmaDao: SigmaDao) {
    fun getAllHistory(): Flow<List<CommandHistoryItem>> = sigmaDao.getAllHistory()
    suspend fun insertHistory(item: CommandHistoryItem): Long = sigmaDao.insertHistory(item)
    suspend fun clearHistory() = sigmaDao.clearHistory()
}
