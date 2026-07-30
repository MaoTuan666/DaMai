package com.ticketassistant.android.runtime

import com.ticketassistant.android.domain.RunMode
import com.ticketassistant.android.domain.TaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStateMachineTest {
    @Test
    fun `return-only task is armed in return phase`() {
        val state = EngineRuntimeState.initial(RunMode.RETURN_ONLY)

        assertEquals(TaskState.ARMED, state.taskState)
        assertEquals(AutomationPhase.RETURN_MONITOR, state.phase)
    }

    @Test
    fun `manual start requires a fresh snapshot before recognition`() {
        var state = EngineRuntimeState.initial(RunMode.SALE_ONLY)
        state = applied(state, RuntimeEvent.SnapshotObserved(5))
        state = applied(state, RuntimeEvent.UserStart)

        assertTrue(
            RuntimeStateMachine.transition(
                state,
                RuntimeEvent.TargetPageRecognized(5),
            ) is StateTransition.Rejected,
        )

        state = applied(state, RuntimeEvent.SnapshotObserved(6))
        state = applied(state, RuntimeEvent.TargetPageRecognized(6))
        assertEquals(TaskState.RUNNING, state.taskState)
    }

    @Test
    fun `resume requires a snapshot newer than the paused page`() {
        var state = applied(
            EngineRuntimeState.initial(RunMode.SALE_ONLY),
            RuntimeEvent.UserStart,
        )
        state = applied(
            state,
            RuntimeEvent.TargetPageRecognized(10),
        )
        state = applied(state, RuntimeEvent.UserPause)
        state = applied(state, RuntimeEvent.UserResume)

        assertFalse(state.canScheduleAction(10))

        state = applied(state, RuntimeEvent.SnapshotObserved(10))
        assertFalse(state.canScheduleAction(10))

        state = applied(state, RuntimeEvent.SnapshotObserved(11))
        assertTrue(state.canScheduleAction(11))
        assertFalse(state.canScheduleAction(10))
    }

    @Test
    fun `safety pause cannot be resumed by user`() {
        val running = applied(
            applied(
                EngineRuntimeState.initial(RunMode.SALE_ONLY),
                RuntimeEvent.UserStart,
            ),
            RuntimeEvent.TargetPageRecognized(1),
        )
        val paused = applied(
            running,
            RuntimeEvent.SafetyPause(SafetyPauseReason.UNKNOWN_PAGE),
        )

        val result = RuntimeStateMachine.transition(paused, RuntimeEvent.UserResume)

        assertTrue(result is StateTransition.Rejected)
        assertEquals(TaskState.SAFETY_PAUSED, paused.taskState)
    }

    @Test
    fun `phase switch is accepted only while running`() {
        val waiting = applied(
            EngineRuntimeState.initial(RunMode.SALE_THEN_RETURN),
            RuntimeEvent.UserStart,
        )
        assertTrue(
            RuntimeStateMachine.transition(
                waiting,
                RuntimeEvent.SwitchPhase(AutomationPhase.RETURN_MONITOR),
            ) is StateTransition.Rejected,
        )

        val running = applied(waiting, RuntimeEvent.TargetPageRecognized(1))
        val switched = applied(
            running,
            RuntimeEvent.SwitchPhase(AutomationPhase.RETURN_MONITOR),
        )
        assertEquals(AutomationPhase.RETURN_MONITOR, switched.phase)
    }

    @Test
    fun `stopped task rejects further transitions`() {
        val stopped = applied(
            EngineRuntimeState.initial(RunMode.SALE_ONLY),
            RuntimeEvent.Stop(RuntimeStopReason.USER_REQUESTED),
        )

        val result = RuntimeStateMachine.transition(
            stopped,
            RuntimeEvent.TargetPageRecognized(2),
        )

        assertTrue(result is StateTransition.Rejected)
        assertEquals(RuntimeStopReason.USER_REQUESTED, stopped.stopReason)
    }

    private fun applied(
        state: EngineRuntimeState,
        event: RuntimeEvent,
    ): EngineRuntimeState =
        (RuntimeStateMachine.transition(state, event) as StateTransition.Applied).state
}
