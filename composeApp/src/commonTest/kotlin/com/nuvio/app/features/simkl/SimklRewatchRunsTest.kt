package com.nuvio.app.features.simkl

import com.nuvio.app.core.time.parseZonedIsoDateTimeToEpochMs
import com.nuvio.app.features.tracking.RewatchRunPosition
import com.nuvio.app.features.tracking.TrackingEpisode
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimklRewatchRunsTest {
    @Test
    fun `two episodes in a row make a run`() {
        val runs = runs(
            session(1 to "2026-09-01T20:00:00Z", 2 to "2026-09-02T20:00:00Z"),
        )

        assertEquals(1, runs.size)
        assertEquals("tt2861424", runs.single().contentId)
        assertEquals(1, runs.single().seasonNumber)
        assertEquals(2, runs.single().episodeNumber)
        assertEquals(parseZonedIsoDateTimeToEpochMs("2026-09-02T20:00:00Z"), runs.single().markedAtEpochMs)
    }

    @Test
    fun `a single rewatched episode is not a run`() {
        assertTrue(runs(session(4 to "2026-09-02T20:00:00Z")).isEmpty())
    }

    @Test
    fun `a skipped episode breaks the run`() {
        assertTrue(
            runs(session(1 to "2026-09-01T20:00:00Z", 3 to "2026-09-03T20:00:00Z")).isEmpty(),
        )
    }

    @Test
    fun `sessions of the same show join into one run`() {
        val runs = runs(
            session(1 to "2026-08-19T19:03:00Z", 2 to "2026-08-21T15:58:00Z"),
            session(3 to "2026-09-02T21:23:00Z"),
        )

        assertEquals(1, runs.size)
        assertEquals(3, runs.single().episodeNumber)
        assertEquals(parseZonedIsoDateTimeToEpochMs("2026-09-02T21:23:00Z"), runs.single().markedAtEpochMs)
    }

    @Test
    fun `a run stops at the end of a season`() {
        assertTrue(
            runs(
                session(10 to "2026-09-01T20:00:00Z", season = 1),
                session(1 to "2026-09-02T20:00:00Z", season = 2),
            ).isEmpty(),
        )
    }

    @Test
    fun `the canonical row is not a run`() {
        assertTrue(
            runs(
                session(
                    1 to "2026-09-01T20:00:00Z",
                    2 to "2026-09-02T20:00:00Z",
                    isRewatch = false,
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `a run carries every id form of its series`() {
        val run = runs(session(1 to "2026-09-01T20:00:00Z", 2 to "2026-09-02T20:00:00Z")).single()

        assertEquals(listOf("tt2861424", "imdb:tt2861424", "simkl:12345"), run.matchKeys)
        assertTrue(run.matches("tt2861424"))
        assertTrue(run.matches("imdb:tt2861424"))
        assertTrue(run.matches("simkl:12345"))
        assertFalse(run.matches("tt0903747"))
        assertFalse(run.matches(null))
    }

    @Test
    fun `the episode a confirmed rewatch landed on is found in the sessions`() {
        val sessions = listOf(session(1 to "2026-09-01T20:00:00Z", 2 to "2026-09-02T20:00:00Z"))

        assertTrue(sessions.holdsRewatchEpisode(reference(episode = 1)))
        assertTrue(sessions.holdsRewatchEpisode(reference(episode = 2)))
    }

    @Test
    fun `a single rewatched episode still answers for itself`() {
        val sessions = listOf(session(4 to "2026-09-02T20:00:00Z"))

        assertTrue(sessions.holdsRewatchEpisode(reference(episode = 4)))
    }

    @Test
    fun `an episode the sessions do not hold is not found`() {
        val sessions = listOf(session(1 to "2026-09-01T20:00:00Z", 2 to "2026-09-02T20:00:00Z"))

        assertFalse(sessions.holdsRewatchEpisode(reference(episode = 5)))
    }

    @Test
    fun `the sessions of another series do not answer for this one`() {
        val sessions = listOf(session(1 to "2026-09-01T20:00:00Z", 2 to "2026-09-02T20:00:00Z"))

        assertFalse(sessions.holdsRewatchEpisode(reference(episode = 1, imdb = "tt0903747", simkl = null)))
    }

    @Test
    fun `the canonical row does not answer for a rewatch`() {
        val sessions = listOf(session(1 to "2026-09-01T20:00:00Z", isRewatch = false))

        assertFalse(sessions.holdsRewatchEpisode(reference(episode = 1)))
    }

    private fun reference(
        episode: Int,
        imdb: String? = "tt2861424",
        simkl: Long? = 12345L,
    ): TrackingMediaReference = TrackingMediaReference(
        kind = TrackingMediaKind.SHOW,
        title = "Rick and Morty",
        ids = TrackingExternalIds(imdb = imdb, simkl = simkl),
        episode = TrackingEpisode(season = 1, number = episode),
    )

    private fun runs(vararg rows: SimklLibraryEntry): List<RewatchRunPosition> =
        deriveSimklRewatchRuns(
            entries = rows.toList(),
            animeIdPreference = SimklAnimeIdPreference.IMDB,
        )

    private fun session(
        vararg episodes: Pair<Int, String>,
        season: Int = 1,
        isRewatch: Boolean = true,
    ): SimklLibraryEntry = SimklLibraryEntry(
        mediaType = SimklMediaType.SHOWS,
        isRewatch = isRewatch,
        show = SimklMedia(
            title = "Rick and Morty",
            ids = mapOf(
                "imdb" to JsonPrimitive("tt2861424"),
                "simkl" to JsonPrimitive("12345"),
            ),
        ),
        seasons = listOf(
            SimklSeason(
                number = season,
                episodes = episodes.map { (number, watchedAt) ->
                    SimklEpisode(number = number, watchedAt = watchedAt)
                },
            ),
        ),
    )
}
