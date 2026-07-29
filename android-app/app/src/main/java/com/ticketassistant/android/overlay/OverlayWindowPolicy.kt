package com.ticketassistant.android.overlay

import android.view.WindowManager

internal object OverlayWindowPolicy {
    const val LOG_FLAGS: Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    const val CONTROL_FLAGS: Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
}
