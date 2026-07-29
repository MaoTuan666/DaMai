package com.ticketassistant.android.overlay

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowPolicyTest {
    @Test
    fun `log window cannot receive focus or touch`() {
        assertTrue(
            OverlayWindowPolicy.LOG_FLAGS and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0,
        )
        assertTrue(
            OverlayWindowPolicy.LOG_FLAGS and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0,
        )
    }

    @Test
    fun `control window only passes touch outside its bounds`() {
        assertTrue(
            OverlayWindowPolicy.CONTROL_FLAGS and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0,
        )
        assertTrue(
            OverlayWindowPolicy.CONTROL_FLAGS and
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0,
        )
        assertEquals(
            0,
            OverlayWindowPolicy.CONTROL_FLAGS and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        )
        assertFalse(
            OverlayWindowPolicy.CONTROL_FLAGS and
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL == 0,
        )
    }
}
