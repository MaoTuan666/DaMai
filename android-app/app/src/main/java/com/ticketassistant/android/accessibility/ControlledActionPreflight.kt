package com.ticketassistant.android.accessibility

import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.runtime.AccessibilityActionCommand
import com.ticketassistant.android.runtime.ActionExecutionResult
import com.ticketassistant.android.runtime.ActiveRunSession

object ControlledActionPreflight {
    fun validate(
        command: AccessibilityActionCommand,
        session: ActiveRunSession?,
        latestSnapshot: UiSnapshot?,
        nowElapsedRealtimeMillis: Long,
        maximumCommandAgeMillis: Long,
    ): ActionExecutionResult? {
        val ageMillis = nowElapsedRealtimeMillis -
            command.scheduledAction.scheduledAtMillis
        if (ageMillis !in 0..maximumCommandAgeMillis) {
            return ActionExecutionResult.CANCELLED
        }

        val runtime = session?.runtimeState
        if (
            session?.runId != command.runId ||
            runtime == null ||
            runtime.taskState != TaskState.RUNNING ||
            !runtime.canScheduleAction(command.snapshotSequence)
        ) {
            return ActionExecutionResult.CANCELLED
        }

        if (
            latestSnapshot?.runId != command.runId ||
            latestSnapshot.captureSequence != command.snapshotSequence ||
            latestSnapshot.packageName != command.expectedPackageName
        ) {
            return ActionExecutionResult.CANCELLED
        }
        return null
    }

    fun intersects(
        target: UiBounds,
        regions: List<UiBounds>,
    ): Boolean = regions.any { region ->
        region.left < target.right &&
            region.right > target.left &&
            region.top < target.bottom &&
            region.bottom > target.top
    }
}
