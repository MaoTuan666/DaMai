package com.ticketassistant.android.platform.api

import com.ticketassistant.android.accessibility.UiNodeSnapshot
import com.ticketassistant.android.accessibility.UiSnapshot
import com.ticketassistant.android.accessibility.UiWindowSnapshot
import com.ticketassistant.android.accessibility.childAtSourceIndex
import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.domain.TicketTask
import com.ticketassistant.android.runtime.EngineRuntimeState
import com.ticketassistant.android.runtime.SafetyPauseReason

enum class EvaluationIgnoreReason {
    RUN_ID_MISMATCH,
    STATE_NOT_ELIGIBLE,
    ADAPTER_NOT_REGISTERED,
    MODE_NOT_SUPPORTED,
    NON_TARGET_PACKAGE_WHILE_WAITING,
}

enum class AdapterContractViolation {
    ADAPTER_THREW_EXCEPTION,
    PAGE_WINDOW_NOT_TOPMOST,
    PAGE_EVIDENCE_MISSING,
    TARGET_FINGERPRINT_MISMATCH,
    TARGET_PACKAGE_MISMATCH,
    TARGET_WINDOW_MISMATCH,
    TARGET_NODE_MISSING,
    TARGET_NODE_NOT_ACTIONABLE,
    REDUNDANT_PHASE_SWITCH,
}

sealed interface PlatformEvaluation {
    data class Ignored(val reason: EvaluationIgnoreReason) : PlatformEvaluation

    data class AwaitingValidStart(val pageResult: PageResult) : PlatformEvaluation

    data class SafetyPause(
        val reason: SafetyPauseReason,
        val pageResult: PageResult?,
    ) : PlatformEvaluation

    data class ContractRejected(
        val violation: AdapterContractViolation,
        val safetyPauseReason: SafetyPauseReason?,
    ) : PlatformEvaluation

    data class Decision(
        val adapter: PlatformAdapter,
        val page: PageResult.Recognized,
        val action: ActionDecision,
    ) : PlatformEvaluation
}

/**
 * Runs an adapter as a pure, read-only decision step and validates its output.
 *
 * This class never executes accessibility actions. A caller may pass a validated Click or
 * GlobalBack proposal to the controlled runtime scheduler in a later step.
 */
class PlatformDecisionCoordinator(
    private val registry: PlatformAdapterRegistry,
) {
    fun evaluate(
        expectedRunId: String,
        snapshot: UiSnapshot,
        task: TicketTask,
        runtime: EngineRuntimeState,
    ): PlatformEvaluation {
        if (snapshot.runId != expectedRunId) {
            return PlatformEvaluation.Ignored(EvaluationIgnoreReason.RUN_ID_MISMATCH)
        }
        if (
            runtime.taskState != TaskState.WAIT_TARGET_APP &&
            runtime.taskState != TaskState.RUNNING
        ) {
            return PlatformEvaluation.Ignored(EvaluationIgnoreReason.STATE_NOT_ELIGIBLE)
        }

        val adapter = when (
            val resolution = registry.resolve(
                platform = task.platform,
                packageName = snapshot.packageName,
                mode = task.runMode,
            )
        ) {
            is AdapterResolution.Found -> resolution.adapter
            is AdapterResolution.AdapterNotRegistered -> {
                return PlatformEvaluation.Ignored(EvaluationIgnoreReason.ADAPTER_NOT_REGISTERED)
            }

            is AdapterResolution.ModeNotSupported -> {
                return PlatformEvaluation.Ignored(EvaluationIgnoreReason.MODE_NOT_SUPPORTED)
            }

            is AdapterResolution.PackageNotSupported -> {
                return if (runtime.taskState == TaskState.RUNNING) {
                    PlatformEvaluation.SafetyPause(
                        reason = SafetyPauseReason.TARGET_APP_LEFT,
                        pageResult = null,
                    )
                } else {
                    PlatformEvaluation.Ignored(
                        EvaluationIgnoreReason.NON_TARGET_PACKAGE_WHILE_WAITING,
                    )
                }
            }
        }

        val pageResult = try {
            adapter.detectPage(snapshot, task)
        } catch (_: Exception) {
            return contractRejected(
                AdapterContractViolation.ADAPTER_THREW_EXCEPTION,
                runtime,
            )
        }

        return when (pageResult) {
            is PageResult.Unknown -> handleUnrecognized(pageResult, runtime)
            is PageResult.Mismatch -> handleMismatch(pageResult, runtime)
            is PageResult.Recognized -> handleRecognized(
                adapter = adapter,
                page = pageResult,
                snapshot = snapshot,
                task = task,
                runtime = runtime,
            )
        }
    }

    private fun handleUnrecognized(
        page: PageResult.Unknown,
        runtime: EngineRuntimeState,
    ): PlatformEvaluation {
        if (runtime.taskState == TaskState.WAIT_TARGET_APP) {
            return PlatformEvaluation.AwaitingValidStart(page)
        }
        val safetyReason = when (page.reason) {
            PageUnknownReason.VERIFICATION_REQUIRED,
            PageUnknownReason.RISK_CONTROL_REQUIRED,
            PageUnknownReason.DEVICE_CHECK_REQUIRED,
            PageUnknownReason.LOGIN_REQUIRED,
            -> SafetyPauseReason.VERIFICATION_REQUIRED

            PageUnknownReason.INSUFFICIENT_EVIDENCE,
            PageUnknownReason.UNKNOWN_TOP_WINDOW,
            PageUnknownReason.PERMISSION_REQUIRED,
            -> SafetyPauseReason.UNKNOWN_PAGE
        }
        return PlatformEvaluation.SafetyPause(safetyReason, page)
    }

    private fun handleMismatch(
        page: PageResult.Mismatch,
        runtime: EngineRuntimeState,
    ): PlatformEvaluation =
        if (runtime.taskState == TaskState.WAIT_TARGET_APP) {
            PlatformEvaluation.AwaitingValidStart(page)
        } else {
            PlatformEvaluation.SafetyPause(SafetyPauseReason.TARGET_MISMATCH, page)
        }

    private fun handleRecognized(
        adapter: PlatformAdapter,
        page: PageResult.Recognized,
        snapshot: UiSnapshot,
        task: TicketTask,
        runtime: EngineRuntimeState,
    ): PlatformEvaluation {
        validateRecognizedPage(snapshot, page)?.let {
            return contractRejected(it, runtime)
        }
        if (
            runtime.taskState == TaskState.WAIT_TARGET_APP &&
            !page.isValidStartFor(runtime.phase)
        ) {
            return PlatformEvaluation.AwaitingValidStart(page)
        }

        val action = try {
            adapter.decideAction(page, task, runtime)
        } catch (_: Exception) {
            return contractRejected(
                AdapterContractViolation.ADAPTER_THREW_EXCEPTION,
                runtime,
            )
        }
        validateAction(snapshot, page, action, runtime)?.let {
            return contractRejected(it, runtime)
        }
        return PlatformEvaluation.Decision(adapter, page, action)
    }

    private fun validateRecognizedPage(
        snapshot: UiSnapshot,
        page: PageResult.Recognized,
    ): AdapterContractViolation? {
        val topPlatformWindow = snapshot.windows
            .filter { it.root?.packageName == snapshot.packageName }
            .maxByOrNull(UiWindowSnapshot::layer)
        if (topPlatformWindow?.id != page.windowId) {
            return AdapterContractViolation.PAGE_WINDOW_NOT_TOPMOST
        }
        if (
            page.evidence.any { evidence ->
                val node = snapshot.findNode(evidence.node)
                node == null ||
                    node.packageName != snapshot.packageName ||
                    !node.isVisibleToUser
            }
        ) {
            return AdapterContractViolation.PAGE_EVIDENCE_MISSING
        }
        return null
    }

    private fun validateAction(
        snapshot: UiSnapshot,
        page: PageResult.Recognized,
        action: ActionDecision,
        runtime: EngineRuntimeState,
    ): AdapterContractViolation? = when (action) {
        is ActionDecision.Click -> validateClick(snapshot, page, action.target)
        is ActionDecision.GlobalBack -> {
            if (action.expectedPageFingerprint != page.fingerprint) {
                AdapterContractViolation.TARGET_FINGERPRINT_MISMATCH
            } else {
                null
            }
        }

        is ActionDecision.SwitchPhase -> {
            if (action.phase == runtime.phase) {
                AdapterContractViolation.REDUNDANT_PHASE_SWITCH
            } else {
                null
            }
        }

        is ActionDecision.Stop,
        is ActionDecision.Wait,
        -> null
    }

    private fun validateClick(
        snapshot: UiSnapshot,
        page: PageResult.Recognized,
        target: ClickTarget,
    ): AdapterContractViolation? {
        if (target.expectedPageFingerprint != page.fingerprint) {
            return AdapterContractViolation.TARGET_FINGERPRINT_MISMATCH
        }
        if (target.expectedPackageName != snapshot.packageName) {
            return AdapterContractViolation.TARGET_PACKAGE_MISMATCH
        }
        if (target.node.windowId != page.windowId) {
            return AdapterContractViolation.TARGET_WINDOW_MISMATCH
        }
        val node = snapshot.findNode(target.node)
            ?: return AdapterContractViolation.TARGET_NODE_MISSING
        val actionable = when (target.resolution) {
            ClickResolution.EXACT_NODE -> node.isActionableFor(snapshot.packageName)
            ClickResolution.NEAREST_CLICKABLE_ANCESTOR -> snapshot
                .nodeAndAncestors(target.node, target.maximumAncestorDepth)
                .any { it.isActionableFor(snapshot.packageName) }
        }
        return if (actionable) {
            null
        } else {
            AdapterContractViolation.TARGET_NODE_NOT_ACTIONABLE
        }
    }

    private fun contractRejected(
        violation: AdapterContractViolation,
        runtime: EngineRuntimeState,
    ): PlatformEvaluation.ContractRejected = PlatformEvaluation.ContractRejected(
        violation = violation,
        safetyPauseReason = if (runtime.taskState == TaskState.RUNNING) {
            SafetyPauseReason.ACTION_TARGET_INVALID
        } else {
            null
        },
    )

    private fun UiSnapshot.findNode(reference: NodeReference): UiNodeSnapshot? {
        val root = windows.firstOrNull { it.id == reference.windowId }?.root ?: return null
        return reference.path.fold(root as UiNodeSnapshot?) { node, childIndex ->
            node?.childAtSourceIndex(childIndex)
        }
    }

    private fun UiSnapshot.nodeAndAncestors(
        reference: NodeReference,
        maximumAncestorDepth: Int,
    ): Sequence<UiNodeSnapshot> = sequence {
        for (depth in 0..minOf(maximumAncestorDepth, reference.path.size)) {
            val candidate = findNode(reference.copy(path = reference.path.dropLast(depth)))
            if (candidate != null) yield(candidate)
        }
    }

    private fun UiNodeSnapshot.isActionableFor(expectedPackageName: String): Boolean =
        packageName == expectedPackageName &&
            isVisibleToUser &&
            isEnabled &&
            isClickable &&
            boundsInScreen.right > boundsInScreen.left &&
            boundsInScreen.bottom > boundsInScreen.top
}
