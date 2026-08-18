package com.ticketassistant.android.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedAppWindowSelectorTest {
    @Test
    fun `supported active package wins`() {
        assertEquals(
            SupportedAppWindow(id = 1, packageName = DAMAI_PACKAGE),
            SupportedAppWindowSelector.select(
                activePackageName = DAMAI_PACKAGE,
                windows = listOf(
                    window(id = 1, layer = 10, packageName = DAMAI_PACKAGE),
                ),
                supportedPackages = SUPPORTED_PACKAGES,
                assistantPackageName = ASSISTANT_PACKAGE,
            ),
        )
    }

    @Test
    fun `top application window is fallback when overlay owns active root`() {
        assertEquals(
            SupportedAppWindow(id = 1, packageName = DAMAI_PACKAGE),
            SupportedAppWindowSelector.select(
                activePackageName = ASSISTANT_PACKAGE,
                windows = listOf(
                    window(id = 1, layer = 10, packageName = DAMAI_PACKAGE),
                    window(
                        id = 2,
                        layer = 20,
                        category = WindowCategory.ACCESSIBILITY_OVERLAY,
                        packageName = ASSISTANT_PACKAGE,
                    ),
                ),
                supportedPackages = SUPPORTED_PACKAGES,
                assistantPackageName = ASSISTANT_PACKAGE,
            ),
        )
    }

    @Test
    fun `system window blocks an underlying supported window`() {
        assertNull(
            SupportedAppWindowSelector.select(
                activePackageName = SYSTEM_PACKAGE,
                windows = listOf(
                    window(id = 1, layer = 10, packageName = DAMAI_PACKAGE),
                    window(
                        id = 2,
                        layer = 20,
                        category = WindowCategory.OTHER,
                        packageName = SYSTEM_PACKAGE,
                    ),
                ),
                supportedPackages = SUPPORTED_PACKAGES,
                assistantPackageName = ASSISTANT_PACKAGE,
            ),
        )
    }

    @Test
    fun `unknown active root rejects overlay fallback`() {
        assertNull(
            SupportedAppWindowSelector.select(
                activePackageName = null,
                windows = listOf(
                    window(id = 1, layer = 10, packageName = DAMAI_PACKAGE),
                    window(
                        id = 2,
                        layer = 20,
                        category = WindowCategory.ACCESSIBILITY_OVERLAY,
                        packageName = ASSISTANT_PACKAGE,
                    ),
                ),
                supportedPackages = SUPPORTED_PACKAGES,
                assistantPackageName = ASSISTANT_PACKAGE,
            ),
        )
    }

    @Test
    fun `higher application blocks even when supported package owns active root`() {
        assertNull(
            SupportedAppWindowSelector.select(
                activePackageName = DAMAI_PACKAGE,
                windows = listOf(
                    window(id = 1, layer = 10, packageName = DAMAI_PACKAGE),
                    window(id = 2, layer = 20, packageName = OTHER_PACKAGE),
                ),
                supportedPackages = SUPPORTED_PACKAGES,
                assistantPackageName = ASSISTANT_PACKAGE,
            ),
        )
    }

    @Test
    fun `assistant package without a higher accessibility overlay is rejected`() {
        assertNull(
            SupportedAppWindowSelector.select(
                activePackageName = ASSISTANT_PACKAGE,
                windows = listOf(
                    window(id = 1, layer = 10, packageName = DAMAI_PACKAGE),
                ),
                supportedPackages = SUPPORTED_PACKAGES,
                assistantPackageName = ASSISTANT_PACKAGE,
            ),
        )
    }

    private fun window(
        id: Int,
        layer: Int,
        category: WindowCategory = WindowCategory.APPLICATION,
        packageName: String?,
    ) = WindowPackage(
        id = id,
        layer = layer,
        category = category,
        packageName = packageName,
    )

    private companion object {
        const val DAMAI_PACKAGE = "cn.damai"
        const val ASSISTANT_PACKAGE = "com.ticketassistant.android"
        const val OTHER_PACKAGE = "com.example.other"
        const val SYSTEM_PACKAGE = "com.android.permissioncontroller"
        val SUPPORTED_PACKAGES = setOf(DAMAI_PACKAGE)
    }
}
