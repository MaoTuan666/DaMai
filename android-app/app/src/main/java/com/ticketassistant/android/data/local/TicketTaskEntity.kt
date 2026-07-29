package com.ticketassistant.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.domain.TicketPlatform
import com.ticketassistant.android.domain.TicketTask
import java.time.Instant

@Entity(tableName = "ticket_task")
data class TicketTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val platform: String,
    @ColumnInfo(name = "run_mode")
    val runMode: String,
    @ColumnInfo(name = "event_keyword")
    val eventKeyword: String,
    @ColumnInfo(name = "target_session")
    val targetSession: String,
    @ColumnInfo(name = "target_tier")
    val targetTier: String,
    @ColumnInfo(name = "target_price_fen")
    val targetPriceFen: Long,
    @ColumnInfo(name = "ticket_count")
    val ticketCount: Int,
    @ColumnInfo(name = "adapter_config")
    val adapterConfig: String,
    @ColumnInfo(name = "max_submit_attempts")
    val maxSubmitAttempts: Int,
    @ColumnInfo(name = "max_runtime_seconds")
    val maxRuntimeSeconds: Long,
    val state: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)

fun TicketTaskEntity.toDomain(): TicketTask = TicketTask(
    id = id,
    platform = TicketPlatform.valueOf(platform),
    runMode = RunMode.valueOf(runMode),
    eventKeyword = eventKeyword,
    targetSession = targetSession,
    targetTier = targetTier,
    targetPriceFen = targetPriceFen,
    ticketCount = ticketCount,
    adapterConfig = adapterConfig,
    maxSubmitAttempts = maxSubmitAttempts,
    maxRuntimeSeconds = maxRuntimeSeconds,
    state = TaskState.valueOf(state),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
