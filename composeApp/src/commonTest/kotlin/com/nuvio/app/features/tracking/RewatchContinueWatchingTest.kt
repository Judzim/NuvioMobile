package com.nuvio.app.features.tracking

import com.nuvio.app.features.simkl.SimklRewatchMode
import com.nuvio.app.features.simkl.SimklRewatchStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewatchContinueWatchingTest {
    @Test
    fun `a show episode with a catalogue id seeds the run`() {
        val seed = buildRewatchContinueWatchingSeed(
            media = showMedia(season = 1, episode = 1),
            watchedAtEpochMs = 1_700_000_000_000,
        )

        assertEquals("tt2861424", seed?.contentId)
        assertEquals(1, seed?.seasonNumber)
        assertEquals(1, seed?.episodeNumber)
        assertEquals(1_700_000_000_000, seed?.markedAtEpochMs)
        assertTrue(seed?.matchKeys?.contains("imdb:tt2861424") == true)
        assertTrue(seed?.matchKeys?.contains("tmdb:60625") == true)
        assertTrue(seed?.matchKeys?.contains("simkl:34902") == true)
    }

    @Test
    fun `movies and episodes without coordinates cannot seed a run`() {
        val movie = showMedia(season = 1, episode = 1).copy(kind = TrackingMediaKind.MOVIE)
        assertNull(buildRewatchContinueWatchingSeed(movie, 1L))
        assertNull(buildRewatchContinueWatchingSeed(showMedia(season = null, episode = 3), 1L))
        assertNull(buildRewatchContinueWatchingSeed(showMedia(season = 1, episode = null), 1L))
        assertNull(buildRewatchContinueWatchingSeed(showMedia(season = 0, episode = 1), 1L))
    }

    @Test
    fun `a seed matches the series under every id the app knows`() {
        val seed = buildRewatchContinueWatchingSeed(showMedia(season = 2, episode = 1), 1L)!!

        assertTrue(seed.matches("tt2861424"))
        assertTrue(seed.matches("imdb:tt2861424"))
        assertTrue(seed.matches("tmdb:60625"))
        assertFalse(seed.matches("imdb:tt0903747"))
        assertFalse(seed.matches(" "))
    }

    @Test
    fun `automatic mode only follows runs that start a season`() {
        assertEquals(
            RewatchContinueWatchingAction.START,
            automaticAction(status = SimklRewatchStatus.ACTIVE, season = 1, episode = 1),
        )
        assertEquals(
            RewatchContinueWatchingAction.START,
            automaticAction(status = SimklRewatchStatus.ACTIVE, season = 4, episode = 1),
        )
        assertEquals(
            RewatchContinueWatchingAction.NONE,
            automaticAction(status = SimklRewatchStatus.ACTIVE, season = 1, episode = 5),
        )
    }

    @Test
    fun `automatic mode advances an existing run episode by episode`() {
        assertEquals(
            RewatchContinueWatchingAction.ADVANCE,
            automaticAction(
                status = SimklRewatchStatus.ACTIVE,
                season = 1,
                episode = 2,
                existing = 1 to 1,
            ),
        )
        assertEquals(
            RewatchContinueWatchingAction.ADVANCE,
            automaticAction(
                status = SimklRewatchStatus.ACTIVE,
                season = 2,
                episode = 1,
                existing = 1 to 10,
            ),
        )
    }

    @Test
    fun `automatic mode ignores jumps that are not part of the run`() {
        assertEquals(
            RewatchContinueWatchingAction.NONE,
            automaticAction(
                status = SimklRewatchStatus.ACTIVE,
                season = 5,
                episode = 3,
                existing = 1 to 1,
            ),
        )
        assertEquals(
            RewatchContinueWatchingAction.NONE,
            automaticAction(
                status = SimklRewatchStatus.ACTIVE,
                season = 1,
                episode = 1,
                existing = 1 to 4,
            ),
        )
    }

    @Test
    fun `only a stored rewatch moves continue watching`() {
        for (status in listOf(
            SimklRewatchStatus.TOO_SOON,
            SimklRewatchStatus.FIRST_WATCH,
            SimklRewatchStatus.NOT_ELIGIBLE,
            SimklRewatchStatus.PRO_REQUIRED,
            SimklRewatchStatus.UNKNOWN,
            null,
        )) {
            assertEquals(
                RewatchContinueWatchingAction.NONE,
                automaticAction(status = status, season = 1, episode = 1),
                "status $status must not start a run",
            )
        }
        for (status in listOf(
            SimklRewatchStatus.ACTIVE,
            SimklRewatchStatus.CLOSED,
            SimklRewatchStatus.COMPLETED,
        )) {
            assertEquals(
                RewatchContinueWatchingAction.START,
                automaticAction(status = status, season = 1, episode = 1),
                "status $status stores the rewatch",
            )
        }
    }

    @Test
    fun `manual and disabled modes never decide by themselves`() {
        assertEquals(
            RewatchContinueWatchingAction.NONE,
            rewatchContinueWatchingAction(
                mode = SimklRewatchMode.MANUAL,
                rewatchStatus = SimklRewatchStatus.ACTIVE,
                seasonNumber = 1,
                episodeNumber = 1,
                hasExistingSeed = false,
            ),
        )
        assertEquals(
            RewatchContinueWatchingAction.NONE,
            rewatchContinueWatchingAction(
                mode = SimklRewatchMode.OFF,
                rewatchStatus = SimklRewatchStatus.ACTIVE,
                seasonNumber = 1,
                episodeNumber = 1,
                hasExistingSeed = false,
            ),
        )
    }

    private fun automaticAction(
        status: SimklRewatchStatus?,
        season: Int,
        episode: Int,
        existing: Pair<Int, Int>? = null,
    ): RewatchContinueWatchingAction = rewatchContinueWatchingAction(
        mode = SimklRewatchMode.AUTOMATIC,
        rewatchStatus = status,
        seasonNumber = season,
        episodeNumber = episode,
        hasExistingSeed = existing != null,
        existingSeedSeason = existing?.first,
        existingSeedEpisode = existing?.second,
    )

    private fun showMedia(season: Int?, episode: Int?): TrackingMediaReference = TrackingMediaReference(
        kind = TrackingMediaKind.SHOW,
        title = "Rick and Morty",
        year = 2013,
        ids = TrackingExternalIds(
            imdb = "tt2861424",
            tmdb = 60_625,
            tvdb = "275274",
            simkl = 34_902,
        ),
        episode = episode?.let { number -> TrackingEpisode(season = season, number = number) },
        catalog = TrackingCatalogReference(
            contentId = if (season == null && episode == null) "" else "tt2861424",
            contentType = "series",
            videoId = null,
        ),
    )
}
