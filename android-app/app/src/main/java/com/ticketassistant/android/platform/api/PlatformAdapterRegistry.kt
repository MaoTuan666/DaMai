package com.ticketassistant.android.platform.api

import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TicketPlatform

@JvmInline
value class AdapterKey(val platform: TicketPlatform)

sealed interface AdapterResolution {
    data class Found(val adapter: PlatformAdapter) : AdapterResolution
    data class AdapterNotRegistered(val key: AdapterKey) : AdapterResolution
    data class PackageNotSupported(
        val key: AdapterKey,
        val packageName: String,
    ) : AdapterResolution

    data class ModeNotSupported(
        val key: AdapterKey,
        val mode: RunMode,
    ) : AdapterResolution
}

class PlatformAdapterRegistry(
    adapters: Collection<PlatformAdapter>,
) {
    private val adaptersByKey: Map<AdapterKey, PlatformAdapter>
    private val adaptersByPackage: Map<String, PlatformAdapter>

    init {
        adapters.forEach(::validateAdapterDeclaration)

        val duplicatePlatforms = adapters
            .groupBy { it.platform }
            .filterValues { it.size > 1 }
            .keys
        require(duplicatePlatforms.isEmpty()) {
            "Only one Android adapter may be registered for each platform: $duplicatePlatforms"
        }

        val packageOwners = buildMap<String, MutableList<TicketPlatform>> {
            adapters.forEach { adapter ->
                adapter.supportedPackages.forEach { packageName ->
                    getOrPut(packageName, ::mutableListOf).add(adapter.platform)
                }
            }
        }
        val duplicatePackages = packageOwners.filterValues { it.size > 1 }
        require(duplicatePackages.isEmpty()) {
            "A foreground package may only belong to one Android adapter: ${duplicatePackages.keys}"
        }

        adaptersByKey = adapters.associateBy { AdapterKey(it.platform) }
        adaptersByPackage = adapters
            .flatMap { adapter ->
                adapter.supportedPackages.map { packageName -> packageName to adapter }
            }
            .toMap()
    }

    fun resolve(
        platform: TicketPlatform,
        packageName: String,
        mode: RunMode,
    ): AdapterResolution {
        val key = AdapterKey(platform)
        val adapter = adaptersByKey[key]
            ?: return AdapterResolution.AdapterNotRegistered(key)
        if (packageName !in adapter.supportedPackages) {
            return AdapterResolution.PackageNotSupported(key, packageName)
        }
        if (mode !in adapter.supportedModes) {
            return AdapterResolution.ModeNotSupported(key, mode)
        }
        return AdapterResolution.Found(adapter)
    }

    fun findByPackage(packageName: String): PlatformAdapter? =
        adaptersByPackage[packageName]

    fun findByPlatform(platform: TicketPlatform): PlatformAdapter? =
        adaptersByKey[AdapterKey(platform)]

    fun registeredPlatforms(): Set<TicketPlatform> =
        adaptersByKey.keys.mapTo(mutableSetOf(), AdapterKey::platform)

    private fun validateAdapterDeclaration(adapter: PlatformAdapter) {
        require(adapter.supportedPackages.isNotEmpty()) {
            "${adapter.platform} adapter must declare at least one Android package"
        }
        require(adapter.supportedPackages.all(::isValidPackageName)) {
            "${adapter.platform} adapter contains an invalid Android package"
        }
        require(adapter.supportedModes.isNotEmpty()) {
            "${adapter.platform} adapter must support at least one run mode"
        }
    }

    private fun isValidPackageName(value: String): Boolean =
        value.matches(PACKAGE_NAME_PATTERN)

    companion object {
        private val PACKAGE_NAME_PATTERN =
            Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        fun empty(): PlatformAdapterRegistry = PlatformAdapterRegistry(emptyList())
    }
}
