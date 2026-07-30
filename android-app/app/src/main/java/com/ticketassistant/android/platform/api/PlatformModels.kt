package com.ticketassistant.android.platform.api

import com.ticketassistant.android.runtime.AutomationPhase

@JvmInline
value class PlatformPageType(val code: String) {
    init {
        require(code.matches(CODE_PATTERN)) {
            "Page type must use uppercase letters, digits, and underscores"
        }
    }
}

@JvmInline
value class PageFingerprint(val value: String) {
    init {
        require(value.isNotBlank())
        require(value.length <= MAX_FINGERPRINT_LENGTH)
    }
}

data class NodeReference(
    val windowId: Int,
    val path: List<Int>,
) {
    init {
        require(path.all { it >= 0 })
    }
}

enum class EvidenceKind {
    VIEW_ID,
    CONTENT_DESCRIPTION,
    TEXT_AND_ROLE,
    RELATIVE_STRUCTURE,
    WINDOW,
}

data class PageEvidence(
    val node: NodeReference,
    val kind: EvidenceKind,
    val evidenceCode: String,
) {
    init {
        requireStandardCode(evidenceCode, "Evidence code")
    }
}

enum class PageUnknownReason {
    INSUFFICIENT_EVIDENCE,
    UNKNOWN_TOP_WINDOW,
    LOGIN_REQUIRED,
    VERIFICATION_REQUIRED,
    RISK_CONTROL_REQUIRED,
    DEVICE_CHECK_REQUIRED,
    PERMISSION_REQUIRED,
}

enum class TargetField {
    PLATFORM,
    DATE,
    PRICE,
}

sealed interface PageResult {
    data class Recognized(
        val pageType: PlatformPageType,
        val fingerprint: PageFingerprint,
        val windowId: Int,
        val evidence: List<PageEvidence>,
        val validStartPhases: Set<AutomationPhase> = emptySet(),
        val observationEventCode: String? = null,
    ) : PageResult {
        init {
            require(evidence.isNotEmpty())
            require(evidence.all { it.node.windowId == windowId })
            observationEventCode?.let {
                requireStandardCode(it, "Page observation event code")
            }
        }

        fun isValidStartFor(phase: AutomationPhase): Boolean = phase in validStartPhases
    }

    data class Unknown(
        val reason: PageUnknownReason,
        val eventCode: String? = null,
    ) : PageResult {
        init {
            eventCode?.let { requireStandardCode(it, "Unknown page event code") }
        }
    }

    data class Mismatch(
        val field: TargetField,
        val expected: String,
        val actual: String?,
        val eventCode: String? = null,
    ) : PageResult {
        init {
            require(expected.isNotBlank())
            eventCode?.let { requireStandardCode(it, "Mismatch event code") }
        }
    }
}

enum class ClickResolution {
    EXACT_NODE,
    NEAREST_CLICKABLE_ANCESTOR,
}

data class ClickTarget(
    val node: NodeReference,
    val expectedPackageName: String,
    val expectedPageFingerprint: PageFingerprint,
    val resolution: ClickResolution = ClickResolution.EXACT_NODE,
    val maximumAncestorDepth: Int = 0,
) {
    init {
        require(expectedPackageName.isNotBlank())
        require(maximumAncestorDepth in 0..MAX_CLICKABLE_ANCESTOR_DEPTH)
        if (resolution == ClickResolution.EXACT_NODE) {
            require(maximumAncestorDepth == 0)
        }
    }
}

enum class WaitReason {
    SALE_NOT_OPEN,
    PAGE_NOT_ACTIONABLE,
    EXPECTED_PAGE_CHANGE,
    EXPECTED_POPUP_CHANGE,
    TARGET_TIER_UNAVAILABLE,
}

enum class AdapterStopReason {
    SALE_FLOW_EXHAUSTED,
    PLATFORM_FLOW_COMPLETE,
    ORDER_LOCKED,
}

sealed interface ActionDecision {
    data class Click(
        val target: ClickTarget,
        val eventCode: String,
    ) : ActionDecision {
        init {
            requireStandardCode(eventCode, "Action event code")
        }
    }

    data class GlobalBack(
        val expectedPageFingerprint: PageFingerprint,
        val eventCode: String,
    ) : ActionDecision {
        init {
            requireStandardCode(eventCode, "Action event code")
        }
    }

    data class Wait(
        val reason: WaitReason,
        val eventCode: String? = null,
    ) : ActionDecision {
        init {
            eventCode?.let { requireStandardCode(it, "Wait event code") }
        }
    }

    data class SwitchPhase(
        val phase: AutomationPhase,
        val eventCode: String,
    ) : ActionDecision {
        init {
            requireStandardCode(eventCode, "Phase event code")
        }
    }

    data class Stop(
        val reason: AdapterStopReason,
        val eventCode: String,
    ) : ActionDecision {
        init {
            requireStandardCode(eventCode, "Stop event code")
        }
    }
}

data class AdapterTimingPolicy(
    val minimumActionIntervalMillis: Long = 250L,
    val unchangedPageCooldownMillis: Long = 800L,
    val maxAttemptsPerFingerprint: Int = 3,
) {
    init {
        require(minimumActionIntervalMillis >= 0)
        require(unchangedPageCooldownMillis >= minimumActionIntervalMillis)
        require(maxAttemptsPerFingerprint in 1..MAX_ATTEMPTS_PER_FINGERPRINT)
    }
}

private val CODE_PATTERN = Regex("[A-Z][A-Z0-9_]{1,63}")
private const val MAX_FINGERPRINT_LENGTH = 128
private const val MAX_CLICKABLE_ANCESTOR_DEPTH = 8
private const val MAX_ATTEMPTS_PER_FINGERPRINT = 10

private fun requireStandardCode(
    value: String,
    label: String,
) {
    require(value.matches(CODE_PATTERN)) {
        "$label must use uppercase letters, digits, and underscores"
    }
}
