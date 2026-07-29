package com.ticketassistant.android.runtime

import com.ticketassistant.android.platform.api.ActionDecision
import com.ticketassistant.android.platform.api.PageResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

data class AccessibilityActionCommand(
    val runId: String,
    val snapshotSequence: Long,
    val expectedPackageName: String,
    val scheduledAction: ScheduledAction,
    val page: PageResult.Recognized,
    val decision: ExecutableActionDecision,
) {
    init {
        require(runId == scheduledAction.runId)
        require(snapshotSequence > 0)
        require(expectedPackageName.isNotBlank())
        require(scheduledAction.candidate.pageFingerprint == page.fingerprint.value)
        require(scheduledAction.candidate.actionKey == decision.eventCode)
    }
}

sealed interface ExecutableActionDecision {
    val eventCode: String

    data class Click(val value: ActionDecision.Click) : ExecutableActionDecision {
        override val eventCode: String
            get() = value.eventCode
    }

    data class GlobalBack(val value: ActionDecision.GlobalBack) : ExecutableActionDecision {
        override val eventCode: String
            get() = value.eventCode
    }
}

data class AccessibilityActionResult(
    val runId: String,
    val actionId: Long,
    val result: ActionExecutionResult,
)

object AccessibilityActionBus {
    private val commandChannel = Channel<AccessibilityActionCommand>(Channel.BUFFERED)
    private val resultChannel = Channel<AccessibilityActionResult>(Channel.BUFFERED)

    val commands: Flow<AccessibilityActionCommand> = commandChannel.receiveAsFlow()
    val results: Flow<AccessibilityActionResult> = resultChannel.receiveAsFlow()

    fun submit(command: AccessibilityActionCommand): Boolean =
        commandChannel.trySend(command).isSuccess

    fun complete(result: AccessibilityActionResult): Boolean =
        resultChannel.trySend(result).isSuccess
}
