package com.rahul.assignmentrahul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionDeduplicatorTest {

    private val cooldown = 60_000L
    private val deduplicator = TransitionDeduplicator(exitCooldownMillis = cooldown)

    @Test
    fun `enter is recorded but never reported`() {
        val decision = deduplicator.decide(previous = null, TransitionType.ENTER, nowMillis = 0)

        assertFalse(decision.shouldReport)
        assertEquals(TransitionType.ENTER, decision.newState.lastTransition)
    }

    @Test
    fun `exit after enter is reported`() {
        val inside = TransitionState(TransitionType.ENTER, lastReportedExitAt = null)

        val decision = deduplicator.decide(inside, TransitionType.EXIT, nowMillis = 1_000)

        assertTrue(decision.shouldReport)
        assertEquals(TransitionState(TransitionType.EXIT, 1_000), decision.newState)
    }

    @Test
    fun `repeated exit is a duplicate`() {
        val outside = TransitionState(TransitionType.EXIT, lastReportedExitAt = 1_000)

        val decision = deduplicator.decide(outside, TransitionType.EXIT, nowMillis = 500_000)

        assertFalse(decision.shouldReport)
        assertEquals(outside, decision.newState)
    }

    @Test
    fun `exit within cooldown after re-entering is suppressed`() {
        val reEntered = TransitionState(TransitionType.ENTER, lastReportedExitAt = 1_000)

        val decision = deduplicator.decide(reEntered, TransitionType.EXIT, nowMillis = 1_000 + cooldown - 1)

        assertFalse(decision.shouldReport)
        // State still moves to EXIT so the next ENTER/EXIT pair is evaluated correctly.
        assertEquals(TransitionState(TransitionType.EXIT, 1_000), decision.newState)
    }

    @Test
    fun `exit after cooldown is reported again`() {
        val reEntered = TransitionState(TransitionType.ENTER, lastReportedExitAt = 1_000)

        val decision = deduplicator.decide(reEntered, TransitionType.EXIT, nowMillis = 1_000 + cooldown)

        assertTrue(decision.shouldReport)
        assertEquals(1_000 + cooldown, decision.newState.lastReportedExitAt)
    }

    @Test
    fun `exit with no known state is reported`() {
        val decision = deduplicator.decide(previous = null, TransitionType.EXIT, nowMillis = 5)

        assertTrue(decision.shouldReport)
    }
}
