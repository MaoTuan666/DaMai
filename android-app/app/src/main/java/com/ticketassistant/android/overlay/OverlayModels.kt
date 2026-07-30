package com.ticketassistant.android.overlay

import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.runtime.AutomationPhase
import com.ticketassistant.android.runtime.EngineRuntimeState
import com.ticketassistant.android.runtime.RuntimeStopReason
import com.ticketassistant.android.runtime.SafetyPauseReason

enum class OverlayPrimaryAction {
    START,
    PAUSE,
    RESUME,
}

data class OverlayControlPresentation(
    val statusText: String,
    val phaseText: String,
    val primaryButtonText: String,
    val primaryButtonEnabled: Boolean,
    val primaryAction: OverlayPrimaryAction?,
)

data class OverlayLogEntry(
    val runId: String,
    val occurredAtEpochMillis: Long,
    val phase: AutomationPhase,
    val eventCode: String,
    val message: String,
    val attemptNumber: Int? = null,
)

object OverlayPresentation {
    fun controls(state: EngineRuntimeState?): OverlayControlPresentation {
        val phaseText = state?.phase?.displayName() ?: "准备"
        return when (state?.taskState) {
            TaskState.ARMED -> OverlayControlPresentation(
                statusText = "请打开大麦后点击开始",
                phaseText = "准备",
                primaryButtonText = "开始",
                primaryButtonEnabled = true,
                primaryAction = OverlayPrimaryAction.START,
            )

            TaskState.RUNNING -> OverlayControlPresentation(
                statusText = "运行中",
                phaseText = phaseText,
                primaryButtonText = "暂停",
                primaryButtonEnabled = true,
                primaryAction = OverlayPrimaryAction.PAUSE,
            )

            TaskState.USER_PAUSED -> OverlayControlPresentation(
                statusText = "已暂停",
                phaseText = phaseText,
                primaryButtonText = "开始",
                primaryButtonEnabled = true,
                primaryAction = OverlayPrimaryAction.RESUME,
            )

            TaskState.SAFETY_PAUSED -> OverlayControlPresentation(
                statusText = "安全暂停",
                phaseText = phaseText,
                primaryButtonText = "已暂停",
                primaryButtonEnabled = false,
                primaryAction = null,
            )

            TaskState.ORDER_LOCKED -> OverlayControlPresentation(
                statusText = "请接管",
                phaseText = phaseText,
                primaryButtonText = "已停止",
                primaryButtonEnabled = false,
                primaryAction = null,
            )

            TaskState.WAIT_TARGET_APP -> OverlayControlPresentation(
                statusText = "正在识别当前页面",
                phaseText = phaseText,
                primaryButtonText = "识别中",
                primaryButtonEnabled = false,
                primaryAction = null,
            )

            TaskState.STOPPED -> OverlayControlPresentation(
                statusText = "已停止",
                phaseText = phaseText,
                primaryButtonText = "已停止",
                primaryButtonEnabled = false,
                primaryAction = null,
            )

            TaskState.CONFIG, null -> OverlayControlPresentation(
                statusText = "准备中",
                phaseText = phaseText,
                primaryButtonText = "准备",
                primaryButtonEnabled = false,
                primaryAction = null,
            )
        }
    }
}

object OverlayEventFormatter {
    fun message(
        eventCode: String,
        state: EngineRuntimeState,
    ): String = when (eventCode) {
        "ENGINE_ARMED" -> "配置已就绪，请打开大麦后点击开始"
        "TASK_USER_STARTED" -> "已开始，正在识别当前页面"
        "TARGET_PAGE_RECOGNIZED" -> "已识别目标起始页面"
        "TASK_USER_PAUSED" -> "用户已暂停，待执行动作已取消"
        "TASK_USER_RESUMED" -> "已恢复，等待新的页面快照"
        "TASK_SAFETY_PAUSED" -> state.safetyPauseReason.displayMessage()
        "TASK_PHASE_SWITCHED" -> "已切换至${state.phase.displayName()}阶段"
        "ORDER_LOCKED" -> "已到达支付前页面，请手动接管"
        "TASK_STOPPED" -> state.stopReason.displayMessage()
        "ACTION_FAILED" -> "操作未成功，等待受控重试"
        else -> actionMessage(eventCode) ?: "运行状态已更新"
    }

    private fun actionMessage(eventCode: String): String? = when (eventCode) {
        "DM_WAIT_RESERVED" -> "等待“立即预订”可用"
        "DM_CLICK_BOOK_NOW" -> "执行“立即预订”"
        "DM_CLICK_SEAT_ENTRY" -> "执行“去选座”"
        "DM_CLICK_SUBMIT" -> "执行“立即提交”"
        "DM_POPUP_CONTINUE" -> "执行“继续尝试”"
        "DM_POPUP_RETURN" -> "执行“返回重新选购”"
        "DM_SWITCH_TO_RETURN" -> "切换到回流监控"
        "DM_RETURN_BACK_TO_DETAIL" -> "返回项目详情页"
        "DM_RETURN_REENTER" -> "重新进入票档页"
        "DM_SELECT_TARGET_TIER" -> "选择固定目标票档"
        "DM_TARGET_TIER_AVAILABLE" -> "固定目标票档已恢复"
        "DM_CONFIRM_TARGET_TIER" -> "确认固定目标票档"
        "DM_ORDER_LOCKED" -> "已到达支付前页面，请手动接管"
        "DM_SAFE_PAUSE" -> "页面需要人工确认，已安全暂停"
        else -> null
    }

    private fun AutomationPhase.displayName(): String = when (this) {
        AutomationPhase.SALE -> "开售"
        AutomationPhase.RETURN_MONITOR -> "回流"
    }

    private fun SafetyPauseReason?.displayMessage(): String = when (this) {
        SafetyPauseReason.UNKNOWN_PAGE -> "页面无法识别，已安全暂停"
        SafetyPauseReason.TARGET_MISMATCH -> "购票目标不一致，已安全暂停"
        SafetyPauseReason.VERIFICATION_REQUIRED -> "出现验证页面，请手动处理"
        SafetyPauseReason.TARGET_APP_LEFT -> "已离开目标应用，自动操作暂停"
        SafetyPauseReason.UNKNOWN_POPUP -> "出现未知弹窗，已安全暂停"
        SafetyPauseReason.ACTION_TARGET_INVALID -> "操作目标已失效，已安全暂停"
        SafetyPauseReason.ACTION_RETRY_LIMIT -> "操作重试达到上限，已安全暂停"
        null -> "触发安全规则，已暂停"
    }

    private fun RuntimeStopReason?.displayMessage(): String = when (this) {
        RuntimeStopReason.USER_REQUESTED -> "用户已停止任务"
        RuntimeStopReason.ACTION_FAILURE_LIMIT_REACHED -> "连续操作失败达到上限"
        RuntimeStopReason.ORDER_LOCKED -> "已到达支付前页面，请手动接管"
        RuntimeStopReason.PLATFORM_REQUESTED -> "平台流程已结束"
        null -> "任务已停止"
    }
}

private fun AutomationPhase.displayName(): String = when (this) {
    AutomationPhase.SALE -> "开售"
    AutomationPhase.RETURN_MONITOR -> "回流"
}
