package com.ticketassistant.android.accessibility

internal enum class WindowCategory {
    APPLICATION,
    ACCESSIBILITY_OVERLAY,
    OTHER,
}

internal data class WindowPackage(
    val id: Int,
    val layer: Int,
    val category: WindowCategory,
    val packageName: String?,
)

internal data class SupportedAppWindow(
    val id: Int,
    val packageName: String,
)

internal object SupportedAppWindowSelector {
    fun select(
        activePackageName: String?,
        windows: List<WindowPackage>,
        supportedPackages: Set<String>,
        assistantPackageName: String,
    ): SupportedAppWindow? {
        require(assistantPackageName.isNotBlank())

        val activeSupportedPackage = activePackageName
            ?.takeIf(supportedPackages::contains)
        val assistantOverlayOwnsActiveRoot = activePackageName == assistantPackageName
        if (activeSupportedPackage == null && !assistantOverlayOwnsActiveRoot) return null

        val supportedWindow = windows
            .asSequence()
            .filter { it.category == WindowCategory.APPLICATION }
            .filter { window ->
                if (activeSupportedPackage != null) {
                    window.packageName == activeSupportedPackage
                } else {
                    window.packageName in supportedPackages
                }
            }
            .maxByOrNull(WindowPackage::layer)
            ?: return null

        val windowsAboveSupportedApp = windows.filter { window ->
            window.id != supportedWindow.id && window.layer > supportedWindow.layer
        }
        if (windowsAboveSupportedApp.any { window ->
                !window.isAssistantAccessibilityOverlay(assistantPackageName)
            }
        ) {
            return null
        }
        if (
            assistantOverlayOwnsActiveRoot &&
            windowsAboveSupportedApp.none { window ->
                window.isAssistantAccessibilityOverlay(assistantPackageName)
            }
        ) {
            return null
        }

        return SupportedAppWindow(
            id = supportedWindow.id,
            packageName = requireNotNull(supportedWindow.packageName),
        )
    }

    private fun WindowPackage.isAssistantAccessibilityOverlay(
        assistantPackageName: String,
    ): Boolean =
        category == WindowCategory.ACCESSIBILITY_OVERLAY &&
            packageName == assistantPackageName
}
