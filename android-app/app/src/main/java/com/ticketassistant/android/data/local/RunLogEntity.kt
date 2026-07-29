package com.ticketassistant.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "run_log",
    indices = [Index(value = ["run_id", "created_at"])],
)
data class RunLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "run_id")
    val runId: String,
    val phase: String,
    @ColumnInfo(name = "event_code")
    val eventCode: String,
    @ColumnInfo(name = "sanitized_detail")
    val sanitizedDetail: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
)
