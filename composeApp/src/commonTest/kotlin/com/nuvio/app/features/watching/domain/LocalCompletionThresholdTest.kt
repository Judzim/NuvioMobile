package com.nuvio.app.features.watching.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalCompletionThresholdTest {

    @Test
    fun `a source without a bar keeps the app's own`() {
        assertNull(localCompletionThresholdFor(null))
        assertNull(localCompletionThresholdFor(Double.NaN))
    }

    @Test
    fun `a source bar at or under the app's own keeps the app's own`() {
        assertNull(localCompletionThresholdFor(80.0))
        assertNull(localCompletionThresholdFor(90.0))
    }

    @Test
    fun `a higher source bar raises the local one, never past the whole scale`() {
        assertEquals(0.96f, localCompletionThresholdFor(96.0)!!, 0.0001f)
        assertEquals(1f, localCompletionThresholdFor(100.0)!!, 0f)
        assertEquals(1f, localCompletionThresholdFor(140.0)!!, 0f)
    }
}
