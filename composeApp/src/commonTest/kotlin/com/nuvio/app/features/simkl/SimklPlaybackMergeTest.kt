package com.nuvio.app.features.simkl

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SimklPlaybackMergeTest {
    @Test
    fun `a pause the app just recorded survives a fetch that has not caught up`() {
        val fetched = listOf(playback(simklId = "1", progress = 88.0, pausedAt = "2026-09-14T09:00:00Z"))
        val held = listOf(playback(simklId = "1", progress = 83.0, pausedAt = "2026-09-14T11:00:00Z"))

        val merged = mergeFetchedPlayback(fetched = fetched, held = held)

        assertEquals(83.0, merged.single().progress)
    }

    @Test
    fun `a fetch from another device wins once it is newer`() {
        val fetched = listOf(playback(simklId = "1", progress = 12.0, pausedAt = "2026-09-14T12:00:00Z"))
        val held = listOf(playback(simklId = "1", progress = 83.0, pausedAt = "2026-09-14T11:00:00Z"))

        val merged = mergeFetchedPlayback(fetched = fetched, held = held)

        assertEquals(12.0, merged.single().progress)
    }

    @Test
    fun `another episode of the same show is another playback`() {
        val fetched = listOf(playback(simklId = "1", episode = 4, pausedAt = "2026-09-14T09:00:00Z"))
        val held = listOf(playback(simklId = "1", episode = 3, pausedAt = "2026-09-14T11:00:00Z"))

        val merged = mergeFetchedPlayback(fetched = fetched, held = held)

        assertEquals(listOf(4), merged.map { session -> session.episode?.number })
    }

    @Test
    fun `a playback the account dropped is not resurrected`() {
        val held = listOf(playback(simklId = "1", pausedAt = "2026-09-14T11:00:00Z"))

        val merged = mergeFetchedPlayback(fetched = emptyList(), held = held)

        assertEquals(emptyList<SimklPlaybackSession>(), merged)
    }

    @Test
    fun `a fetch without held playbacks is passed through`() {
        val fetched = listOf(
            playback(simklId = "1", pausedAt = "2026-09-14T09:00:00Z"),
            playback(simklId = "2", pausedAt = "2026-09-14T08:00:00Z"),
        )

        assertEquals(fetched, mergeFetchedPlayback(fetched = fetched, held = emptyList()))
    }

    private fun playback(
        simklId: String,
        progress: Double = 50.0,
        pausedAt: String? = null,
        episode: Int? = null,
    ) = SimklPlaybackSession(
        progress = progress,
        pausedAt = pausedAt,
        type = if (episode == null) "movie" else "episode",
        episode = episode?.let { number -> SimklPlaybackEpisode(season = 1, number = number) },
        movie = if (episode == null) media(simklId) else null,
        show = if (episode == null) null else media(simklId),
    )

    private fun media(simklId: String) = SimklMedia(
        title = "A show",
        ids = mapOf("simkl" to JsonPrimitive(simklId.toLong())),
    )
}
