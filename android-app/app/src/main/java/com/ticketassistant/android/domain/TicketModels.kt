package com.ticketassistant.android.domain

import java.time.Instant

enum class TicketPlatform(val displayName: String) {
    DAMAI("大麦"),
}

enum class RunMode(
    val displayName: String,
    val includesReturnPhase: Boolean,
) {
    SALE_ONLY("只抢票", false),
    RETURN_ONLY("只抢回流", true),
    SALE_THEN_RETURN("抢票后接回流", true),
}

enum class TaskState {
    CONFIG,
    WAIT_TARGET_APP,
    RUNNING,
    USER_PAUSED,
    SAFETY_PAUSED,
    ORDER_LOCKED,
    STOPPED,
}

data class TicketTask(
    val id: Long,
    val platform: TicketPlatform,
    val runMode: RunMode,
    val eventKeyword: String,
    val targetSession: String,
    val targetTier: String,
    val targetPriceFen: Long,
    val ticketCount: Int,
    val adapterConfig: String,
    val maxSubmitAttempts: Int,
    val maxRuntimeSeconds: Long,
    val state: TaskState,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TicketTaskDraft(
    val platform: TicketPlatform = TicketPlatform.DAMAI,
    val runMode: RunMode = RunMode.SALE_ONLY,
    val eventKeyword: String = "",
    val targetSession: String = "",
    val targetTier: String = "",
    val targetPriceYuan: String = "",
    val ticketCount: String = "1",
    val attendeesConfigured: Boolean = false,
    val maxSubmitAttempts: String = "20",
    val maxRuntimeMinutes: String = "30",
)

data class ValidatedTicketTask(
    val platform: TicketPlatform,
    val runMode: RunMode,
    val eventKeyword: String,
    val targetSession: String,
    val targetTier: String,
    val targetPriceFen: Long,
    val ticketCount: Int,
    val adapterConfig: String,
    val maxSubmitAttempts: Int,
    val maxRuntimeSeconds: Long,
)

data class ValidationIssue(
    val field: ConfigField,
    val message: String,
)

enum class ConfigField {
    EVENT_KEYWORD,
    TARGET_SESSION,
    TARGET_TIER,
    TARGET_PRICE,
    TICKET_COUNT,
    ATTENDEES_CONFIGURED,
    MAX_SUBMIT_ATTEMPTS,
    MAX_RUNTIME,
}

sealed interface ValidationResult {
    data class Valid(val task: ValidatedTicketTask) : ValidationResult
    data class Invalid(val issues: List<ValidationIssue>) : ValidationResult
}
