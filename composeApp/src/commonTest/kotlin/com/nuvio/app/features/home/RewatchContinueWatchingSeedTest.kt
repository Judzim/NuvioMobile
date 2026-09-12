package com.nuvio.app.features.home

import com.nuvio.app.features.tracking.RewatchContinueWatchingSeed
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watching.domain.WatchingContentRef
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class RewatchContinueWatchingSeedTest {
    @Test
    fun `a fresh seed moves the series onto the rewatch run`() {
        val candidates = buildHomeNextUpSeedCandidates(
            progressEntries = listOf(canonicalEntry(season = 9, episode = 10, at = JULY)),
            watchedItems = emptyList<WatchedItem>(),
            providerOwnsCompletedHistory = false,
            preferFurthestEpisode = true,
            nowEpochMs = JULY,
            shouldUseProgressSeed = { _, _ -> true },
            rewatchContinueWatchingSeeds = listOf(seed(season = 1, episode = 1, at = SEPTEMBER)),
        )

        assertEquals(1, candidates.size)
        assertEquals("tt2861424", candidates.single().content.id)
        assertEquals(1, candidates.single().seasonNumber)
        assertEquals(1, candidates.single().episodeNumber)
        assertEquals(SEPTEMBER, candidates.single().markedAtEpochMs)
    }

    @Test
    fun `an older canonical position wins over a stale seed`() {
        val candidates = buildHomeNextUpSeedCandidates(
            progressEntries = listOf(canonicalEntry(season = 9, episode = 10, at = OCTOBER)),
            watchedItems = emptyList<WatchedItem>(),
            providerOwnsCompletedHistory = false,
            preferFurthestEpisode = true,
            nowEpochMs = OCTOBER,
            shouldUseProgressSeed = { _, _ -> true },
            rewatchContinueWatchingSeeds = listOf(seed(season = 1, episode = 1, at = SEPTEMBER)),
        )

        assertEquals(9, candidates.single().seasonNumber)
        assertEquals(10, candidates.single().episodeNumber)
        assertEquals(OCTOBER, candidates.single().markedAtEpochMs)
    }

    @Test
    fun `a seed for another series leaves this one alone`() {
        val candidates = buildHomeNextUpSeedCandidates(
            progressEntries = listOf(canonicalEntry(season = 9, episode = 10, at = JULY)),
            watchedItems = emptyList<WatchedItem>(),
            providerOwnsCompletedHistory = false,
            preferFurthestEpisode = true,
            nowEpochMs = JULY,
            shouldUseProgressSeed = { _, _ -> true },
            rewatchContinueWatchingSeeds = listOf(
                seed(season = 1, episode = 1, at = SEPTEMBER, contentId = "tt0903747", keys = listOf("imdb:tt0903747")),
            ),
        )

        assertEquals(9, candidates.single().seasonNumber)
        assertEquals(10, candidates.single().episodeNumber)
    }

    @Test
    fun `seeds are matched through any id form of the series`() {
        val candidates = applyRewatchContinueWatchingSeeds(
            candidates = listOf(candidate(id = "imdb:tt2861424", season = 9, episode = 10, at = JULY)),
            seeds = listOf(seed(season = 1, episode = 2, at = SEPTEMBER, contentId = "tt2861424")),
        )

        assertEquals(1, candidates.single().seasonNumber)
        assertEquals(2, candidates.single().episodeNumber)
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

    private fun seed(
        season: Int,
        episode: Int,
        at: Long,
        contentId: String = "tt2861424",
        keys: List<String> = listOf("imdb:tt2861424", "tmdb:60625"),
    ): RewatchContinueWatchingSeed = RewatchContinueWatchingSeed(
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
