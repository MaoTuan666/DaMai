package com.ticketassistant.android.platform.api

import com.ticketassistant.android.accessibility.UiSnapshot
import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TicketPlatform
import com.ticketassistant.android.domain.TicketTask
import com.ticketassistant.android.runtime.EngineRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformAdapterRegistryTest {
    @Test
    fun `registry resolves by platform package and mode`() {
        val adapter = FakeAdapter(
            packages = setOf("cn.damai"),
            modes = RunMode.entries.toSet(),
        )
        val registry = PlatformAdapterRegistry(listOf(adapter))

        val resolution = registry.resolve(
            platform = TicketPlatform.DAMAI,
            packageName = "cn.damai",
            mode = RunMode.SALE_THEN_RETURN,
        )

        assertTrue(resolution is AdapterResolution.Found)
        assertSame(adapter, (resolution as AdapterResolution.Found).adapter)
        assertSame(adapter, registry.findByPackage("cn.damai"))
        assertEquals(setOf(TicketPlatform.DAMAI), registry.registeredPlatforms())
    }

    @Test
    fun `registry rejects duplicate platform keys`() {
        val first = FakeAdapter(setOf("cn.damai"), setOf(RunMode.SALE_ONLY))
        val second = FakeAdapter(setOf("com.example.damai"), setOf(RunMode.RETURN_ONLY))

        val result = runCatching {
            PlatformAdapterRegistry(listOf(first, second))
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `registry rejects invalid package declaration`() {
        val result = runCatching {
            PlatformAdapterRegistry(
                listOf(FakeAdapter(setOf("damai"), setOf(RunMode.SALE_ONLY))),
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `registry reports unsupported mode without returning adapter`() {
        val registry = PlatformAdapterRegistry(
            listOf(FakeAdapter(setOf("cn.damai"), setOf(RunMode.SALE_ONLY))),
        )

        val resolution = registry.resolve(
            TicketPlatform.DAMAI,
            "cn.damai",
            RunMode.RETURN_ONLY,
        )

        assertTrue(resolution is AdapterResolution.ModeNotSupported)
    }

    private class FakeAdapter(
        private val packages: Set<String>,
        private val modes: Set<RunMode>,
    ) : PlatformAdapter {
        override val platform = TicketPlatform.DAMAI
        override val supportedPackages = packages
        override val supportedModes = modes

        override fun detectPage(
            snapshot: UiSnapshot,
            task: TicketTask,
        ): PageResult = PageResult.Unknown(PageUnknownReason.INSUFFICIENT_EVIDENCE)

        override fun decideAction(
            page: PageResult.Recognized,
            task: TicketTask,
            runtime: EngineRuntimeState,
        ): ActionDecision = ActionDecision.Wait(WaitReason.PAGE_NOT_ACTIONABLE)
    }
}
