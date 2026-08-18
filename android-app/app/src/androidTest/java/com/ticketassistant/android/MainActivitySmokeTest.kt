package com.ticketassistant.android

import android.app.ActivityManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TicketPlatform
import com.ticketassistant.android.domain.ValidatedTicketTask
import com.ticketassistant.android.runtime.ActiveRunStore
import com.ticketassistant.android.runtime.TaskForegroundService
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    private lateinit var device: UiDevice

    @Before
    fun launchConfigScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        device = UiDevice.getInstance(instrumentation)

        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )

        assertTrue(
            "配置页未在 5 秒内显示",
            device.wait(Until.hasObject(By.text("购票辅助")), SCREEN_TIMEOUT_MS),
        )
    }

    @Test
    fun configScreen_exposesTargetsAndManualPaymentBoundary() {
        assertEquals(TARGET_PACKAGE, device.currentPackageName)
        assertNotNull(device.findObject(By.text("创建新的购票任务")))
        assertNotNull(device.findObject(By.text("当前支持大麦 Android")))
        assertNotNull(device.findObject(By.text("只抢票")))
        assertTrue("未找到演出日期", scrollUntilText("演出日期"))
        assertTrue("未找到票档价格", scrollUntilText("票档价格（元）"))
        assertTrue(
            "未找到手动付款边界提示",
            scrollUntilText(
                "付款始终由你手动完成。应用不会保存账号、实名信息或支付资料。",
            ),
        )
    }

    @Test
    fun configScreen_scrollsToStartControlWithoutLaunchingDamai() {
        assertTrue("未找到启动按钮", scrollUntilText("保存配置并显示悬浮窗"))
        assertNotNull(device.findObject(By.text("保存配置并显示悬浮窗")))
        assertEquals(TARGET_PACKAGE, device.currentPackageName)
    }

    @Test
    fun foregroundService_duplicateStop_allowsNextRun() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TicketAssistantApplication
        val firstRunId = "android-test-${UUID.randomUUID()}"
        val firstTaskId = application.taskRepository.createArmedTask(testTask(), firstRunId)

        try {
            TaskForegroundService.start(context, firstRunId, firstTaskId)
            assertTrue(
                "首个任务未启动",
                waitUntil { ActiveRunStore.isActive(firstRunId) },
            )

            repeat(4) {
                TaskForegroundService.stop(context, firstRunId, firstTaskId)
                TaskForegroundService.pause(context, firstRunId, firstTaskId)
                TaskForegroundService.begin(context, firstRunId, firstTaskId)
                TaskForegroundService.resume(context, firstRunId, firstTaskId)
            }
            assertTrue(
                "重复停止和迟到控制请求后前台服务仍残留",
                waitUntil { !isRuntimeServiceRunning(context) },
            )

            TaskForegroundService.pause(context, firstRunId, firstTaskId)
            TaskForegroundService.begin(context, firstRunId, firstTaskId)
            TaskForegroundService.resume(context, firstRunId, firstTaskId)
            assertTrue(
                "迟到控制请求重建了空服务",
                waitUntil { !isRuntimeServiceRunning(context) },
            )

            val secondRunId = "android-test-${UUID.randomUUID()}"
            val secondTaskId = application.taskRepository.createArmedTask(testTask(), secondRunId)
            TaskForegroundService.start(context, secondRunId, secondTaskId)
            assertTrue(
                "残留服务阻止了下一次任务",
                waitUntil { ActiveRunStore.isActive(secondRunId) },
            )
            TaskForegroundService.stop(context, secondRunId, secondTaskId)
            assertTrue(
                "第二个任务停止后前台服务仍残留",
                waitUntil { !isRuntimeServiceRunning(context) },
            )
        } finally {
            TaskForegroundService.stop(context, firstRunId, firstTaskId)
            ActiveRunStore.session.value?.let { session ->
                TaskForegroundService.stop(context, session.runId, session.taskId)
            }
        }
    }

    private fun scrollUntilText(text: String): Boolean {
        repeat(MAX_SCROLL_ATTEMPTS) {
            if (device.hasObject(By.text(text))) return true
            val centerX = device.displayWidth / 2
            device.swipe(
                centerX,
                device.displayHeight * 4 / 5,
                centerX,
                device.displayHeight / 4,
                30,
            )
        }
        return device.wait(Until.hasObject(By.text(text)), SCREEN_TIMEOUT_MS)
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + SERVICE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(SERVICE_POLL_INTERVAL_MS)
        }
        return condition()
    }

    @Suppress("DEPRECATION")
    private fun isRuntimeServiceRunning(context: android.content.Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java)
        return manager.getRunningServices(Int.MAX_VALUE).any { service ->
            service.service.className == TaskForegroundService::class.java.name
        }
    }

    private fun testTask() = ValidatedTicketTask(
        platform = TicketPlatform.DAMAI,
        runMode = RunMode.SALE_ONLY,
        targetDate = "2026-08-17",
        targetPriceFen = 58_000L,
        adapterConfig = "{}",
    )

    private companion object {
        const val TARGET_PACKAGE = "com.ticketassistant.android"
        const val SCREEN_TIMEOUT_MS = 5_000L
        const val MAX_SCROLL_ATTEMPTS = 8
        const val SERVICE_TIMEOUT_MS = 5_000L
        const val SERVICE_POLL_INTERVAL_MS = 50L
    }
}
