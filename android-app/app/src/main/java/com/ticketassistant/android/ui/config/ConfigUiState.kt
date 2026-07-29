package com.ticketassistant.android.ui.config

import com.ticketassistant.android.domain.ConfigField
import com.ticketassistant.android.domain.TicketTaskDraft
import com.ticketassistant.android.domain.TicketTaskValidator
import com.ticketassistant.android.domain.ValidationResult
import com.ticketassistant.android.runtime.NotificationPermissionState

data class ConfigUiState(
    val draft: TicketTaskDraft = TicketTaskDraft(),
    val fieldErrors: Map<ConfigField, String> = emptyMap(),
    val accessibilityEnabled: Boolean = false,
    val notificationPermissionState: NotificationPermissionState =
        NotificationPermissionState.RUNTIME_PERMISSION_REQUIRED,
    val runActive: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val pendingRunStart: PendingRunStart? = null,
) {
    val canStart: Boolean
        get() = accessibilityEnabled &&
            notificationPermissionState == NotificationPermissionState.GRANTED &&
            !runActive &&
            !isSaving &&
            pendingRunStart == null &&
            TicketTaskValidator.validate(draft) is ValidationResult.Valid
}

data class PendingRunStart(
    val runId: String,
    val taskId: Long,
)
