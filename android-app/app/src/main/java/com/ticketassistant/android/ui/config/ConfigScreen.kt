package com.ticketassistant.android.ui.config

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.ticketassistant.android.domain.ConfigField
import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TicketTaskDraft
import com.ticketassistant.android.domain.TicketTaskValidator
import com.ticketassistant.android.domain.ValidationResult
import com.ticketassistant.android.runtime.NotificationPermissionState
import com.ticketassistant.android.ui.theme.TicketAssistantTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onDraftChange: ((TicketTaskDraft) -> TicketTaskDraft) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onManageNotificationPermission: () -> Unit,
    onStart: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "购票辅助",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "安全配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            StartActionBar(
                state = state,
                onStart = onStart,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard()

            SectionCard(
                step = "01",
                title = "选择运行模式",
                description = "根据开售阶段选择本次任务的执行范围。",
            ) {
                RunModeField(
                    selected = state.draft.runMode,
                    onSelected = { mode ->
                        onDraftChange { it.copy(runMode = mode) }
                    },
                )
            }

            SectionCard(
                step = "02",
                title = "填写目标信息",
                description = "请与大麦项目页中的场次和票档名称保持一致。",
            ) {
                ConfigTextField(
                    label = "项目识别关键词",
                    placeholder = "例如：歌手名 + 城市",
                    value = state.draft.eventKeyword,
                    error = state.error(ConfigField.EVENT_KEYWORD),
                    onValueChange = { value ->
                        onDraftChange { it.copy(eventKeyword = value) }
                    },
                )
                ConfigTextField(
                    label = "目标场次",
                    placeholder = "例如：08 月 08 日 19:30",
                    value = state.draft.targetSession,
                    error = state.error(ConfigField.TARGET_SESSION),
                    onValueChange = { value ->
                        onDraftChange { it.copy(targetSession = value) }
                    },
                )
                ConfigTextField(
                    label = "目标票档名称",
                    placeholder = "例如：看台 580 元",
                    value = state.draft.targetTier,
                    error = state.error(ConfigField.TARGET_TIER),
                    onValueChange = { value ->
                        onDraftChange { it.copy(targetTier = value) }
                    },
                )
                ConfigTextField(
                    label = "目标单价（元）",
                    placeholder = "580",
                    value = state.draft.targetPriceYuan,
                    error = state.error(ConfigField.TARGET_PRICE),
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = { value ->
                        onDraftChange { it.copy(targetPriceYuan = value) }
                    },
                )
                ConfigTextField(
                    label = "购票数量",
                    placeholder = "1",
                    value = state.draft.ticketCount,
                    error = state.error(ConfigField.TICKET_COUNT),
                    keyboardType = KeyboardType.Number,
                    onValueChange = { value ->
                        onDraftChange { it.copy(ticketCount = value) }
                    },
                )

                AttendeeConfirmation(
                    checked = state.draft.attendeesConfigured,
                    error = state.error(ConfigField.ATTENDEES_CONFIGURED),
                    onCheckedChange = { checked ->
                        onDraftChange { it.copy(attendeesConfigured = checked) }
                    },
                )
            }

            SectionCard(
                step = "03",
                title = "设置安全上限",
                description = "到达任一上限后任务会停止，避免长时间或重复提交。",
            ) {
                SafetyLimitFields(
                    state = state,
                    onDraftChange = onDraftChange,
                )
            }

            SectionCard(
                step = "04",
                title = "完成运行授权",
                description = "两项授权都只用于任务识别、状态展示和停止控制。",
            ) {
                PermissionStatus(
                    accessibilityEnabled = state.accessibilityEnabled,
                    notificationState = state.notificationPermissionState,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onManageNotificationPermission = onManageNotificationPermission,
                )
            }

            if (state.runActive) {
                NoticeBanner(
                    title = "任务正在运行",
                    message = "可使用左上角悬浮控件或系统通知停止任务。",
                    isError = false,
                )
            }

            state.message?.let { message ->
                NoticeBanner(
                    title = if (state.messageIsError) "操作未完成" else "状态更新",
                    message = message,
                    isError = state.messageIsError,
                )
            }

            Text(
                text = "付款始终由你手动完成。应用不会保存账号、实名信息或支付资料。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HeroCard() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Text(
                            text = "票",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "创建新的购票任务",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "当前支持大麦 Android",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                Text(
                    text = "应用执行至支付前，付款与最终确认由你本人完成。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    step: String,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Text(
                            text = step,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun RunModeField(
    selected: RunMode,
    onSelected: (RunMode) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.selectableGroup(),
    ) {
        RunMode.entries.forEach { mode ->
            val isSelected = selected == mode
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(mode) },
                        role = Role.RadioButton,
                    )
                    .semantics {
                        stateDescription = if (isSelected) "已选择" else "未选择"
                    },
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = mode.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    placeholder: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { ErrorText(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun AttendeeConfirmation(
    checked: Boolean,
    error: String?,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Checkbox,
                ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                )
                Text(
                    text = "我已在大麦中配置场次、票档、数量和实名观演人",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        error?.let { ErrorText(it) }
    }
}

@Composable
private fun SafetyLimitFields(
    state: ConfigUiState,
    onDraftChange: ((TicketTaskDraft) -> TicketTaskDraft) -> Unit,
) {
    BoxWithConstraints {
        val compact = maxWidth < 360.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MaxAttemptsField(state, onDraftChange)
                MaxRuntimeField(state, onDraftChange)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MaxAttemptsField(
                    state = state,
                    onDraftChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                )
                MaxRuntimeField(
                    state = state,
                    onDraftChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MaxAttemptsField(
    state: ConfigUiState,
    onDraftChange: ((TicketTaskDraft) -> TicketTaskDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfigTextField(
        label = "最大提交次数",
        placeholder = "20",
        value = state.draft.maxSubmitAttempts,
        error = state.error(ConfigField.MAX_SUBMIT_ATTEMPTS),
        keyboardType = KeyboardType.Number,
        modifier = modifier,
        onValueChange = { value ->
            onDraftChange { it.copy(maxSubmitAttempts = value) }
        },
    )
}

@Composable
private fun MaxRuntimeField(
    state: ConfigUiState,
    onDraftChange: ((TicketTaskDraft) -> TicketTaskDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfigTextField(
        label = "最长运行（分钟）",
        placeholder = "30",
        value = state.draft.maxRuntimeMinutes,
        error = state.error(ConfigField.MAX_RUNTIME),
        keyboardType = KeyboardType.Number,
        modifier = modifier,
        onValueChange = { value ->
            onDraftChange { it.copy(maxRuntimeMinutes = value) }
        },
    )
}

@Composable
private fun PermissionStatus(
    accessibilityEnabled: Boolean,
    notificationState: NotificationPermissionState,
    onOpenAccessibilitySettings: () -> Unit,
    onManageNotificationPermission: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionItem(
            title = "无障碍服务",
            description = "用于识别页面节点，并提供左上角任务控制窗。",
            granted = accessibilityEnabled,
            actionLabel = "前往系统设置开启",
            onAction = onOpenAccessibilitySettings,
        )
        PermissionItem(
            title = "运行通知",
            description = "用于持续显示运行状态，并提供随时停止入口。",
            granted = notificationState == NotificationPermissionState.GRANTED,
            actionLabel = if (
                notificationState == NotificationPermissionState.SETTINGS_REQUIRED
            ) {
                "打开通知设置"
            } else {
                "允许运行通知"
            },
            onAction = onManageNotificationPermission,
        )
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (granted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (granted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        ),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "$title · ${if (granted) "已开启" else "待开启"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (granted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (granted) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        },
                    )
                }
            }
            if (!granted) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun NoticeBanner(
    title: String,
    message: String,
    isError: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StartActionBar(
    state: ConfigUiState,
    onStart: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                enabled = state.canStart,
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onTertiary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text(if (state.isSaving) "正在保存…" else "保存配置并开始")
            }
            Text(
                text = state.startHint(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun RunMode.description(): String = when (this) {
    RunMode.SALE_ONLY -> "仅执行开售阶段的目标票档提交"
    RunMode.RETURN_ONLY -> "直接等待并尝试回流票"
    RunMode.SALE_THEN_RETURN -> "开售未成功时继续等待回流票"
}

private fun ConfigUiState.startHint(): String = when {
    isSaving -> "正在保存任务，请稍候"
    pendingRunStart != null -> "正在启动运行服务"
    runActive -> "已有任务正在运行，请先从系统通知停止"
    !accessibilityEnabled -> "请先开启无障碍服务"
    notificationPermissionState != NotificationPermissionState.GRANTED ->
        "请先允许运行通知"
    else -> when (val result = TicketTaskValidator.validate(draft)) {
        is ValidationResult.Invalid ->
            result.issues.firstOrNull()?.message ?: "请检查任务配置"
        is ValidationResult.Valid ->
            "配置已就绪；开始后请手动打开大麦"
    }
}

private fun ConfigUiState.error(field: ConfigField): String? = fieldErrors[field]

@Preview(
    name = "默认配置",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun ConfigScreenPreview() {
    TicketAssistantTheme(darkTheme = false) {
        ConfigScreen(
            state = ConfigUiState(),
            onDraftChange = {},
            onOpenAccessibilitySettings = {},
            onManageNotificationPermission = {},
            onStart = {},
        )
    }
}

@Preview(
    name = "暗色就绪",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ReadyConfigScreenPreview() {
    TicketAssistantTheme(darkTheme = true) {
        ConfigScreen(
            state = ConfigUiState(
                draft = TicketTaskDraft(
                    runMode = RunMode.SALE_THEN_RETURN,
                    eventKeyword = "示例项目 上海",
                    targetSession = "08 月 08 日 19:30",
                    targetTier = "看台 580 元",
                    targetPriceYuan = "580",
                    ticketCount = "2",
                    attendeesConfigured = true,
                    maxSubmitAttempts = "20",
                    maxRuntimeMinutes = "30",
                ),
                accessibilityEnabled = true,
                notificationPermissionState = NotificationPermissionState.GRANTED,
            ),
            onDraftChange = {},
            onOpenAccessibilitySettings = {},
            onManageNotificationPermission = {},
            onStart = {},
        )
    }
}
