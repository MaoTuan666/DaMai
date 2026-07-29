package com.ticketassistant.android.accessibility

data class UiSnapshot(
    val runId: String,
    val captureSequence: Long,
    val packageName: String,
    val capturedAtEpochMillis: Long,
    val windows: List<UiWindowSnapshot>,
)

data class UiWindowSnapshot(
    val id: Int,
    val type: Int,
    val layer: Int,
    val title: String?,
    val boundsInScreen: UiBounds,
    val isActive: Boolean,
    val isFocused: Boolean,
    val isAccessibilityFocused: Boolean,
    val isInPictureInPictureMode: Boolean,
    val root: UiNodeSnapshot?,
)

data class UiNodeSnapshot(
    val path: List<Int>,
    val packageName: String?,
    val viewIdResourceName: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val boundsInScreen: UiBounds,
    val isVisibleToUser: Boolean,
    val isEnabled: Boolean,
    val isClickable: Boolean,
    val isSelected: Boolean,
    val isCheckable: Boolean,
    val checkedState: UiCheckedState,
    val isFocusable: Boolean,
    val isScrollable: Boolean,
    val actionIds: Set<Int>,
    val children: List<UiNodeSnapshot>,
)

enum class UiCheckedState {
    NOT_CHECKABLE,
    UNCHECKED,
    CHECKED,
    PARTIAL,
}

data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
