package com.nuvio.app.features.simkl

import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimklRewatchPolicyTest {
    @Test
    fun `rewatch writes stay off until the user opts in`() {
        assertFalse(rewatchRequested(mode = SimklRewatchMode.OFF))
        assertFalse(rewatchRequested(mode = SimklRewatchMode.OFF, manualIntentArmed = true))
    }

    @Test
    fun `automatic records every finished playback`() {
        assertTrue(rewatchRequested(mode = SimklRewatchMode.AUTOMATIC, progressPercent = 80.0))
        assertTrue(rewatchRequested(mode = SimklRewatchMode.AUTOMATIC, progressPercent = 100.0))
    }

    @Test
    fun `automatic ignores scrobbles that mark nothing watched`() {
        assertFalse(rewatchRequested(mode = SimklRewatchMode.AUTOMATIC, progressPercent = 79.9))
        assertFalse(
            rewatchRequested(
                mode = SimklRewatchMode.AUTOMATIC,
                action = TrackingScrobbleAction.START,
                progressPercent = 100.0,
            ),
        )
        assertFalse(
            rewatchRequested(
                mode = SimklRewatchMode.AUTOMATIC,
                action = TrackingScrobbleAction.PAUSE,
                progressPercent = 100.0,
            ),
        )
    }

    @Test
    fun `manual records only the titles the user armed`() {
        assertFalse(rewatchRequested(mode = SimklRewatchMode.MANUAL))
        assertTrue(rewatchRequested(mode = SimklRewatchMode.MANUAL, manualIntentArmed = true))
    }

    @Test
    fun `free and unknown plans never send the flag`() {
        assertFalse(rewatchRequested(mode = SimklRewatchMode.AUTOMATIC, accountType = "free"))
        assertFalse(rewatchRequested(mode = SimklRewatchMode.MANUAL, accountType = "free", manualIntentArmed = true))
        assertFalse(rewatchRequested(mode = SimklRewatchMode.AUTOMATIC, accountType = null))
        assertFalse(rewatchRequested(mode = SimklRewatchMode.AUTOMATIC, accountType = "  "))
        assertTrue(rewatchRequested(mode = SimklRewatchMode.AUTOMATIC, accountType = "VIP"))
    }

    @Test
    fun `rewatch modes are gated by the plan`() {
        assertTrue(isSimklRewatchModeSelectable(SimklRewatchMode.OFF, accountType = "free"))
        assertTrue(isSimklRewatchModeSelectable(SimklRewatchMode.OFF, accountType = null))
        assertFalse(isSimklRewatchModeSelectable(SimklRewatchMode.MANUAL, accountType = "free"))
        assertFalse(isSimklRewatchModeSelectable(SimklRewatchMode.AUTOMATIC, accountType = "free"))
        assertTrue(isSimklRewatchModeSelectable(SimklRewatchMode.AUTOMATIC, accountType = "pro"))
    }

    @Test
    fun `stored mode falls back to off`() {
        assertEquals(SimklRewatchMode.OFF, SimklRewatchMode.fromStorage(null))
        assertEquals(SimklRewatchMode.OFF, SimklRewatchMode.fromStorage(""))
        assertEquals(SimklRewatchMode.OFF, SimklRewatchMode.fromStorage("legacy-value"))
        assertEquals(SimklRewatchMode.AUTOMATIC, SimklRewatchMode.fromStorage("automatic"))
        assertEquals(SimklRewatchMode.MANUAL, SimklRewatchMode.fromStorage("MANUAL"))
    }

    @Test
    fun `rewatch status covers the wider scrobble set`() {
        assertEquals(SimklRewatchStatus.ACTIVE, SimklRewatchStatus.fromWire("active"))
        assertEquals(SimklRewatchStatus.COMPLETED, SimklRewatchStatus.fromWire("completed"))
        assertEquals(SimklRewatchStatus.CLOSED, SimklRewatchStatus.fromWire("closed"))
        assertEquals(SimklRewatchStatus.FIRST_WATCH, SimklRewatchStatus.fromWire("first_watch"))
        assertEquals(SimklRewatchStatus.TOO_SOON, SimklRewatchStatus.fromWire("too_soon"))
        assertEquals(SimklRewatchStatus.NOT_ELIGIBLE, SimklRewatchStatus.fromWire("not_eligible"))
        assertEquals(SimklRewatchStatus.PRO_REQUIRED, SimklRewatchStatus.fromWire("pro_required"))
        assertEquals(SimklRewatchStatus.UNKNOWN, SimklRewatchStatus.fromWire("something_new"))
        assertNull(SimklRewatchStatus.fromWire(null))
        assertNull(SimklRewatchStatus.fromWire(""))
    }

    @Test
    fun `only saved sessions count as recorded rewatches`() {
        assertTrue(SimklRewatchStatus.ACTIVE.isRecorded)
        assertTrue(SimklRewatchStatus.COMPLETED.isRecorded)
        assertTrue(SimklRewatchStatus.CLOSED.isRecorded)
        assertFalse(SimklRewatchStatus.FIRST_WATCH.isRecorded)
        assertFalse(SimklRewatchStatus.TOO_SOON.isRecorded)
        assertFalse(SimklRewatchStatus.NOT_ELIGIBLE.isRecorded)
        assertFalse(SimklRewatchStatus.PRO_REQUIRED.isRecorded)
        assertFalse(SimklRewatchStatus.UNKNOWN.isRecorded)
    }

    @Test
    fun `stop response exposes the created session`() {
        val result = scrobbleResult(
            """{"action":"scrobble","progress":95,"rewatch_id":21284,"rewatch_status":"completed"}""",
        )

        assertEquals(SimklScrobbleOutcome.SCROBBLE, result.outcome)
        assertEquals(21284L, result.rewatchId)
        assertEquals(SimklRewatchStatus.COMPLETED, result.rewatchStatus)
    }

    @Test
    fun `a running session reports its id even when the watch was rejected`() {
        val result = scrobbleResult(
            """{"action":"scrobble","progress":95,"rewatch_id":21284,"rewatch_status":"too_soon"}""",
        )

        assertEquals(21284L, result.rewatchId)
        assertEquals(SimklRewatchStatus.TOO_SOON, result.rewatchStatus)
        assertFalse(result.rewatchStatus?.isRecorded == true)
    }

    @Test
    fun `scrobbles without the flag carry no rewatch fields`() {
        val result = scrobbleResult("""{"action":"pause","progress":42}""")

        assertEquals(SimklScrobbleOutcome.PAUSE, result.outcome)
        assertNull(result.rewatchId)
        assertNull(result.rewatchStatus)
    }

    @Test
    fun `the flag is only requested on stop`() {
        assertEquals(mapOf("allow_rewatch" to "yes"), SIMKL_ALLOW_REWATCH_QUERY)
        assertTrue(SIMKL_REWATCH_MIN_PROGRESS_PERCENT == 80.0)
    }

    private fun rewatchRequested(
        mode: SimklRewatchMode,
        accountType: String? = "pro",
        action: TrackingScrobbleAction = TrackingScrobbleAction.STOP,
        progressPercent: Double = 95.0,
        manualIntentArmed: Boolean = false,
    ): Boolean = shouldRecordSimklRewatch(
        mode = mode,
        accountType = accountType,
        action = action,
        progressPercent = progressPercent,
        manualIntentArmed = manualIntentArmed,
    )

    private fun scrobbleResult(body: String) = SimklApiResponse(
        status = 200,
        body = body,
        headers = emptyMap(),
    ).toSimklScrobbleResult(
        requestedAction = TrackingScrobbleAction.STOP,
        event = TrackingScrobbleEvent(media = movie(), progressPercent = 95.0),
        json = Json,
    )

    private fun movie() = TrackingMediaReference(
        kind = TrackingMediaKind.MOVIE,
        title = "Inception",
        year = 2010,
        ids = TrackingExternalIds(simkl = 472214, imdb = "tt1375666"),
    )
}
