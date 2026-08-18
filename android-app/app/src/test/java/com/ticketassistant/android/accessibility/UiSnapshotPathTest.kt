package com.ticketassistant.android.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiSnapshotPathTest {
    @Test
    fun `source child index survives unreadable sibling gaps`() {
        val first = node(path = listOf(0))
        val third = node(path = listOf(2))
        val root = node(
            path = emptyList(),
            children = listOf(first, third),
        )

        assertEquals(first, root.childAtSourceIndex(0))
        assertNull(root.childAtSourceIndex(1))
        assertEquals(third, root.childAtSourceIndex(2))
    }

    private fun node(
        path: List<Int>,
        children: List<UiNodeSnapshot> = emptyList(),
    ) = UiNodeSnapshot(
        path = path,
        packageName = "cn.damai",
        viewIdResourceName = null,
        text = null,
        contentDescription = null,
        className = "android.view.View",
        boundsInScreen = UiBounds(0, 0, 1, 1),
        isVisibleToUser = true,
        isEnabled = true,
        isClickable = false,
        isSelected = false,
        isCheckable = false,
        checkedState = UiCheckedState.NOT_CHECKABLE,
        isFocusable = false,
        isScrollable = false,
        actionIds = emptySet(),
        children = children,
    )
}
