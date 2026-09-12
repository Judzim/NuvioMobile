package com.nuvio.app.features.simkl

import com.nuvio.app.core.time.parseZonedIsoDateTimeToEpochMs
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

    private fun runs(vararg rows: SimklLibraryEntry): List<SimklRewatchRun> =
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
