package com.ticketassistant.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketTaskDao {
    @Insert
    suspend fun insert(task: TicketTaskEntity): Long

    @Query("SELECT * FROM ticket_task ORDER BY updated_at DESC LIMIT 1")
    fun observeLatest(): Flow<TicketTaskEntity?>

    @Query("SELECT * FROM ticket_task WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: Long): TicketTaskEntity?

    @Query(
        """
        UPDATE ticket_task
        SET state = :state, updated_at = :updatedAt
        WHERE id = :taskId
        """,
    )
    suspend fun updateState(
        taskId: Long,
        state: String,
        updatedAt: Instant,
    ): Int
}

@Dao
interface RunLogDao {
    @Insert
    suspend fun insert(log: RunLogEntity): Long

    @Query("DELETE FROM run_log")
    suspend fun clear()
}
