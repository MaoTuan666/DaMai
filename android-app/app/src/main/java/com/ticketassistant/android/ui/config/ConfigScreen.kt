package com.ticketassistant.android.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ticketassistant.android.domain.ConfigField
import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TicketTaskDraft
import com.ticketassistant.android.runtime.NotificationPermissionState

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
        topBar = {
            TopAppBar(title = { Text("购票任务配置") })
        },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "当前支持：大麦 Android",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "付款始终由你手动完成。应用不会保存账号、实名信息或支付资料。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RunModeField(
                selected = state.draft.runMode,
                onSelected = { mode ->
                    onDraftChange { it.copy(runMode = mode) }
                },
            )

            ConfigTextField(
                label = "项目识别关键词",
                value = state.draft.eventKeyword,
                error = state.error(ConfigField.EVENT_KEYWORD),
                onValueChange = { value ->
                    onDraftChange { it.copy(eventKeyword = value) }
                },
            )
            ConfigTextField(
                label = "目标场次",
                value = state.draft.targetSession,
                error = state.error(ConfigField.TARGET_SESSION),
                onValueChange = { value ->
                    onDraftChange { it.copy(targetSession = value) }
                },
            )
            ConfigTextField(
                label = "目标票档名称",
                value = state.draft.targetTier,
                error = state.error(ConfigField.TARGET_TIER),
                onValueChange = { value ->
                    onDraftChange { it.copy(targetTier = value) }
                },
            )
            ConfigTextField(
                label = "目标单价（元）",
                value = state.draft.targetPriceYuan,
                error = state.error(ConfigField.TARGET_PRICE),
                keyboardType = KeyboardType.Decimal,
                onValueChange = { value ->
                    onDraftChange { it.copy(targetPriceYuan = value) }
                },
            )
            ConfigTextField(
                label = "购票数量",
                value = state.draft.ticketCount,
                error = state.error(ConfigField.TICKET_COUNT),
                keyboardType = KeyboardType.Number,
                onValueChange = { value ->
                    onDraftChange { it.copy(ticketCount = value) }
                },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = state.draft.attendeesConfigured,
                    onCheckedChange = { checked ->
                        onDraftChange { it.copy(attendeesConfigured = checked) }
                    },
                )
                Text(
                    text = "我已在大麦中配置目标场次、票档、数量和实名观演人",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.error(ConfigField.ATTENDEES_CONFIGURED)?.let {
                ErrorText(it)
            }

            Text(
                text = "安全上限",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ConfigTextField(
                    label = "最大提交次数",
                    value = state.draft.maxSubmitAttempts,
                    error = state.error(ConfigField.MAX_SUBMIT_ATTEMPTS),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    onValueChange = { value ->
                        onDraftChange { it.copy(maxSubmitAttempts = value) }
                    },
                )
                ConfigTextField(
                    label = "最长运行（分钟）",
                    value = state.draft.maxRuntimeMinutes,
                    error = state.error(ConfigField.MAX_RUNTIME),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    onValueChange = { value ->
                        onDraftChange { it.copy(maxRuntimeMinutes = value) }
                    },
                )
            }

            AccessibilityStatus(
                enabled = state.accessibilityEnabled,
                onOpenSettings = onOpenAccessibilitySettings,
            )
            NotificationStatus(
                state = state.notificationPermissionState,
                onManagePermission = onManageNotificationPermission,
            )
            if (state.runActive) {
                Text(
                    text = "已有任务正在运行。可使用左上角悬浮控件或系统通知停止任务。",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    color = if (state.messageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                enabled = state.canStart,
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "正在保存…" else "保存并开始")
            }
            Text(
                text = "开始后请自行打开大麦；本应用不会自动启动售票软件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunModeField(
    selected: RunMode,
    onSelected: (RunMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("运行模式") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            RunMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigTextField(
    label: String,
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
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { ErrorText(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccessibilityStatus(
    enabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (enabled) "无障碍服务：已开启" else "无障碍服务：未开启",
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "页面识别和左上角控制窗均由此服务提供，无需另开悬浮窗权限。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!enabled) {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("前往系统设置开启")
            }
        }
    }
}

@Composable
private fun NotificationStatus(
    state: NotificationPermissionState,
    onManagePermission: () -> Unit,
) {
    val granted = state == NotificationPermissionState.GRANTED
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (granted) "运行通知：已允许" else "运行通知：未允许",
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.titleSmall,
        )
        if (!granted) {
            OutlinedButton(
                onClick = onManagePermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state == NotificationPermissionState.SETTINGS_REQUIRED) {
                        "打开通知设置"
                    } else {
                        "允许运行通知"
                    },
                )
            }
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

private fun ConfigUiState.error(field: ConfigField): String? = fieldErrors[field]
