package com.ticketassistant.android.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object TicketTaskValidator {
    fun validate(draft: TicketTaskDraft): ValidationResult {
        val issues = buildList {
            if (parseDate(draft.targetDate) == null) {
                add(ValidationIssue(ConfigField.TARGET_DATE, "请选择演出日期"))
            }

            val priceFen = parsePriceFen(draft.targetPriceYuan)
            if (priceFen == null || priceFen <= 0L) {
                add(ValidationIssue(ConfigField.TARGET_PRICE, "请输入有效的票档价格"))
            }

        }

        if (issues.isNotEmpty()) {
            return ValidationResult.Invalid(issues)
        }

        return ValidationResult.Valid(
            ValidatedTicketTask(
                platform = draft.platform,
                runMode = draft.runMode,
                targetDate = requireNotNull(parseDate(draft.targetDate)).toString(),
                targetPriceFen = requireNotNull(parsePriceFen(draft.targetPriceYuan)),
                adapterConfig = "{}",
            ),
        )
    }

    fun parsePriceFen(value: String): Long? {
        val normalized = value.trim()
        if (
            normalized.isEmpty() ||
            normalized != value ||
            !normalized.all(Char::isDigit)
        ) {
            return null
        }

        return runCatching {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }

    fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim()) }.getOrNull()
}
