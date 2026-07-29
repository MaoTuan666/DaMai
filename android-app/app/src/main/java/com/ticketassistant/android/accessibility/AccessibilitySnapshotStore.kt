package com.ticketassistant.android.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccessibilitySnapshotStore {
    private val mutableLatest = MutableStateFlow<UiSnapshot?>(null)
    val latest: StateFlow<UiSnapshot?> = mutableLatest.asStateFlow()

    fun update(snapshot: UiSnapshot) {
        mutableLatest.value = snapshot
    }

    fun clear() {
        mutableLatest.value = null
    }
}
