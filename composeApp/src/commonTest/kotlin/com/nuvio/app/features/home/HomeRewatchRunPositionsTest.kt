package com.nuvio.app.features.home

import com.nuvio.app.features.tracking.RewatchRunPosition
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watching.domain.WatchingContentRef
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRewatchRunPositionsTest {
    @Test
    fun `a fresh run moves the series onto the run`() {
        val candidates = buildHomeNextUpSeedCandidates(
            progressEntries = listOf(canonicalEntry(season = 9, episode = 10, at = JULY)),
            watchedItems = emptyList<WatchedItem>(),
            providerOwnsCompletedHistory = false,
            preferFurthestEpisode = true,
            nowEpochMs = JULY,
            shouldUseProgressSeed = { _, _ -> true },
            simklRewatchRuns = listOf(run(season = 1, episode = 1, at = SEPTEMBER)),
        )

        assertEquals(1, candidates.size)
        assertEquals("tt2861424", candidates.single().content.id)
        assertEquals(1, candidates.single().seasonNumber)
        assertEquals(1, candidates.single().episodeNumber)
        assertEquals(SEPTEMBER, candidates.single().markedAtEpochMs)
    }

    @Test
    fun `an older canonical position wins over a stale run`() {
        val candidates = buildHomeNextUpSeedCandidates(
            progressEntries = listOf(canonicalEntry(season = 9, episode = 10, at = OCTOBER)),
            watchedItems = emptyList<WatchedItem>(),
            providerOwnsCompletedHistory = false,
            preferFurthestEpisode = true,
            nowEpochMs = OCTOBER,
            shouldUseProgressSeed = { _, _ -> true },
            simklRewatchRuns = listOf(run(season = 1, episode = 1, at = SEPTEMBER)),
        )

        assertEquals(9, candidates.single().seasonNumber)
        assertEquals(10, candidates.single().episodeNumber)
        assertEquals(OCTOBER, candidates.single().markedAtEpochMs)
    }

    @Test
    fun `a run for another series leaves this one alone`() {
        val candidates = buildHomeNextUpSeedCandidates(
            progressEntries = listOf(canonicalEntry(season = 9, episode = 10, at = JULY)),
            watchedItems = emptyList<WatchedItem>(),
            providerOwnsCompletedHistory = false,
            preferFurthestEpisode = true,
            nowEpochMs = JULY,
            shouldUseProgressSeed = { _, _ -> true },
            simklRewatchRuns = listOf(
                run(season = 1, episode = 1, at = SEPTEMBER, contentId = "tt0903747", keys = listOf("imdb:tt0903747")),
            ),
        )

        val untouched = candidates.single { candidate -> candidate.content.id == "tt2861424" }
        assertEquals(9, untouched.seasonNumber)
        assertEquals(10, untouched.episodeNumber)
        assertEquals(JULY, untouched.markedAtEpochMs)

        // The other series has no canonical history here, so the run is the only reason it shows.
        val followed = candidates.single { candidate -> candidate.content.id == "tt0903747" }
        assertEquals(1, followed.seasonNumber)
        assertEquals(1, followed.episodeNumber)
        assertEquals(SEPTEMBER, followed.markedAtEpochMs)
    }

    @Test
    fun `runs are matched through any id form of the series`() {
        val candidates = applyRewatchRunPositions(
            candidates = listOf(candidate(id = "imdb:tt2861424", season = 9, episode = 10, at = JULY)),
            runs = listOf(run(season = 1, episode = 2, at = SEPTEMBER, contentId = "tt2861424")),
        )

        assertEquals(1, candidates.single().seasonNumber)
        assertEquals(2, candidates.single().episodeNumber)
    }

    @Test
    fun `a run brings a series into the row when the canonical history has nothing`() {
        val candidates = buildHomeNextUpSeedCandidates(
            progressEntries = emptyList(),
            watchedItems = emptyList<WatchedItem>(),
            providerOwnsCompletedHistory = true,
            preferFurthestEpisode = true,
            nowEpochMs = SEPTEMBER,
            shouldUseProgressSeed = { _, _ -> true },
            simklRewatchRuns = listOf(run(season = 1, episode = 1, at = SEPTEMBER)),
        )

        assertEquals(1, candidates.size)
        assertEquals("tt2861424", candidates.single().content.id)
        assertEquals("series", candidates.single().content.type)
        assertEquals(1, candidates.single().seasonNumber)
        assertEquals(1, candidates.single().episodeNumber)
        assertEquals(SEPTEMBER, candidates.single().markedAtEpochMs)
    }

    private fun candidate(
        id: String,
        season: Int,
        episode: Int,
        at: Long,
    ): CompletedSeriesCandidate = CompletedSeriesCandidate(
        content = WatchingContentRef(type = "series", id = id),
        seasonNumber = season,
        episodeNumber = episode,
        markedAtEpochMs = at,
    )

    private fun run(
        season: Int,
        episode: Int,
        at: Long,
        contentId: String = "tt2861424",
        keys: List<String> = listOf("imdb:tt2861424", "tmdb:60625"),
    ): RewatchRunPosition = RewatchRunPosition(
        contentId = contentId,
        matchKeys = keys,
        seasonNumber = season,
        episodeNumber = episode,
        markedAtEpochMs = at,
    )

    private fun canonicalEntry(season: Int, episode: Int, at: Long): WatchProgressEntry = WatchProgressEntry(
        contentType = "series",
        parentMetaId = "tt2861424",
        parentMetaType = "series",
        videoId = "tt2861424:$season:$episode",
        title = "Rick and Morty",
        seasonNumber = season,
        episodeNumber = episode,
        lastPositionMs = 1_200_000,
        durationMs = 1_200_000,
        lastUpdatedEpochMs = at,
        isCompleted = true,
        progressPercent = 100f,
    )

    private companion object {
        const val JULY = 1_780_000_000_000L
        const val SEPTEMBER = 1_788_000_000_000L
        const val OCTOBER = 1_790_000_000_000L
    }
}
