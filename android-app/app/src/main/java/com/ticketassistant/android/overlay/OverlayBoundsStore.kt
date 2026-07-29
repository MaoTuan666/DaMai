package com.ticketassistant.android.overlay

import com.ticketassistant.android.accessibility.UiBounds
import java.util.concurrent.atomic.AtomicReference

object OverlayBoundsStore {
    private val bounds = AtomicReference<List<UiBounds>>(emptyList())

    fun update(regions: List<UiBounds>) {
        bounds.set(regions.filter { it.right > it.left && it.bottom > it.top })
    }

    fun snapshot(): List<UiBounds> = bounds.get()

    fun clear() {
        bounds.set(emptyList())
    }
}
