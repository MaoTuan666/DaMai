package com.ticketassistant.android.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.ticketassistant.android.runtime.ActiveRunSession
import com.ticketassistant.android.runtime.ActiveRunStore
import com.ticketassistant.android.runtime.TaskForegroundService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AccessibilityOverlayController(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val logView = createLogView()
    private val pauseResumeButton = createControlButton()
    private val stopButton = createControlButton().apply {
        text = "停止"
        contentDescription = "停止购票辅助任务"
    }
    private val controlView = createControlView()
    private val logLayoutParams = createLogLayoutParams()
    private val controlLayoutParams = createControlLayoutParams()

    private var observationJob: Job? = null
    private var attached = false
    private var currentSession: ActiveRunSession? = null
    private var currentPrimaryAction: OverlayPrimaryAction? = null

    init {
        pauseResumeButton.setOnClickListener {
            val session = currentSession ?: return@setOnClickListener
            when (currentPrimaryAction) {
                OverlayPrimaryAction.START -> runCatching {
                    TaskForegroundService.begin(
                        service,
                        session.runId,
                        session.taskId,
                    )
                }

                OverlayPrimaryAction.PAUSE -> runCatching {
                    TaskForegroundService.pause(
                        service,
                        session.runId,
                        session.taskId,
                    )
                }

                OverlayPrimaryAction.RESUME -> runCatching {
                    TaskForegroundService.resume(
                        service,
                        session.runId,
                        session.taskId,
                    )
                }

                null -> Unit
            }
        }
        stopButton.setOnClickListener {
            val session = currentSession ?: return@setOnClickListener
            runCatching {
                TaskForegroundService.stop(service, session.runId, session.taskId)
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (observationJob != null) return
        observationJob = scope.launch {
            combine(ActiveRunStore.session, RuntimeOverlayStore.logs) { session, logs ->
                session to logs
            }.collect { (session, logs) ->
                if (session == null) {
                    currentSession = null
                    detach()
                } else {
                    currentSession = session
                    attachIfNeeded()
                    render(session, logs)
                }
            }
        }
    }

    fun onConfigurationChanged() {
        if (!attached) return
        updateSafePosition()
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
        currentSession = null
        detach()
    }

    private fun render(
        session: ActiveRunSession,
        logs: RuntimeOverlayLogState?,
    ) {
        val controls = OverlayPresentation.controls(session.runtimeState)
        currentPrimaryAction = controls.primaryAction
        pauseResumeButton.text = controls.primaryButtonText
        pauseResumeButton.isEnabled = controls.primaryButtonEnabled
        pauseResumeButton.contentDescription = when (controls.primaryAction) {
            OverlayPrimaryAction.START -> "开始购票辅助任务"
            OverlayPrimaryAction.PAUSE -> "暂停购票辅助任务"
            OverlayPrimaryAction.RESUME -> "开始购票辅助任务"
            null -> controls.primaryButtonText
        }
        stopButton.isEnabled = true
        controlView.contentDescription = "${controls.phaseText}阶段，${controls.statusText}"

        val visibleEntries = if (logs?.runId == session.runId) {
            logs.entries.takeLast(MAX_LOG_LINES)
        } else {
            emptyList()
        }
        logView.text = if (visibleEntries.isEmpty()) {
            formatFallbackLine(session, controls)
        } else {
            visibleEntries.joinToString(separator = "\n", transform = ::formatLogLine)
        }
        logView.post(::publishBounds)
    }

    private fun formatFallbackLine(
        session: ActiveRunSession,
        controls: OverlayControlPresentation,
    ): String = buildString {
        append(timeFormatter.format(Instant.ofEpochMilli(session.startedAtEpochMillis)))
        append(" [")
        append(controls.phaseText)
        append("] ")
        append(controls.statusText)
    }

    private fun formatLogLine(entry: OverlayLogEntry): String = buildString {
        append(timeFormatter.format(Instant.ofEpochMilli(entry.occurredAtEpochMillis)))
        append(" [")
        append(
            when (entry.phase) {
                com.ticketassistant.android.runtime.AutomationPhase.SALE -> "开售"
                com.ticketassistant.android.runtime.AutomationPhase.RETURN_MONITOR -> "回流"
            },
        )
        append("] ")
        append(entry.message)
        entry.attemptNumber?.let {
            append("（第")
            append(it)
            append("次）")
        }
    }

    private fun attachIfNeeded() {
        if (attached) return
        updateLayoutValues()
        val logAdded = runCatching {
            windowManager.addView(logView, logLayoutParams)
        }.isSuccess
        if (!logAdded) return

        val controlsAdded = runCatching {
            windowManager.addView(controlView, controlLayoutParams)
        }.isSuccess
        if (!controlsAdded) {
            runCatching { windowManager.removeViewImmediate(logView) }
            return
        }
        attached = true
        logView.post {
            updateSafePosition()
            publishBounds()
        }
    }

    private fun detach() {
        if (!attached) return
        runCatching { windowManager.removeViewImmediate(logView) }
        runCatching { windowManager.removeViewImmediate(controlView) }
        attached = false
        currentPrimaryAction = null
        OverlayBoundsStore.clear()
    }

    private fun updateSafePosition() {
        updateLayoutValues()
        runCatching { windowManager.updateViewLayout(logView, logLayoutParams) }
        runCatching { windowManager.updateViewLayout(controlView, controlLayoutParams) }
        logView.post(::publishBounds)
    }

    private fun updateLayoutValues() {
        val safeInsets = safeInsets()
        val safeStart = safeInsets.left + dp(SAFE_MARGIN_DP)
        val safeTop = safeInsets.top + dp(SAFE_MARGIN_DP)
        val availableWidth = (screenWidth() - safeStart - safeInsets.right - dp(SAFE_MARGIN_DP))
            .coerceAtLeast(1)

        controlLayoutParams.x = safeStart
        controlLayoutParams.y = safeTop
        logLayoutParams.x = safeStart
        logLayoutParams.y = safeTop + dp(CONTROL_WINDOW_HEIGHT_DP + WINDOW_GAP_DP)
        logLayoutParams.width = minOf(dp(MAX_LOG_WIDTH_DP), availableWidth)
    }

    private fun createLogView(): TextView = TextView(service).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        maxLines = MAX_LOG_LINES
        setPadding(dp(8), dp(6), dp(8), dp(6))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.argb(105, 20, 20, 20))
        }
    }

    private fun createControlButton(): Button = Button(service).apply {
        isAllCaps = false
        textSize = 13f
        minWidth = dp(CONTROL_BUTTON_WIDTH_DP)
        minHeight = dp(CONTROL_WINDOW_HEIGHT_DP)
        minimumWidth = dp(CONTROL_BUTTON_WIDTH_DP)
        minimumHeight = dp(CONTROL_WINDOW_HEIGHT_DP)
        setPadding(dp(10), 0, dp(10), 0)
    }

    private fun createControlView(): LinearLayout = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(
            pauseResumeButton,
            LinearLayout.LayoutParams(
                dp(CONTROL_BUTTON_WIDTH_DP),
                dp(CONTROL_WINDOW_HEIGHT_DP),
            ),
        )
        addView(
            stopButton,
            LinearLayout.LayoutParams(
                dp(CONTROL_BUTTON_WIDTH_DP),
                dp(CONTROL_WINDOW_HEIGHT_DP),
            ),
        )
    }

    private fun createLogLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            dp(MAX_LOG_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            OverlayWindowPolicy.LOG_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "TicketAssistantLogOverlay"
        }

    private fun createControlLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            dp(CONTROL_BUTTON_WIDTH_DP * 2),
            dp(CONTROL_WINDOW_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            OverlayWindowPolicy.CONTROL_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "TicketAssistantControlOverlay"
        }

    @Suppress("DEPRECATION")
    private fun safeInsets(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout(),
            )
            return Rect(insets.left, insets.top, insets.right, insets.bottom)
        }

        logView.rootWindowInsets?.let { insets ->
            val cutoutInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                displayCutoutInsets(insets)
            } else {
                Rect()
            }
            return Rect(
                maxOf(insets.systemWindowInsetLeft, cutoutInsets.left),
                maxOf(insets.systemWindowInsetTop, cutoutInsets.top),
                maxOf(insets.systemWindowInsetRight, cutoutInsets.right),
                maxOf(insets.systemWindowInsetBottom, cutoutInsets.bottom),
            )
        }
        return Rect(0, dp(LEGACY_SAFE_TOP_DP), 0, 0)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun displayCutoutInsets(insets: WindowInsets): Rect {
        val cutout = insets.displayCutout ?: return Rect()
        return Rect(
            cutout.safeInsetLeft,
            cutout.safeInsetTop,
            cutout.safeInsetRight,
            cutout.safeInsetBottom,
        )
    }

    @Suppress("DEPRECATION")
    private fun screenWidth(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.bounds.width()
        }
        return service.resources.displayMetrics.widthPixels
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    private fun publishBounds() {
        if (!attached || !logView.isAttachedToWindow || !controlView.isAttachedToWindow) {
            OverlayBoundsStore.clear()
            return
        }
        OverlayBoundsStore.update(
            listOf(logView.screenBounds(), controlView.screenBounds()),
        )
    }

    private fun View.screenBounds(): com.ticketassistant.android.accessibility.UiBounds {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return com.ticketassistant.android.accessibility.UiBounds(
            left = location[0],
            top = location[1],
            right = location[0] + width,
            bottom = location[1] + height,
        )
    }

    companion object {
        private const val MAX_LOG_LINES = 4
        private const val SAFE_MARGIN_DP = 8
        private const val WINDOW_GAP_DP = 4
        private const val CONTROL_BUTTON_WIDTH_DP = 72
        private const val CONTROL_WINDOW_HEIGHT_DP = 48
        private const val LEGACY_SAFE_TOP_DP = 24
        private const val MAX_LOG_WIDTH_DP = 300
    }
}
