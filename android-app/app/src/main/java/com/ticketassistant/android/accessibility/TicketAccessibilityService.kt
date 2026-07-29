package com.ticketassistant.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ticketassistant.android.overlay.AccessibilityOverlayController
import com.ticketassistant.android.runtime.AccessibilityActionBus
import com.ticketassistant.android.runtime.ActiveRunStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TicketAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val debouncePolicy = SnapshotDebouncePolicy(
        stabilityWindowMillis = STABILITY_WINDOW_MILLIS,
        maximumWaitMillis = MAXIMUM_STABILITY_WAIT_MILLIS,
    )
    private var pendingCapture: Job? = null
    private var burstStartedAtMillis: Long? = null
    private var pendingRunId: String? = null
    private var captureSequence = 0L
    private var captureRequestJob: Job? = null
    private var actionExecutionJob: Job? = null
    private var overlayController: AccessibilityOverlayController? = null
    private var controlledExecutor: ControlledAccessibilityExecutor? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (overlayController == null) {
            overlayController = AccessibilityOverlayController(this).also {
                it.start(serviceScope)
            }
        }
        if (captureRequestJob == null) {
            captureRequestJob = serviceScope.launch {
                SnapshotCaptureRequests.requests.collect(::captureRequestedSnapshot)
            }
        }
        if (controlledExecutor == null) {
            controlledExecutor = ControlledAccessibilityExecutor(this)
        }
        if (actionExecutionJob == null) {
            actionExecutionJob = serviceScope.launch {
                AccessibilityActionBus.commands.collect { command ->
                    controlledExecutor?.execute(command)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val runId = ActiveRunStore.session.value?.runId ?: return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in SUPPORTED_PACKAGES) return

        scheduleStableSnapshot(runId, packageName)
    }

    override fun onInterrupt() {
        cancelPendingCapture()
        AccessibilitySnapshotStore.clear()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController?.onConfigurationChanged()
    }

    override fun onDestroy() {
        cancelPendingCapture()
        AccessibilitySnapshotStore.clear()
        captureRequestJob?.cancel()
        captureRequestJob = null
        actionExecutionJob?.cancel()
        actionExecutionJob = null
        controlledExecutor = null
        overlayController?.stop()
        overlayController = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun captureRequestedSnapshot(runId: String) {
        if (!ActiveRunStore.isActive(runId)) return
        val packageName = rootInActiveWindow?.packageName?.toString() ?: return
        if (packageName !in SUPPORTED_PACKAGES) return
        scheduleStableSnapshot(runId, packageName)
    }

    private fun scheduleStableSnapshot(
        runId: String,
        packageName: String,
    ) {
        if (pendingRunId != runId) {
            cancelPendingCapture()
            pendingRunId = runId
        }
        val eventAtMillis = SystemClock.elapsedRealtime()
        val burstStart = burstStartedAtMillis ?: eventAtMillis.also {
            burstStartedAtMillis = it
        }
        val delayMillis = debouncePolicy.delayMillis(
            burstStartedAtMillis = burstStart,
            eventAtMillis = eventAtMillis,
        )

        pendingCapture?.cancel()
        pendingCapture = serviceScope.launch {
            delay(delayMillis)
            if (!ActiveRunStore.isActive(runId)) {
                resetPendingCapture()
                return@launch
            }
            captureSnapshot(runId, packageName)
            resetPendingCapture()
        }
    }

    private fun captureSnapshot(
        runId: String,
        packageName: String,
    ) {
        captureSequence += 1
        val snapshot = UiSnapshot(
            runId = runId,
            captureSequence = captureSequence,
            packageName = packageName,
            capturedAtEpochMillis = System.currentTimeMillis(),
            windows = windows
                .asSequence()
                .filterNot { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
                .sortedByDescending(AccessibilityWindowInfo::getLayer)
                .map(::snapshotWindow)
                .toList(),
        )
        AccessibilitySnapshotStore.update(snapshot)
    }

    private fun cancelPendingCapture() {
        pendingCapture?.cancel()
        resetPendingCapture()
    }

    private fun resetPendingCapture() {
        pendingCapture = null
        burstStartedAtMillis = null
        pendingRunId = null
    }

    private fun snapshotWindow(window: AccessibilityWindowInfo): UiWindowSnapshot {
        val bounds = Rect()
        window.getBoundsInScreen(bounds)
        return UiWindowSnapshot(
            id = window.id,
            type = window.type,
            layer = window.layer,
            title = window.title?.toString(),
            boundsInScreen = bounds.toUiBounds(),
            isActive = window.isActive,
            isFocused = window.isFocused,
            isAccessibilityFocused = window.isAccessibilityFocused,
            isInPictureInPictureMode = window.isInPictureInPictureMode,
            root = window.root?.let {
                snapshotNode(
                    node = it,
                    path = emptyList(),
                    depth = 0,
                    budget = NodeBudget(),
                )
            },
        )
    }

    private fun snapshotNode(
        node: AccessibilityNodeInfo,
        path: List<Int>,
        depth: Int,
        budget: NodeBudget,
    ): UiNodeSnapshot? {
        if (depth > MAX_DEPTH || !budget.take()) return null

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val children = buildList {
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                snapshotNode(
                    node = child,
                    path = path + index,
                    depth = depth + 1,
                    budget = budget,
                )?.let(::add)
            }
        }

        return UiNodeSnapshot(
            path = path,
            packageName = node.packageName?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            className = node.className?.toString(),
            boundsInScreen = bounds.toUiBounds(),
            isVisibleToUser = node.isVisibleToUser,
            isEnabled = node.isEnabled,
            isClickable = node.isClickable,
            isSelected = node.isSelected,
            isCheckable = node.isCheckable,
            checkedState = checkedState(node),
            isFocusable = node.isFocusable,
            isScrollable = node.isScrollable,
            actionIds = node.actionList.mapTo(mutableSetOf()) { it.id },
            children = children,
        )
    }

    private fun checkedState(node: AccessibilityNodeInfo): UiCheckedState {
        if (!node.isCheckable) return UiCheckedState.NOT_CHECKABLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            return when (node.checked) {
                AccessibilityNodeInfo.CHECKED_STATE_TRUE -> UiCheckedState.CHECKED
                AccessibilityNodeInfo.CHECKED_STATE_PARTIAL -> UiCheckedState.PARTIAL
                else -> UiCheckedState.UNCHECKED
            }
        }

        @Suppress("DEPRECATION")
        return if (node.isChecked) UiCheckedState.CHECKED else UiCheckedState.UNCHECKED
    }

    private fun Rect.toUiBounds(): UiBounds = UiBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )

    private class NodeBudget {
        private var remaining = MAX_NODES

        fun take(): Boolean {
            if (remaining == 0) return false
            remaining -= 1
            return true
        }
    }

    companion object {
        val SUPPORTED_PACKAGES: Set<String> = setOf("cn.damai")
        private const val STABILITY_WINDOW_MILLIS = 180L
        private const val MAXIMUM_STABILITY_WAIT_MILLIS = 800L
        private const val MAX_DEPTH = 50
        private const val MAX_NODES = 2_000
    }
}
