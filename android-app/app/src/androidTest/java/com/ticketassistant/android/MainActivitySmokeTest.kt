package com.ticketassistant.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
            device.wait(Until.hasObject(By.text("购票任务配置")), SCREEN_TIMEOUT_MS),
        )
    }

    @Test
    fun configScreen_exposesTargetsAndManualPaymentBoundary() {
        assertEquals(TARGET_PACKAGE, device.currentPackageName)
        assertNotNull(device.findObject(By.text("当前支持：大麦 Android")))
        assertNotNull(device.findObject(By.text("只抢票")))
        assertNotNull(device.findObject(By.text("项目识别关键词")))
        assertNotNull(device.findObject(By.text("目标场次")))
        assertNotNull(device.findObject(By.text("目标票档名称")))
        assertNotNull(
            device.findObject(
                By.text("付款始终由你手动完成。应用不会保存账号、实名信息或支付资料。"),
            ),
        )
    }

    @Test
    fun configScreen_scrollsToSafetyControlsWithoutLaunchingDamai() {
        val centerX = device.displayWidth / 2
        device.swipe(
            centerX,
            device.displayHeight * 4 / 5,
            centerX,
            device.displayHeight / 4,
            30,
        )

        assertTrue(
            "底部安全提示未在 5 秒内显示",
            device.wait(
                Until.hasObject(By.text("开始后请自行打开大麦；本应用不会自动启动售票软件。")),
                SCREEN_TIMEOUT_MS,
            ),
        )
        assertNotNull(device.findObject(By.text("最大提交次数")))
        assertNotNull(device.findObject(By.text("最长运行（分钟）")))
        assertNotNull(device.findObject(By.text("保存并开始")))
        assertEquals(TARGET_PACKAGE, device.currentPackageName)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.ticketassistant.android"
        const val SCREEN_TIMEOUT_MS = 5_000L
    }
}
