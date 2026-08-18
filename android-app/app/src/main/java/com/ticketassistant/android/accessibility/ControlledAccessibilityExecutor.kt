package com.ticketassistant.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ticketassistant.android.overlay.OverlayBoundsStore
import com.ticketassistant.android.platform.api.ClickResolution
import com.ticketassistant.android.platform.api.NodeReference
import com.ticketassistant.android.runtime.AccessibilityActionBus
import com.ticketassistant.android.runtime.AccessibilityActionCommand
import com.ticketassistant.android.runtime.AccessibilityActionResult
import com.ticketassistant.android.runtime.ActionExecutionResult
import com.ticketassistant.android.runtime.ActiveRunStore
import com.ticketassistant.android.runtime.ExecutableActionDecision
import kotlinx.coroutines.CancellationException

class ControlledAccessibilityExecutor(
    private val service: AccessibilityService,
) {
    fun execute(command: AccessibilityActionCommand) {
        val result = ControlledExecutionBoundary.run {
            preflight(command) ?: when (val decision = command.decision) {
                is ExecutableActionDecision.Click -> executeClick(command, decision)
                is ExecutableActionDecision.GlobalBack -> executeGlobalBack(command, decision)
            }
        }
        AccessibilityActionBus.complete(
            AccessibilityActionResult(
                runId = command.runId,
                actionId = command.scheduledAction.actionId,
                result = result,
            ),
        )
    }

    private fun preflight(command: AccessibilityActionCommand): ActionExecutionResult? {
        val snapshot = AccessibilitySnapshotStore.latest.value
        ControlledActionPreflight.validate(
            command = command,
            session = ActiveRunStore.session.value,
            latestSnapshot = snapshot,
            nowElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            maximumCommandAgeMillis = MAX_COMMAND_AGE_MILLIS,
        )?.let { return it }
        requireNotNull(snapshot)

        val supportedWindow = SupportedAppWindowSelector.select(
            activePackageName = service.rootInActiveWindow?.packageName?.toString(),
            windows = service.windows
                .asSequence()
                .map { window ->
                    WindowPackage(
                        id = window.id,
                        layer = window.layer,
                        category = window.selectorCategory(),
                        packageName = window.root?.packageName?.toString(),
                    )
                }
                .toList(),
            supportedPackages = TicketAccessibilityService.SUPPORTED_PACKAGES,
            assistantPackageName = service.packageName,
        )
        if (
            supportedWindow?.packageName != snapshot.packageName ||
            supportedWindow.id != command.page.windowId
        ) {
            return ActionExecutionResult.TARGET_INVALID
        }
        if (!validateLivePageEvidence(snapshot, command)) {
            return ActionExecutionResult.TARGET_INVALID
        }
        return null
    }

    private fun AccessibilityWindowInfo.selectorCategory(): WindowCategory = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> WindowCategory.APPLICATION
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ->
            WindowCategory.ACCESSIBILITY_OVERLAY

        else -> WindowCategory.OTHER
    }

    private fun executeClick(
        command: AccessibilityActionCommand,
        decision: ExecutableActionDecision.Click,
    ): ActionExecutionResult {
        val target = decision.value.target
        if (
            target.expectedPageFingerprint != command.page.fingerprint ||
            target.expectedPackageName != command.expectedPackageName
        ) {
            return ActionExecutionResult.TARGET_INVALID
        }

        val snapshot = AccessibilitySnapshotStore.latest.value
            ?: return ActionExecutionResult.CANCELLED
        val capturedTarget = snapshot.findNode(target.node)
            ?: return ActionExecutionResult.TARGET_INVALID
        val liveTarget = findLiveNode(target.node)
            ?: return ActionExecutionResult.TARGET_INVALID
        if (!liveTarget.matchesCaptured(capturedTarget, target.expectedPackageName)) {
            return ActionExecutionResult.TARGET_INVALID
        }

        val clickable = when (target.resolution) {
            ClickResolution.EXACT_NODE -> liveTarget.takeIf {
                it.isSafeClickable(target.expectedPackageName)
            }

            ClickResolution.NEAREST_CLICKABLE_ANCESTOR -> {
                (0..minOf(target.maximumAncestorDepth, target.node.path.size))
                    .asSequence()
                    .mapNotNull { depth ->
                        findLiveNode(
                            target.node.copy(path = target.node.path.dropLast(depth)),
                        )
                    }
                    .firstOrNull { it.isSafeClickable(target.expectedPackageName) }
            }
        } ?: return ActionExecutionResult.TARGET_INVALID

        val bounds = Rect().also(clickable::getBoundsInScreen)
        if (
            ControlledActionPreflight.intersects(
                target = UiBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                regions = OverlayBoundsStore.snapshot(),
            )
        ) {
            return ActionExecutionResult.TARGET_INVALID
        }
        return if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            ActionExecutionResult.PERFORMED
        } else {
            ActionExecutionResult.FAILED
        }
    }

    private fun executeGlobalBack(
        command: AccessibilityActionCommand,
        decision: ExecutableActionDecision.GlobalBack,
    ): ActionExecutionResult {
        if (decision.value.expectedPageFingerprint != command.page.fingerprint) {
            return ActionExecutionResult.TARGET_INVALID
        }
        return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            ActionExecutionResult.PERFORMED
        } else {
            ActionExecutionResult.FAILED
        }
    }

    private fun validateLivePageEvidence(
        snapshot: UiSnapshot,
        command: AccessibilityActionCommand,
    ): Boolean = command.page.evidence.all { evidence ->
        val captured = snapshot.findNode(evidence.node) ?: return@all false
        val live = findLiveNode(evidence.node) ?: return@all false
        live.matchesCaptured(captured, snapshot.packageName)
    }

    private fun findLiveNode(reference: NodeReference): AccessibilityNodeInfo? {
        val root = service.windows.firstOrNull { it.id == reference.windowId }?.root
            ?: return null
        return reference.path.fold(root as AccessibilityNodeInfo?) { node, childIndex ->
            node?.getChild(childIndex)
        }
    }

    private fun UiSnapshot.findNode(reference: NodeReference): UiNodeSnapshot? {
        val root = windows.firstOrNull { it.id == reference.windowId }?.root ?: return null
        return reference.path.fold(root as UiNodeSnapshot?) { node, childIndex ->
            node?.childAtSourceIndex(childIndex)
        }
    }

    private fun AccessibilityNodeInfo.matchesCaptured(
        captured: UiNodeSnapshot,
        expectedPackageName: String,
    ): Boolean {
        if (
            packageName?.toString() != expectedPackageName ||
            !isVisibleToUser ||
            viewIdResourceName != captured.viewIdResourceName ||
            className?.toString() != captured.className ||
            normalize(text?.toString()) != normalize(captured.text) ||
            normalize(contentDescription?.toString()) !=
            normalize(captured.contentDescription)
        ) {
            return false
        }
        val bounds = Rect().also(::getBoundsInScreen)
        return bounds.right > bounds.left && bounds.bottom > bounds.top
    }

    private fun AccessibilityNodeInfo.isSafeClickable(expectedPackageName: String): Boolean {
        if (
            packageName?.toString() != expectedPackageName ||
            !isVisibleToUser ||
            !isEnabled ||
            !isClickable
        ) {
            return false
        }
        val bounds = Rect().also(::getBoundsInScreen)
        return bounds.right > bounds.left && bounds.bottom > bounds.top
    }

    private fun normalize(value: String?): String? =
        value?.trim()?.replace(Regex("\\s+"), " ")

    companion object {
        private const val MAX_COMMAND_AGE_MILLIS = 1_500L
    }
}

internal object ControlledExecutionBoundary {
    fun run(block: () -> ActionExecutionResult): ActionExecutionResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ActionExecutionResult.TARGET_INVALID
    }
}
