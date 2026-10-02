package com.example.data.dao

import androidx.room.*
import com.example.data.model.ForwardingLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardingLogDao {
    @Query("SELECT * FROM forwarding_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<ForwardingLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ForwardingLog): Long

    @Query("DELETE FROM forwarding_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM forwarding_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)
}
