package com.ticketassistant.android.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ticketassistant.android.domain.TicketTaskDraft
import com.ticketassistant.android.domain.TicketTaskRepository
import com.ticketassistant.android.domain.TicketTaskValidator
import com.ticketassistant.android.domain.ValidationResult
import com.ticketassistant.android.runtime.ActiveRunStore
import com.ticketassistant.android.runtime.NotificationPermissionState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConfigViewModel(
    private val repository: TicketTaskRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            ActiveRunStore.session.collect { session ->
                mutableUiState.update { it.copy(runActive = session != null) }
            }
        }
    }

    fun updateDraft(transform: (TicketTaskDraft) -> TicketTaskDraft) {
        mutableUiState.update {
            it.copy(
                draft = transform(it.draft),
                fieldErrors = emptyMap(),
                message = null,
                messageIsError = false,
            )
        }
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        mutableUiState.update { it.copy(accessibilityEnabled = enabled) }
    }

    fun setNotificationPermissionState(state: NotificationPermissionState) {
        mutableUiState.update { it.copy(notificationPermissionState = state) }
    }

    fun start() {
        val state = mutableUiState.value
        if (state.isSaving) return
        if (state.pendingRunStart != null) return
        if (state.runActive) {
            mutableUiState.update {
                it.copy(
                    message = "已有任务正在运行，请先从系统通知停止",
                    messageIsError = true,
                )
            }
            return
        }
        if (!state.accessibilityEnabled) {
            mutableUiState.update {
                it.copy(
                    message = "请先开启购票辅助无障碍服务",
                    messageIsError = true,
                )
            }
            return
        }
        if (state.notificationPermissionState != NotificationPermissionState.GRANTED) {
            mutableUiState.update {
                it.copy(
                    message = "请允许通知，以便持续显示运行状态和停止入口",
                    messageIsError = true,
                )
            }
            return
        }

        when (val validation = TicketTaskValidator.validate(state.draft)) {
            is ValidationResult.Invalid -> {
                mutableUiState.update {
                    it.copy(
                        fieldErrors = validation.issues.associate {
                            issue -> issue.field to issue.message
                        },
                        message = "请检查配置后再开始",
                        messageIsError = true,
                    )
                }
            }

            is ValidationResult.Valid -> {
                mutableUiState.update {
                    it.copy(isSaving = true, fieldErrors = emptyMap(), message = null)
                }
                viewModelScope.launch {
                    val runId = UUID.randomUUID().toString()
                    runCatching {
                        repository.createArmedTask(validation.task, runId)
                    }.onSuccess { taskId ->
                        mutableUiState.update {
                            it.copy(
                                isSaving = false,
                                message = "任务已就绪，请手动打开大麦",
                                messageIsError = false,
                                pendingRunStart = PendingRunStart(
                                    runId = runId,
                                    taskId = taskId,
                                ),
                            )
                        }
                    }.onFailure {
                        mutableUiState.update {
                            it.copy(
                                isSaving = false,
                                message = "任务保存失败，请重试",
                                messageIsError = true,
                            )
                        }
                    }
                }
            }
        }
    }

    fun consumePendingRunStart() {
        mutableUiState.update { it.copy(pendingRunStart = null) }
    }

    fun onForegroundServiceStartFailed(pendingRun: PendingRunStart) {
        ActiveRunStore.disarm(pendingRun.runId)
        mutableUiState.update {
            it.copy(
                pendingRunStart = null,
                message = "前台运行服务启动失败，任务已停止",
                messageIsError = true,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.stopTask(
                    taskId = pendingRun.taskId,
                    runId = pendingRun.runId,
                    reason = "前台运行服务启动失败",
                )
            }
        }
    }
}

class ConfigViewModelFactory(
    private val repository: TicketTaskRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConfigViewModel::class.java))
        return ConfigViewModel(repository) as T
    }
}
