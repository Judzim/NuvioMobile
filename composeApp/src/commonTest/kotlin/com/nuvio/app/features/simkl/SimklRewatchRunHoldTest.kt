package com.nuvio.app.features.simkl

import com.nuvio.app.features.tracking.RewatchRunPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimklRewatchRunHoldTest {

    @Test
    fun `an optimistic run replaces the account run for the same item`() {
        val account = run(season = 3, episode = 4, markedAt = 1_000L)
        val optimistic = run(season = 3, episode = 5, markedAt = 2_000L)

        assertEquals(
            listOf(optimistic),
            mergeOptimisticRewatchRuns(listOf(account), mapOf("show" to optimistic)),
        )
    }

    @Test
    fun `an optimistic run for an item the account does not hold is added`() {
        val account = run(contentId = "other", season = 1, episode = 2)
        val optimistic = run(season = 3, episode = 5, markedAt = 2_000L)

        val merged = mergeOptimisticRewatchRuns(listOf(account), mapOf("show" to optimistic))

        assertEquals(setOf("show", "other"), merged.map(RewatchRunPosition::contentId).toSet())
        assertEquals(optimistic, merged.first())
    }

    @Test
    fun `no optimistic run leaves the account list alone`() {
        val account = run(season = 3, episode = 4)

        assertEquals(listOf(account), mergeOptimisticRewatchRuns(listOf(account), emptyMap()))
    }

    @Test
    fun `a run is compared by season then episode`() {
        assertTrue(compareRewatchRuns(run(season = 2, episode = 1), run(season = 1, episode = 10)) > 0)
        assertTrue(compareRewatchRuns(run(season = 1, episode = 9), run(season = 1, episode = 10)) < 0)
        assertEquals(0, compareRewatchRuns(run(season = 1, episode = 10), run(season = 1, episode = 10)))
    }

    @Test
    fun `the account catching up in another season releases the hold`() {
        val optimistic = run(season = 1, episode = 10, markedAt = 2_000L)

        assertTrue(accountCaughtUpWithOptimisticRun(listOf(run(season = 2, episode = 1)), optimistic))
        assertTrue(accountCaughtUpWithOptimisticRun(emptyList(), optimistic))
        assertFalse(accountCaughtUpWithOptimisticRun(listOf(run(season = 1, episode = 9)), optimistic))
    }

    @Test
    fun `the card seeded at the run position is the run card`() {
        val runs = listOf(run(season = 1, episode = 4, matchKeys = listOf("imdb:tt1")))

        assertTrue(isRewatchRunCard(runs, "show", seedSeason = 1, seedEpisode = 4))
        assertTrue(isRewatchRunCard(runs, "imdb:tt1", seedSeason = 1, seedEpisode = 4))
        assertFalse(isRewatchRunCard(runs, "show", seedSeason = 1, seedEpisode = 5))
        assertFalse(isRewatchRunCard(runs, "missing", seedSeason = 1, seedEpisode = 4))
    }

    private fun run(
        contentId: String = "show",
        season: Int,
        episode: Int,
        markedAt: Long = 1_000L,
        matchKeys: List<String> = emptyList(),
    ) = RewatchRunPosition(
        contentId = contentId,
        matchKeys = matchKeys,
        seasonNumber = season,
        episodeNumber = episode,
        markedAtEpochMs = markedAt,
    )
}
