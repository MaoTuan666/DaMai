package com.ticketassistant.android.domain

import kotlinx.coroutines.flow.Flow

interface TicketTaskRepository {
    fun observeLatestTask(): Flow<TicketTask?>

    suspend fun getTask(taskId: Long): TicketTask?

    suspend fun createArmedTask(
        task: ValidatedTicketTask,
        runId: String,
    ): Long

    suspend fun stopTask(
        taskId: Long,
        runId: String,
        reason: String,
    )

    suspend fun updateRuntimeState(
        taskId: Long,
        runId: String,
        state: TaskState,
        eventCode: String,
        sanitizedDetail: String,
    )

    suspend fun clearRunLogs()
}
