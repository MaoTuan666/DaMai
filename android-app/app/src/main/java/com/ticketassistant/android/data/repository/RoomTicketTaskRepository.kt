package com.ticketassistant.android.data.repository

import androidx.room.withTransaction
import com.ticketassistant.android.data.local.RunLogEntity
import com.ticketassistant.android.data.local.TicketDatabase
import com.ticketassistant.android.data.local.TicketTaskEntity
import com.ticketassistant.android.data.local.toDomain
import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.domain.TicketTask
import com.ticketassistant.android.domain.TicketTaskRepository
import com.ticketassistant.android.domain.ValidatedTicketTask
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTicketTaskRepository(
    private val database: TicketDatabase,
) : TicketTaskRepository {
    private val taskDao = database.ticketTaskDao()
    private val runLogDao = database.runLogDao()

    override fun observeLatestTask(): Flow<TicketTask?> =
        taskDao.observeLatest().map { it?.toDomain() }

    override suspend fun getTask(taskId: Long): TicketTask? =
        taskDao.getById(taskId)?.toDomain()

    override suspend fun createArmedTask(
        task: ValidatedTicketTask,
        runId: String,
    ): Long {
        val now = Instant.now()
        return database.withTransaction {
            val taskId = taskDao.insert(
                TicketTaskEntity(
                    platform = task.platform.name,
                    runMode = task.runMode.name,
                    eventKeyword = "",
                    targetSession = task.targetDate,
                    targetTier = "",
                    targetPriceFen = task.targetPriceFen,
                    ticketCount = 1,
                    adapterConfig = task.adapterConfig,
                    // Keep legacy Room columns populated for schema compatibility.
                    // Runtime no longer uses submission-count or elapsed-time limits.
                    maxSubmitAttempts = 0,
                    maxRuntimeSeconds = 0,
                    state = TaskState.ARMED.name,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            runLogDao.insert(
                RunLogEntity(
                    runId = runId,
                    phase = TaskState.ARMED.name,
                    eventCode = "TASK_ARMED",
                    sanitizedDetail = "配置已校验，等待用户在悬浮窗点击开始",
                    createdAt = now,
                ),
            )
            taskId
        }
    }

    override suspend fun stopTask(
        taskId: Long,
        runId: String,
        reason: String,
    ) {
        updateRuntimeState(
            taskId = taskId,
            runId = runId,
            state = TaskState.STOPPED,
            eventCode = "TASK_STOPPED",
            sanitizedDetail = reason,
        )
    }

    override suspend fun updateRuntimeState(
        taskId: Long,
        runId: String,
        state: TaskState,
        eventCode: String,
        sanitizedDetail: String,
    ) {
        val now = Instant.now()
        database.withTransaction {
            val updatedRows = taskDao.updateState(
                taskId = taskId,
                state = state.name,
                updatedAt = now,
            )
            check(updatedRows == 1) { "Task $taskId does not exist" }
            runLogDao.insert(
                RunLogEntity(
                    runId = runId,
                    phase = state.name,
                    eventCode = eventCode,
                    sanitizedDetail = sanitizedDetail,
                    createdAt = now,
                ),
            )
        }
    }

    override suspend fun clearRunLogs() {
        runLogDao.clear()
    }
}
