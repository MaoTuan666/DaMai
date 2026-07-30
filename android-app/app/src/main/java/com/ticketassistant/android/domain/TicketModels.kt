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
    ARMED,
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
    val targetDate: String,
    val targetPriceFen: Long,
    val adapterConfig: String,
    val state: TaskState,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TicketTaskDraft(
    val platform: TicketPlatform = TicketPlatform.DAMAI,
    val runMode: RunMode = RunMode.SALE_ONLY,
    val targetDate: String = "",
    val targetPriceYuan: String = "",
)

data class ValidatedTicketTask(
    val platform: TicketPlatform,
    val runMode: RunMode,
    val targetDate: String,
    val targetPriceFen: Long,
    val adapterConfig: String,
)

data class ValidationIssue(
    val field: ConfigField,
    val message: String,
)

enum class ConfigField {
    TARGET_DATE,
    TARGET_PRICE,
}

sealed interface ValidationResult {
    data class Valid(val task: ValidatedTicketTask) : ValidationResult
    data class Invalid(val issues: List<ValidationIssue>) : ValidationResult
}
