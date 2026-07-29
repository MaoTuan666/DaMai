package com.ticketassistant.android.platform.api

import com.ticketassistant.android.accessibility.UiSnapshot
import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TicketPlatform
import com.ticketassistant.android.domain.TicketTask
import com.ticketassistant.android.runtime.ActionPolicy
import com.ticketassistant.android.runtime.EngineRuntimeState

/**
 * A platform adapter is a pure decision component.
 *
 * Implementations receive immutable snapshots and may only return standard results. They must
 * not hold an AccessibilityService, perform clicks, schedule retries, or mutate task targets.
 */
interface PlatformAdapter {
    val platform: TicketPlatform
    val supportedPackages: Set<String>
    val supportedModes: Set<RunMode>
    val timingPolicy: AdapterTimingPolicy
        get() = AdapterTimingPolicy()

    fun detectPage(
        snapshot: UiSnapshot,
        task: TicketTask,
    ): PageResult

    fun decideAction(
        page: PageResult.Recognized,
        task: TicketTask,
        runtime: EngineRuntimeState,
    ): ActionDecision
}

fun AdapterTimingPolicy.toActionPolicy(): ActionPolicy = ActionPolicy(
    minimumActionIntervalMillis = minimumActionIntervalMillis,
    unchangedPageCooldownMillis = unchangedPageCooldownMillis,
    maxAttemptsPerFingerprint = maxAttemptsPerFingerprint,
)
