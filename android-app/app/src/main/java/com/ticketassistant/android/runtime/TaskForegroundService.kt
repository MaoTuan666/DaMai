package com.ticketassistant.android.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ticketassistant.android.MainActivity
import com.ticketassistant.android.R
import com.ticketassistant.android.TicketAssistantApplication
import com.ticketassistant.android.accessibility.AccessibilitySnapshotStore
import com.ticketassistant.android.accessibility.SnapshotCaptureRequests
import com.ticketassistant.android.accessibility.SnapshotSequenceStore
import com.ticketassistant.android.accessibility.UiSnapshot
import com.ticketassistant.android.domain.TaskState
import com.ticketassistant.android.domain.TicketTask
import com.ticketassistant.android.overlay.RuntimeOverlayStore
import com.ticketassistant.android.platform.api.ActionDecision
import com.ticketassistant.android.platform.api.AdapterStopReason
import com.ticketassistant.android.platform.api.PageResult
import com.ticketassistant.android.platform.api.PlatformDecisionCoordinator
import com.ticketassistant.android.platform.api.PlatformEvaluation
import com.ticketassistant.android.platform.api.toActionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val runtimeMutex = Mutex()
    private var currentRunId: String? = null
    private var engine: AutomationEngine? = null
    private var pendingAction: ScheduledAction? = null
    private var actionTimeoutJob: Job? = null
    private var retryCaptureJob: Job? = null
    private val recordedObservationKeys = linkedSetOf<String>()
    private val awaitingValidStartLogLimiter = AwaitingValidStartLogLimiter()
    private var isStopping = false
    @Volatile
    private var terminalStopRequested = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (terminalStopRequested) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        return when (intent?.action) {
            ACTION_START -> startRun(intent, startId)
            ACTION_BEGIN -> beginRun(intent, startId)
            ACTION_PAUSE -> changePausedState(intent, startId, pause = true)
            ACTION_RESUME -> changePausedState(intent, startId, pause = false)
            ACTION_STOP -> stopRun(intent, startId)
            else -> {
                stopSelfResult(startId)
                START_NOT_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        currentRunId?.let {
            ActiveRunStore.disarm(it)
            RuntimeOverlayStore.end(it)
            SnapshotSequenceStore.clear(it)
        }
        AccessibilitySnapshotStore.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRun(intent: Intent, startId: Int): Int {
        val runId = intent.getStringExtra(EXTRA_RUN_ID)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, INVALID_TASK_ID)
        if (runId.isNullOrBlank() || taskId <= 0) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val existingRunId = currentRunId
        if (existingRunId != null) {
            if (existingRunId != runId) {
                stopSupersededTask(runId, taskId)
            }
            return START_NOT_STICKY
        }

        promoteToForeground(buildNotification(runId, taskId))
        currentRunId = runId
        val startedAtElapsedRealtime = SystemClock.elapsedRealtime()
        awaitingValidStartLogLimiter.reset()
        RuntimeOverlayStore.begin(runId)
        ActiveRunStore.arm(
            runId = runId,
            taskId = taskId,
            startedAtElapsedRealtimeMillis = startedAtElapsedRealtime,
        )
        initializeEngine(runId, taskId)
        return START_NOT_STICKY
    }

    private fun changePausedState(
        intent: Intent,
        startId: Int,
        pause: Boolean,
    ): Int {
        val runId = intent.getStringExtra(EXTRA_RUN_ID)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, INVALID_TASK_ID)
        val session = ActiveRunStore.session.value
        if (
            runId.isNullOrBlank() ||
            taskId <= 0 ||
            session?.runId != runId ||
            session.taskId != taskId ||
            currentRunId != runId ||
            isStopping
        ) {
            if (currentRunId == null) {
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }

        serviceScope.launch {
            runtimeMutex.withLock {
                val runtimeEngine = engine ?: return@withLock
                val transition = if (pause) {
                    runtimeEngine.pause()
                } else {
                    runtimeEngine.resume()
                }
                if (pause && transition is StateTransition.Applied) {
                    pendingAction = null
                    actionTimeoutJob?.cancel()
                    actionTimeoutJob = null
                    retryCaptureJob?.cancel()
                    retryCaptureJob = null
                }
                if (!pause && transition is StateTransition.Applied) {
                    AccessibilitySnapshotStore.clear()
                    scheduleSnapshotCapture(runId, delayMillis = 0)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun beginRun(intent: Intent, startId: Int): Int {
        val runId = intent.getStringExtra(EXTRA_RUN_ID)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, INVALID_TASK_ID)
        val session = ActiveRunStore.session.value
        if (
            runId.isNullOrBlank() ||
            taskId <= 0 ||
            session?.runId != runId ||
            session.taskId != taskId ||
            currentRunId != runId ||
            isStopping
        ) {
            if (currentRunId == null) {
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }

        serviceScope.launch {
            runtimeMutex.withLock {
                val runtimeEngine = engine ?: return@withLock
                if (runtimeEngine.start() is StateTransition.Applied) {
                    AccessibilitySnapshotStore.clear()
                    scheduleSnapshotCapture(runId, delayMillis = 0)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopRun(intent: Intent, startId: Int): Int {
        val runId = intent.getStringExtra(EXTRA_RUN_ID)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, INVALID_TASK_ID)
        if (runId.isNullOrBlank() || taskId <= 0 || !ActiveRunStore.isActive(runId)) {
            if (currentRunId == null) {
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }
        if (isStopping) return START_NOT_STICKY
        isStopping = true

        val repository = (application as TicketAssistantApplication).taskRepository
        serviceScope.launch {
            try {
                runtimeMutex.withLock {
                    pendingAction = null
                    actionTimeoutJob?.cancel()
                    actionTimeoutJob = null
                    retryCaptureJob?.cancel()
                    retryCaptureJob = null
                    val activeEngine = engine
                    if (activeEngine != null) {
                        activeEngine.stop(RuntimeStopReason.USER_REQUESTED)
                    } else {
                        runCatching {
                            repository.stopTask(
                                taskId = taskId,
                                runId = runId,
                                reason = RuntimeStopReason.USER_REQUESTED.name,
                            )
                        }
                    }
                }
            } finally {
                lifecycleMutex.withLock {
                    finishRun(runId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun initializeEngine(
        runId: String,
        taskId: Long,
    ) {
        val ticketApplication = application as TicketAssistantApplication
        val repository = ticketApplication.taskRepository
        serviceScope.launch {
            val components = lifecycleMutex.withLock {
                try {
                    val task = repository.getTask(taskId)
                    if (task == null || !ActiveRunStore.isActive(runId)) {
                        finishRun(runId)
                        return@withLock null
                    }
                    val adapter = ticketApplication.platformAdapterRegistry
                        .findByPlatform(task.platform)
                        ?: error("No adapter registered for ${task.platform}")

                    val runtimeEngine = AutomationEngine(
                        runId = runId,
                        task = task,
                        nowMillis = SystemClock::elapsedRealtime,
                        recorder = RuntimeStateRecorder { state, eventCode, detail ->
                            RuntimeOverlayStore.record(
                                runId = runId,
                                state = state,
                                eventCode = eventCode,
                                sanitizedDetail = detail,
                            )
                            repository.updateRuntimeState(
                                taskId = taskId,
                                runId = runId,
                                state = state.taskState,
                                eventCode = eventCode,
                                sanitizedDetail = detail,
                            )
                        },
                        onStateChanged = { state ->
                            ActiveRunStore.updateRuntimeState(runId, state)
                        },
                        actionPolicy = adapter.timingPolicy.toActionPolicy(),
                    )
                    engine = runtimeEngine
                    runtimeEngine.initialize()
                    RuntimeComponents(
                        engine = runtimeEngine,
                        task = task,
                        decisionCoordinator = PlatformDecisionCoordinator(
                            ticketApplication.platformAdapterRegistry,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    runCatching {
                        repository.stopTask(
                            taskId = taskId,
                            runId = runId,
                            reason = "ENGINE_INITIALIZATION_FAILED",
                        )
                    }
                    finishRun(runId)
                    null
                }
            } ?: return@launch

            launchActionResultCollector(runId, components.engine)
            launchSnapshotCollector(runId, components)
        }
    }

    private fun CoroutineScope.launchSnapshotCollector(
        runId: String,
        components: RuntimeComponents,
    ) = launch {
        AccessibilitySnapshotStore.latest
            .filterNotNull()
            .collect { snapshot ->
                if (!ActiveRunStore.isActive(runId)) return@collect
                val terminal = runtimeMutex.withLock {
                    processSnapshot(runId, snapshot, components)
                }
                if (terminal) {
                    lifecycleMutex.withLock {
                        finishRun(runId)
                    }
                    return@collect
                }
            }
    }

    private fun CoroutineScope.launchActionResultCollector(
        runId: String,
        runtimeEngine: AutomationEngine,
    ) = launch {
        AccessibilityActionBus.results.collect { actionResult ->
            if (actionResult.runId != runId || !ActiveRunStore.isActive(runId)) {
                return@collect
            }
            val terminal = runtimeMutex.withLock {
                val action = pendingAction
                if (action?.actionId != actionResult.actionId) {
                    return@withLock false
                }
                pendingAction = null
                actionTimeoutJob?.cancel()
                actionTimeoutJob = null
                runtimeEngine.completeAction(action, actionResult.result)
                when (runtimeEngine.state.value.taskState) {
                    TaskState.STOPPED -> true
                    TaskState.RUNNING -> {
                        scheduleSnapshotCapture(runId, delayMillis = SNAPSHOT_AFTER_ACTION_MILLIS)
                        false
                    }

                    else -> false
                }
            }
            if (terminal) {
                lifecycleMutex.withLock {
                    finishRun(runId)
                }
                return@collect
            }
        }
    }

    private suspend fun processSnapshot(
        runId: String,
        snapshot: UiSnapshot,
        components: RuntimeComponents,
    ): Boolean {
        if (snapshot.runId != runId) return false
        val runtimeEngine = components.engine
        if (
            SnapshotProbePolicy.arrivalDisposition(
                captureSequence = snapshot.captureSequence,
                lastObservedSequence = runtimeEngine.state.value.lastSnapshotSequence,
            ) == SnapshotArrivalDisposition.IGNORE_AND_KEEP_PROBE
        ) {
            return continueWithSnapshotProbe(runId)
        }
        retryCaptureJob?.cancel()
        retryCaptureJob = null
        runtimeEngine.observeSnapshot(
            snapshotRunId = snapshot.runId,
            sequence = snapshot.captureSequence,
        )

        repeat(MAX_DECISION_STEPS_PER_SNAPSHOT) {
            val evaluation = components.decisionCoordinator.evaluate(
                expectedRunId = runId,
                snapshot = snapshot,
                task = components.task,
                runtime = runtimeEngine.state.value,
            )
            when (evaluation) {
                is PlatformEvaluation.Ignored -> {
                    scheduleSnapshotCapture(
                        runId = runId,
                        delayMillis = PAGE_POLL_INTERVAL_MILLIS,
                    )
                    return false
                }

                is PlatformEvaluation.AwaitingValidStart -> {
                    recordAwaitingValidStart(
                        runtimeEngine = runtimeEngine,
                        pageResult = evaluation.pageResult,
                    )
                    scheduleSnapshotCapture(
                        runId = runId,
                        delayMillis = PAGE_POLL_INTERVAL_MILLIS,
                    )
                    return false
                }

                is PlatformEvaluation.SafetyPause -> {
                    recordPageResultEventOnce(runtimeEngine, evaluation.pageResult)
                    pendingAction = null
                    actionTimeoutJob?.cancel()
                    actionTimeoutJob = null
                    retryCaptureJob?.cancel()
                    retryCaptureJob = null
                    runtimeEngine.safetyPause(evaluation.reason)
                    return false
                }

                is PlatformEvaluation.ContractRejected -> {
                    val pauseReason = evaluation.safetyPauseReason
                    if (pauseReason == null) {
                        recordAwaitingValidStart(
                            runtimeEngine = runtimeEngine,
                            event = AwaitingValidStartLogEvent.contractRejected(
                                evaluation.violation,
                            ),
                        )
                        scheduleSnapshotCapture(
                            runId = runId,
                            delayMillis = PAGE_POLL_INTERVAL_MILLIS,
                        )
                        return false
                    }
                    pendingAction = null
                    actionTimeoutJob?.cancel()
                    actionTimeoutJob = null
                    retryCaptureJob?.cancel()
                    retryCaptureJob = null
                    runtimeEngine.safetyPause(pauseReason)
                    return false
                }

                is PlatformEvaluation.Decision -> {
                    recordObservationOnce(
                        runtimeEngine = runtimeEngine,
                        eventCode = evaluation.page.observationEventCode,
                        key = evaluation.page.fingerprint.value,
                        detail = evaluation.page.pageType.code,
                    )

                    if (runtimeEngine.state.value.taskState == TaskState.WAIT_TARGET_APP) {
                        val transition = runtimeEngine.recognizeTargetPage(
                            snapshot.captureSequence,
                        )
                        if (transition !is StateTransition.Applied) {
                            return continueWithSnapshotProbe(runId)
                        }
                        return@repeat
                    }

                    when (val action = evaluation.action) {
                        is ActionDecision.Wait -> {
                            recordObservationOnce(
                                runtimeEngine = runtimeEngine,
                                eventCode = action.eventCode,
                                key = evaluation.page.fingerprint.value,
                                detail = action.reason.name,
                            )
                            scheduleSnapshotCapture(
                                runId = runId,
                                delayMillis = PAGE_POLL_INTERVAL_MILLIS,
                            )
                            return false
                        }

                        is ActionDecision.SwitchPhase -> {
                            recordObservationOnce(
                                runtimeEngine = runtimeEngine,
                                eventCode = action.eventCode,
                                key = evaluation.page.fingerprint.value,
                                detail = action.phase.name,
                            )
                            val transition = runtimeEngine.switchPhase(action.phase)
                            if (transition !is StateTransition.Applied) {
                                return continueWithSnapshotProbe(runId)
                            }
                            return@repeat
                        }

                        is ActionDecision.Stop -> {
                            runtimeEngine.recordAdapterEvent(
                                action.eventCode,
                                action.reason.name,
                            )
                            pendingAction = null
                            actionTimeoutJob?.cancel()
                            actionTimeoutJob = null
                            retryCaptureJob?.cancel()
                            retryCaptureJob = null
                            return if (action.reason == AdapterStopReason.ORDER_LOCKED) {
                                runtimeEngine.lockOrder()
                                alertUserForTakeover(runId, components.task.id)
                                false
                            } else {
                                runtimeEngine.stop(RuntimeStopReason.PLATFORM_REQUESTED)
                                true
                            }
                        }

                        is ActionDecision.Click,
                        is ActionDecision.GlobalBack,
                        -> return scheduleExecutableAction(
                            runId = runId,
                            snapshot = snapshot,
                            page = evaluation.page,
                            action = action,
                            runtimeEngine = runtimeEngine,
                        )
                    }
                }
            }
        }
        runtimeEngine.safetyPause(SafetyPauseReason.UNKNOWN_PAGE)
        return false
    }

    private suspend fun scheduleExecutableAction(
        runId: String,
        snapshot: UiSnapshot,
        page: PageResult.Recognized,
        action: ActionDecision,
        runtimeEngine: AutomationEngine,
    ): Boolean {
        if (pendingAction != null) return continueWithSnapshotProbe(runId)
        val eventCode: String
        val executable: ExecutableActionDecision
        when (action) {
            is ActionDecision.Click -> {
                eventCode = action.eventCode
                executable = ExecutableActionDecision.Click(action)
            }

            is ActionDecision.GlobalBack -> {
                eventCode = action.eventCode
                executable = ExecutableActionDecision.GlobalBack(action)
            }

            else -> return continueWithSnapshotProbe(runId)
        }

        return when (
            val decision = runtimeEngine.requestAction(
                candidate = ActionCandidate(
                    actionKey = eventCode,
                    pageFingerprint = page.fingerprint.value,
                ),
                snapshotSequence = snapshot.captureSequence,
            )
        ) {
            is EngineActionDecision.Scheduled -> {
                pendingAction = decision.action
                val submitted = AccessibilityActionBus.submit(
                    AccessibilityActionCommand(
                        runId = runId,
                        snapshotSequence = snapshot.captureSequence,
                        expectedPackageName = snapshot.packageName,
                        scheduledAction = decision.action,
                        page = page,
                        decision = executable,
                    ),
                )
                if (!submitted) {
                    pendingAction = null
                    runtimeEngine.completeAction(
                        decision.action,
                        ActionExecutionResult.CANCELLED,
                    )
                    return continueWithSnapshotProbe(runId)
                } else {
                    scheduleActionTimeout(runId, decision.action, runtimeEngine)
                }
                runtimeEngine.state.value.taskState == TaskState.STOPPED
            }

            is EngineActionDecision.Waiting -> {
                continueWithSnapshotProbe(
                    runId = runId,
                    delayMillis = SnapshotProbePolicy.retryDelayMillis(
                        requestedDelayMillis = decision.retryAfterMillis,
                        defaultDelayMillis = PAGE_POLL_INTERVAL_MILLIS,
                    ),
                )
            }

            is EngineActionDecision.Paused -> false

            EngineActionDecision.StateDoesNotAllowAction ->
                continueWithSnapshotProbe(runId)

            is EngineActionDecision.Stopped -> true
        }
    }

    private suspend fun recordPageResultEventOnce(
        runtimeEngine: AutomationEngine,
        pageResult: PageResult?,
    ) {
        when (pageResult) {
            is PageResult.Unknown -> recordObservationOnce(
                runtimeEngine,
                pageResult.eventCode,
                pageResult.reason.name,
                pageResult.reason.name,
            )

            is PageResult.Mismatch -> recordObservationOnce(
                runtimeEngine,
                pageResult.eventCode,
                pageResult.field.name,
                pageResult.field.name,
            )

            is PageResult.Recognized -> recordObservationOnce(
                runtimeEngine,
                pageResult.observationEventCode,
                pageResult.fingerprint.value,
                pageResult.pageType.code,
            )

            null -> Unit
        }
    }

    private suspend fun recordAwaitingValidStart(
        runtimeEngine: AutomationEngine,
        pageResult: PageResult,
    ) = recordAwaitingValidStart(
        runtimeEngine = runtimeEngine,
        event = AwaitingValidStartLogEvent.from(
            pageResult = pageResult,
            phase = runtimeEngine.state.value.phase,
        ),
    )

    private suspend fun recordAwaitingValidStart(
        runtimeEngine: AutomationEngine,
        event: AwaitingValidStartLogEvent,
    ) {
        if (
            awaitingValidStartLogLimiter.shouldRecord(
                event = event,
                nowElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            )
        ) {
            runtimeEngine.recordAdapterEvent(
                eventCode = event.eventCode,
                sanitizedDetail = event.sanitizedDetail,
            )
        }
    }

    private suspend fun recordObservationOnce(
        runtimeEngine: AutomationEngine,
        eventCode: String?,
        key: String,
        detail: String,
    ) {
        if (eventCode == null) return
        val observationKey = "$eventCode:$key"
        if (!recordedObservationKeys.add(observationKey)) return
        if (recordedObservationKeys.size > MAX_RECORDED_OBSERVATIONS) {
            recordedObservationKeys.remove(recordedObservationKeys.first())
        }
        runtimeEngine.recordAdapterEvent(eventCode, detail)
    }

    private fun scheduleSnapshotCapture(
        runId: String,
        delayMillis: Long,
    ) {
        retryCaptureJob?.cancel()
        retryCaptureJob = serviceScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val probeStartedAtMillis = SystemClock.elapsedRealtime()
            var unavailableLogged = false
            while (true) {
                val taskState = engine?.state?.value?.taskState
                if (
                    !ActiveRunStore.isActive(runId) ||
                    (
                        taskState != TaskState.WAIT_TARGET_APP &&
                            taskState != TaskState.RUNNING
                        )
                ) {
                    return@launch
                }
                SnapshotCaptureRequests.request(runId)

                if (
                    !unavailableLogged &&
                    taskState == TaskState.WAIT_TARGET_APP &&
                    SystemClock.elapsedRealtime() - probeStartedAtMillis >=
                    SNAPSHOT_UNAVAILABLE_LOG_DELAY_MILLIS
                ) {
                    runtimeMutex.withLock {
                        val runtimeEngine = engine
                        if (
                            runtimeEngine != null &&
                            ActiveRunStore.isActive(runId) &&
                            runtimeEngine.state.value.taskState == TaskState.WAIT_TARGET_APP
                        ) {
                            recordAwaitingValidStart(
                                runtimeEngine = runtimeEngine,
                                event = AwaitingValidStartLogEvent.snapshotUnavailable(),
                            )
                        }
                    }
                    unavailableLogged = true
                }
                delay(PAGE_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun continueWithSnapshotProbe(
        runId: String,
        delayMillis: Long = PAGE_POLL_INTERVAL_MILLIS,
    ): Boolean {
        if (retryCaptureJob?.isActive != true) {
            scheduleSnapshotCapture(runId, delayMillis)
        }
        return false
    }

    private fun scheduleActionTimeout(
        runId: String,
        action: ScheduledAction,
        runtimeEngine: AutomationEngine,
    ) {
        actionTimeoutJob?.cancel()
        actionTimeoutJob = serviceScope.launch {
            delay(ACTION_RESULT_TIMEOUT_MILLIS)
            runtimeMutex.withLock {
                if (
                    ActiveRunStore.isActive(runId) &&
                    pendingAction?.actionId == action.actionId
                ) {
                    pendingAction = null
                    runtimeEngine.completeAction(
                        action,
                        ActionExecutionResult.CANCELLED,
                    )
                    if (runtimeEngine.state.value.taskState == TaskState.RUNNING) {
                        scheduleSnapshotCapture(runId, 0)
                    }
                }
            }
        }
    }

    private fun stopSupersededTask(
        runId: String,
        taskId: Long,
    ) {
        val repository = (application as TicketAssistantApplication).taskRepository
        serviceScope.launch {
            runCatching {
                repository.stopTask(
                    taskId = taskId,
                    runId = runId,
                    reason = "ACTIVE_RUN_ALREADY_EXISTS",
                )
            }
        }
    }

    private fun finishRun(runId: String) {
        pendingAction = null
        actionTimeoutJob?.cancel()
        actionTimeoutJob = null
        retryCaptureJob?.cancel()
        retryCaptureJob = null
        recordedObservationKeys.clear()
        awaitingValidStartLogLimiter.reset()
        ActiveRunStore.disarm(runId)
        RuntimeOverlayStore.end(runId)
        AccessibilitySnapshotStore.clear()
        SnapshotSequenceStore.clear(runId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        terminalStopRequested = true
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.run_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.run_notification_waiting)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        runId: String,
        taskId: Long,
        contentText: String = getString(R.string.run_notification_waiting),
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            createStopIntent(this, runId, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.run_notification_title))
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.run_notification_stop),
                    stopIntent,
                ).build(),
            )
            .build()
    }

    private fun alertUserForTakeover(
        runId: String,
        taskId: Long,
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                runId = runId,
                taskId = taskId,
                contentText = getString(R.string.run_notification_takeover),
            ),
        )
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    TAKEOVER_VIBRATION_MILLIS,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val ACTION_START =
            "com.ticketassistant.android.runtime.action.START"
        private const val ACTION_BEGIN =
            "com.ticketassistant.android.runtime.action.BEGIN"
        private const val ACTION_PAUSE =
            "com.ticketassistant.android.runtime.action.PAUSE"
        private const val ACTION_RESUME =
            "com.ticketassistant.android.runtime.action.RESUME"
        private const val ACTION_STOP =
            "com.ticketassistant.android.runtime.action.STOP"
        private const val EXTRA_RUN_ID = "run_id"
        private const val EXTRA_TASK_ID = "task_id"
        const val CHANNEL_ID = "ticket_task_runtime"
        private const val NOTIFICATION_ID = 1_001
        private const val OPEN_APP_REQUEST_CODE = 1_002
        private const val STOP_REQUEST_CODE = 1_003
        private const val INVALID_TASK_ID = -1L
        private const val SNAPSHOT_AFTER_ACTION_MILLIS = 220L
        private const val PAGE_POLL_INTERVAL_MILLIS = 300L
        private const val SNAPSHOT_UNAVAILABLE_LOG_DELAY_MILLIS = 3_000L
        private const val ACTION_RESULT_TIMEOUT_MILLIS = 1_800L
        private const val TAKEOVER_VIBRATION_MILLIS = 350L
        private const val MAX_DECISION_STEPS_PER_SNAPSHOT = 4
        private const val MAX_RECORDED_OBSERVATIONS = 32

        fun start(
            context: Context,
            runId: String,
            taskId: Long,
        ) {
            context.startForegroundService(
                Intent(context, TaskForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_RUN_ID, runId)
                    putExtra(EXTRA_TASK_ID, taskId)
                },
            )
        }

        fun pause(
            context: Context,
            runId: String,
            taskId: Long,
        ) {
            context.startService(createControlIntent(context, ACTION_PAUSE, runId, taskId))
        }

        fun begin(
            context: Context,
            runId: String,
            taskId: Long,
        ) {
            context.startService(createControlIntent(context, ACTION_BEGIN, runId, taskId))
        }

        fun resume(
            context: Context,
            runId: String,
            taskId: Long,
        ) {
            context.startService(createControlIntent(context, ACTION_RESUME, runId, taskId))
        }

        fun stop(
            context: Context,
            runId: String,
            taskId: Long,
        ) {
            context.startService(createStopIntent(context, runId, taskId))
        }

        private fun createStopIntent(
            context: Context,
            runId: String,
            taskId: Long,
        ): Intent = createControlIntent(context, ACTION_STOP, runId, taskId)

        private fun createControlIntent(
            context: Context,
            controlAction: String,
            runId: String,
            taskId: Long,
        ): Intent = Intent(context, TaskForegroundService::class.java).apply {
            action = controlAction
            putExtra(EXTRA_RUN_ID, runId)
            putExtra(EXTRA_TASK_ID, taskId)
        }
    }

    private data class RuntimeComponents(
        val engine: AutomationEngine,
        val task: TicketTask,
        val decisionCoordinator: PlatformDecisionCoordinator,
    )
}
