package com.ticketassistant.android.domain

import java.math.BigDecimal
import java.math.RoundingMode

object TicketTaskValidator {
    const val MIN_SUBMIT_ATTEMPTS = 1
    const val MAX_SUBMIT_ATTEMPTS = 100
    const val MIN_RUNTIME_MINUTES = 1L
    const val MAX_RUNTIME_MINUTES = 360L
    const val MAX_TICKET_COUNT = 6

    fun validate(draft: TicketTaskDraft): ValidationResult {
        val issues = buildList {
            if (draft.eventKeyword.trim().isEmpty()) {
                add(ValidationIssue(ConfigField.EVENT_KEYWORD, "请输入项目识别关键词"))
            }
            if (draft.targetSession.trim().isEmpty()) {
                add(ValidationIssue(ConfigField.TARGET_SESSION, "请输入目标场次"))
            }
            if (draft.targetTier.trim().isEmpty()) {
                add(ValidationIssue(ConfigField.TARGET_TIER, "请输入目标票档"))
            }

            val priceFen = parsePriceFen(draft.targetPriceYuan)
            if (priceFen == null || priceFen <= 0L) {
                add(ValidationIssue(ConfigField.TARGET_PRICE, "请输入有效的目标单价"))
            }

            val ticketCount = draft.ticketCount.toIntOrNull()
            if (ticketCount == null || ticketCount !in 1..MAX_TICKET_COUNT) {
                add(
                    ValidationIssue(
                        ConfigField.TICKET_COUNT,
                        "票数必须在 1 到 $MAX_TICKET_COUNT 之间",
                    ),
                )
            }

            if (!draft.attendeesConfigured) {
                add(
                    ValidationIssue(
                        ConfigField.ATTENDEES_CONFIGURED,
                        "请先在大麦中完成实名观演人配置",
                    ),
                )
            }

            val maxAttempts = draft.maxSubmitAttempts.toIntOrNull()
            if (maxAttempts == null || maxAttempts !in MIN_SUBMIT_ATTEMPTS..MAX_SUBMIT_ATTEMPTS) {
                add(
                    ValidationIssue(
                        ConfigField.MAX_SUBMIT_ATTEMPTS,
                        "提交次数必须在 $MIN_SUBMIT_ATTEMPTS 到 $MAX_SUBMIT_ATTEMPTS 之间",
                    ),
                )
            }

            val runtimeMinutes = draft.maxRuntimeMinutes.toLongOrNull()
            if (runtimeMinutes == null || runtimeMinutes !in MIN_RUNTIME_MINUTES..MAX_RUNTIME_MINUTES) {
                add(
                    ValidationIssue(
                        ConfigField.MAX_RUNTIME,
                        "运行时长必须在 $MIN_RUNTIME_MINUTES 到 $MAX_RUNTIME_MINUTES 分钟之间",
                    ),
                )
            }
        }

        if (issues.isNotEmpty()) {
            return ValidationResult.Invalid(issues)
        }

        return ValidationResult.Valid(
            ValidatedTicketTask(
                platform = draft.platform,
                runMode = draft.runMode,
                eventKeyword = draft.eventKeyword.trim(),
                targetSession = draft.targetSession.trim(),
                targetTier = draft.targetTier.trim(),
                targetPriceFen = requireNotNull(parsePriceFen(draft.targetPriceYuan)),
                ticketCount = requireNotNull(draft.ticketCount.toIntOrNull()),
                adapterConfig = """{"attendeesConfigured":true}""",
                maxSubmitAttempts = requireNotNull(draft.maxSubmitAttempts.toIntOrNull()),
                maxRuntimeSeconds = requireNotNull(draft.maxRuntimeMinutes.toLongOrNull()) * 60L,
            ),
        )
    }

    fun parsePriceFen(value: String): Long? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null

        return runCatching {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }
}
