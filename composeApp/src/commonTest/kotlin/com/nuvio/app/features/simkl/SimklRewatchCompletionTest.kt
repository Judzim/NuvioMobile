package com.nuvio.app.features.simkl

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a playback counts as finished for Simkl, once IntroDB knows where the credits start.
 */
class SimklRewatchCompletionTest {

    @Test
    fun `without a marker the user threshold decides`() {
        assertEquals(95.0, resolvedSimklCompletionPercent(95.0, null), 0.0001)
    }

    @Test
    fun `a marker after the user threshold does not move it`() {
        assertEquals(90.0, resolvedSimklCompletionPercent(90.0, 96.5), 0.0001)
    }

    @Test
    fun `the credits end a playback earlier than the user threshold would`() {
        // The user asked for 95 percent, the credits start at 88: the content is over at 88.
        assertEquals(88.0, resolvedSimklCompletionPercent(95.0, 88.0), 0.0001)
    }

    @Test
    fun `a marker under what Simkl needs is ignored`() {
        // Simkl records a watch from 80 percent up, so a segment below that cannot lower the bar. This
        // is the guard that keeps a wrong or early marker from forcing a number Simkl would not believe.
        assertEquals(95.0, resolvedSimklCompletionPercent(95.0, 62.0), 0.0001)
        assertEquals(95.0, resolvedSimklCompletionPercent(95.0, 79.99), 0.0001)
    }

    @Test
    fun `a threshold under Simkl's bar is raised to it`() {
        assertEquals(80.0, resolvedSimklCompletionPercent(70.0, null), 0.0001)
        assertEquals(80.0, resolvedSimklCompletionPercent(70.0, 60.0), 0.0001)
    }

    @Test
    fun `nonsense values fall back to the threshold`() {
        assertEquals(90.0, resolvedSimklCompletionPercent(90.0, Double.NaN), 0.0001)
        assertEquals(80.0, resolvedSimklCompletionPercent(Double.NaN, null), 0.0001)
    }
}
