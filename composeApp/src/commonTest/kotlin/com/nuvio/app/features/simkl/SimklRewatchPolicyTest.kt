package com.nuvio.app.features.simkl

import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimklRewatchPolicyTest {
    @Test
    fun `rewatch writes stay off until the user opts in`() {
        assertFalse(stopFlagRequested(mode = SimklRewatchMode.OFF))
        assertFalse(promptRequested(mode = SimklRewatchMode.OFF))
    }

    @Test
    fun `automatic records every finished playback`() {
        assertTrue(stopFlagRequested(mode = SimklRewatchMode.AUTOMATIC, progressPercent = 80.0))
        assertTrue(stopFlagRequested(mode = SimklRewatchMode.AUTOMATIC, progressPercent = 100.0))
    }

    @Test
    fun `automatic ignores scrobbles that mark nothing watched`() {
        assertFalse(stopFlagRequested(mode = SimklRewatchMode.AUTOMATIC, progressPercent = 79.9))
        assertFalse(
            stopFlagRequested(
                mode = SimklRewatchMode.AUTOMATIC,
                action = TrackingScrobbleAction.START,
                progressPercent = 100.0,
            ),
        )
        assertFalse(
            stopFlagRequested(
                mode = SimklRewatchMode.AUTOMATIC,
                action = TrackingScrobbleAction.PAUSE,
                progressPercent = 100.0,
            ),
        )
    }

    @Test
    fun `a playback below the chosen threshold is not a finished watch`() {
        assertFalse(
            stopFlagRequested(
                mode = SimklRewatchMode.AUTOMATIC,
                progressPercent = 85.0,
                completionThresholdPercent = 90.0,
            ),
        )
        assertTrue(
            stopFlagRequested(
                mode = SimklRewatchMode.AUTOMATIC,
                progressPercent = 92.0,
                completionThresholdPercent = 90.0,
            ),
        )
        assertFalse(
            promptRequested(
                mode = SimklRewatchMode.MANUAL,
                progressPercent = 85.0,
                completionThresholdPercent = 90.0,
            ),
        )
    }

    @Test
    fun `the threshold can never start below Simkl's own mark`() {
        assertEquals(80, coerceSimklWatchedThresholdPercent(40))
        assertEquals(88, coerceSimklWatchedThresholdPercent(88))
        assertEquals(95, coerceSimklWatchedThresholdPercent(120))
        assertEquals(80, SIMKL_WATCHED_THRESHOLD_DEFAULT_PERCENT)
    }

    @Test
    fun `manual never flags the scrobble itself`() {
        assertFalse(stopFlagRequested(mode = SimklRewatchMode.MANUAL, progressPercent = 100.0))
    }

    @Test
    fun `manual asks after a finished repeat viewing`() {
        assertTrue(promptRequested(mode = SimklRewatchMode.MANUAL))
    }

    @Test
    fun `the question is not asked when no rewatch could be recorded`() {
        assertFalse(promptRequested(mode = SimklRewatchMode.MANUAL, progressPercent = 79.9))
        assertFalse(
            promptRequested(
                mode = SimklRewatchMode.MANUAL,
                action = TrackingScrobbleAction.START,
                progressPercent = 100.0,
            ),
        )
        assertFalse(
            promptRequested(
                mode = SimklRewatchMode.MANUAL,
                action = TrackingScrobbleAction.PAUSE,
                progressPercent = 100.0,
            ),
        )
        assertFalse(promptRequested(mode = SimklRewatchMode.MANUAL, outcome = SimklScrobbleOutcome.PAUSE))
        assertFalse(promptRequested(mode = SimklRewatchMode.MANUAL, watched = false))
        assertFalse(promptRequested(mode = SimklRewatchMode.AUTOMATIC))
        assertFalse(promptRequested(mode = SimklRewatchMode.MANUAL, accountType = "free"))
        assertFalse(promptRequested(mode = SimklRewatchMode.MANUAL, accountType = null))
    }

    @Test
    fun `a watch from the last two days is not offered again`() {
        val twelveHoursAgo = NOW_EPOCH_MS - 12L * 60L * 60L * 1_000L
        val threeDaysAgo = NOW_EPOCH_MS - 3L * 24L * 60L * 60L * 1_000L

        assertFalse(promptRequested(mode = SimklRewatchMode.MANUAL, watchedAtEpochMs = twelveHoursAgo))
        assertTrue(promptRequested(mode = SimklRewatchMode.MANUAL, watchedAtEpochMs = threeDaysAgo))
    }

    @Test
    fun `an unknown watch date still asks`() {
        // Movies without a usable canonical timestamp, and the 1970 placeholder, must not hide the
        // question: Simkl decides afterwards whether the session can be created.
        assertTrue(promptRequested(mode = SimklRewatchMode.MANUAL, watchedAtEpochMs = null))
        assertTrue(promptRequested(mode = SimklRewatchMode.MANUAL, watchedAtEpochMs = 1_000L))
    }

    @Test
    fun `rewatch modes are gated by the plan`() {
        assertTrue(isSimklRewatchModeSelectable(SimklRewatchMode.OFF, accountType = "free"))
        assertTrue(isSimklRewatchModeSelectable(SimklRewatchMode.OFF, accountType = null))
        assertFalse(isSimklRewatchModeSelectable(SimklRewatchMode.MANUAL, accountType = "free"))
        assertFalse(isSimklRewatchModeSelectable(SimklRewatchMode.AUTOMATIC, accountType = "free"))
        assertTrue(isSimklRewatchModeSelectable(SimklRewatchMode.AUTOMATIC, accountType = "vip"))
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
    }

    @Test
    fun `scrobbles without the flag carry no rewatch fields`() {
        val result = scrobbleResult("""{"action":"pause","progress":42}""")

        assertEquals(SimklScrobbleOutcome.PAUSE, result.outcome)
        assertNull(result.rewatchId)
        assertNull(result.rewatchStatus)
    }

    @Test
    fun `a confirmed rewatch is written as a rewatch on the history endpoint`() {
        val body = Json.parseToJsonElement(
            buildSimklHistoryMutationBody(
                items = listOf(
                    TrackingHistoryItem(movie(), watchedAtEpochMs = 1_700_000_000_000L),
                ),
                isRewatch = true,
            ),
        ).jsonObject
        val item = body.getValue("movies").jsonArray.single().jsonObject

        assertEquals(true, item.getValue("is_rewatch").jsonPrimitive.content.toBoolean())
        assertEquals("2023-11-14T22:13:20Z", item.getValue("watched_at").jsonPrimitive.content)
    }

    @Test
    fun `ordinary history writes stay untouched`() {
        val body = Json.parseToJsonElement(
            buildSimklHistoryMutationBody(
                items = listOf(TrackingHistoryItem(movie(), watchedAtEpochMs = 1_700_000_000_000L)),
            ),
        ).jsonObject
        val item = body.getValue("movies").jsonArray.single().jsonObject

        assertNull(item["is_rewatch"])
    }

    @Test
    fun `prior watch reads the canonical history of the scrobbled episode`() {
        val snapshot = SimklSyncSnapshot(
            entries = listOf(
                SimklLibraryEntry(
                    mediaType = SimklMediaType.SHOWS,
                    status = SimklListStatus.WATCHING,
                    show = SimklMedia(title = "Breaking Bad", ids = mapOf("simkl" to Json.parseToJsonElement("1234"))),
                    seasons = listOf(
                        SimklSeason(
                            number = 1,
                            episodes = listOf(
                                SimklEpisode(number = 1, watchedAt = "2026-08-01T20:00:00Z"),
                                SimklEpisode(number = 2, watchedAt = null),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val watched = snapshot.priorWatchForScrobble(seriesResult(episodeNumber = 1))
        assertTrue(watched.wasWatched)
        assertEquals(parseSimklUtcEpochMs("2026-08-01T20:00:00Z"), watched.watchedAtEpochMs)

        assertFalse(snapshot.priorWatchForScrobble(seriesResult(episodeNumber = 2)).wasWatched)
        assertFalse(snapshot.priorWatchForScrobble(seriesResult(episodeNumber = 7)).wasWatched)
    }

    @Test
    fun `prior watch ignores movies that are only planned`() {
        val planned = SimklSyncSnapshot(
            entries = listOf(
                SimklLibraryEntry(
                    mediaType = SimklMediaType.MOVIES,
                    status = SimklListStatus.PLAN_TO_WATCH,
                    movie = SimklMedia(title = "Inception", ids = mapOf("simkl" to Json.parseToJsonElement("472214"))),
                ),
            ),
        )
        val watchedMovie = SimklSyncSnapshot(
            entries = listOf(
                SimklLibraryEntry(
                    mediaType = SimklMediaType.MOVIES,
                    status = SimklListStatus.COMPLETED,
                    lastWatchedAt = "2026-07-01T20:00:00Z",
                    movie = SimklMedia(title = "Inception", ids = mapOf("simkl" to Json.parseToJsonElement("472214"))),
                ),
            ),
        )

        assertFalse(planned.priorWatchForScrobble(movieResult()).wasWatched)
        val watched = watchedMovie.priorWatchForScrobble(movieResult())
        assertTrue(watched.wasWatched)
        assertEquals(parseSimklUtcEpochMs("2026-07-01T20:00:00Z"), watched.watchedAtEpochMs)
    }

    private fun stopFlagRequested(
        mode: SimklRewatchMode,
        accountType: String? = "pro",
        action: TrackingScrobbleAction = TrackingScrobbleAction.STOP,
        progressPercent: Double = 95.0,
        completionThresholdPercent: Double = SIMKL_REWATCH_MIN_PROGRESS_PERCENT,
    ): Boolean = shouldRecordSimklRewatchOnStop(
        mode = mode,
        accountType = accountType,
        action = action,
        progressPercent = progressPercent,
        completionThresholdPercent = completionThresholdPercent,
    )

    private fun promptRequested(
        mode: SimklRewatchMode,
        accountType: String? = "pro",
        action: TrackingScrobbleAction = TrackingScrobbleAction.STOP,
        outcome: SimklScrobbleOutcome = SimklScrobbleOutcome.SCROBBLE,
        progressPercent: Double = 95.0,
        watched: Boolean = true,
        watchedAtEpochMs: Long? = null,
        completionThresholdPercent: Double = SIMKL_REWATCH_MIN_PROGRESS_PERCENT,
    ): Boolean = shouldPromptSimklRewatch(
        mode = mode,
        accountType = accountType,
        action = action,
        outcome = outcome,
        progressPercent = progressPercent,
        priorWatch = SimklPriorWatch(wasWatched = watched, watchedAtEpochMs = watchedAtEpochMs),
        nowEpochMs = NOW_EPOCH_MS,
        completionThresholdPercent = completionThresholdPercent,
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

    private fun seriesResult(episodeNumber: Int) = SimklScrobbleResult(
        outcome = SimklScrobbleOutcome.SCROBBLE,
        playbackId = null,
        progress = 95.0,
        mediaType = SimklMediaType.SHOWS,
        media = SimklMedia(title = "Breaking Bad", ids = mapOf("simkl" to Json.parseToJsonElement("1234"))),
        episode = SimklPlaybackEpisode(season = 1, number = episodeNumber),
    )

    private fun movieResult() = SimklScrobbleResult(
        outcome = SimklScrobbleOutcome.SCROBBLE,
        playbackId = null,
        progress = 95.0,
        mediaType = SimklMediaType.MOVIES,
        media = SimklMedia(title = "Inception", ids = mapOf("simkl" to Json.parseToJsonElement("472214"))),
        episode = null,
    )

    private fun movie() = TrackingMediaReference(
        kind = TrackingMediaKind.MOVIE,
        title = "Inception",
        year = 2010,
        ids = TrackingExternalIds(simkl = 472214, imdb = "tt1375666"),
    )

    private companion object {
        const val NOW_EPOCH_MS = 1_790_000_000_000L
    }
}
