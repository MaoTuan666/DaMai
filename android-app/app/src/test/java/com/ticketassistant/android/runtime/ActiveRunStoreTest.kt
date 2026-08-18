package com.ticketassistant.android.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRunStoreTest {
    @After
    fun tearDown() {
        ActiveRunStore.disarm()
    }

    @Test
    fun `arm stores the task identity and run token`() {
        ActiveRunStore.arm(
            runId = "run-new",
            taskId = 42,
            startedAtEpochMillis = 123,
        )

        val session = requireNotNull(ActiveRunStore.session.value)
        assertEquals("run-new", session.runId)
        assertEquals(42L, session.taskId)
        assertEquals(123L, session.startedAtEpochMillis)
        assertTrue(ActiveRunStore.isActive("run-new"))
    }

    @Test
    fun `old run cannot disarm the current run`() {
        ActiveRunStore.arm("run-current", 7, 123)

        ActiveRunStore.disarm("run-old")

        assertTrue(ActiveRunStore.isActive("run-current"))
        assertFalse(ActiveRunStore.isActive("run-old"))
    }

    @Test
    fun `snapshot publication and run teardown are serialized`() {
        ActiveRunStore.arm("run-current", 7, 123)
        val publicationEntered = CountDownLatch(1)
        val allowPublicationToFinish = CountDownLatch(1)
        val teardownFinished = CountDownLatch(1)
        val publicationResult = AtomicReference<String?>()

        val publisher = thread {
            publicationResult.set(
                ActiveRunStore.withActiveRun("run-current") {
                    publicationEntered.countDown()
                    assertTrue(allowPublicationToFinish.await(2, TimeUnit.SECONDS))
                    "published"
                },
            )
        }
        assertTrue(publicationEntered.await(2, TimeUnit.SECONDS))

        val teardown = thread {
            ActiveRunStore.disarm("run-current")
            teardownFinished.countDown()
        }
        assertFalse(teardownFinished.await(100, TimeUnit.MILLISECONDS))

        allowPublicationToFinish.countDown()
        publisher.join(2_000)
        teardown.join(2_000)

        assertEquals("published", publicationResult.get())
        assertTrue(teardownFinished.await(100, TimeUnit.MILLISECONDS))
        assertNull(ActiveRunStore.withActiveRun("run-current") { "late publication" })
    }
}
