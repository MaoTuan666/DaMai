package com.ticketassistant.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketassistant.android.accessibility.AccessibilityPermission
import com.ticketassistant.android.runtime.NotificationPermission
import com.ticketassistant.android.runtime.NotificationPermissionState
import com.ticketassistant.android.runtime.TaskForegroundService
import com.ticketassistant.android.ui.config.ConfigScreen
import com.ticketassistant.android.ui.config.ConfigViewModel
import com.ticketassistant.android.ui.config.ConfigViewModelFactory
import com.ticketassistant.android.ui.theme.TicketAssistantTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ConfigViewModel by viewModels {
        val application = application as TicketAssistantApplication
        ConfigViewModelFactory(application.taskRepository)
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.setNotificationPermissionState(
            NotificationPermission.state(this),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle().value

            TicketAssistantTheme {
                ConfigScreen(
                    state = state,
                    onDraftChange = viewModel::updateDraft,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onManageNotificationPermission = ::manageNotificationPermission,
                    onStart = viewModel::start,
                )
            }

            LaunchedEffect(state.pendingRunStart) {
                val pendingRun = state.pendingRunStart ?: return@LaunchedEffect
                runCatching {
                    TaskForegroundService.start(
                        context = this@MainActivity,
                        runId = pendingRun.runId,
                        taskId = pendingRun.taskId,
                    )
                }.onSuccess {
                    viewModel.consumePendingRunStart()
                    moveTaskToBack(true)
                }.onFailure {
                    viewModel.onForegroundServiceStartFailed(pendingRun)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setAccessibilityEnabled(
            AccessibilityPermission.isServiceEnabled(this),
        )
        viewModel.setNotificationPermissionState(
            NotificationPermission.state(this),
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun manageNotificationPermission() {
        when (NotificationPermission.state(this)) {
            NotificationPermissionState.GRANTED -> {
                viewModel.setNotificationPermissionState(NotificationPermissionState.GRANTED)
            }

            NotificationPermissionState.RUNTIME_PERMISSION_REQUIRED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            NotificationPermissionState.SETTINGS_REQUIRED -> {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    },
                )
            }
        }
    }
}
