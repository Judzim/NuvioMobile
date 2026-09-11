package com.nuvio.app.features.tracking

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewatchIntentRepositoryTest {
    @AfterTest
    fun tearDown() {
        RewatchIntentRepository.clear()
    }

    @Test
    fun `arming a title matches the catalog id the player reports`() {
        RewatchIntentRepository.armContent("tt0903747")

        assertTrue(RewatchIntentRepository.isArmed(show(episode = TrackingEpisode(season = 1, number = 1))))
        assertTrue(RewatchIntentRepository.isArmedContent("  TT0903747 "))
    }

    @Test
    fun `an armed title does not arm a different one`() {
        RewatchIntentRepository.armContent("tt1375666")

        assertFalse(RewatchIntentRepository.isArmed(show()))
        assertFalse(RewatchIntentRepository.isArmedContent("tt0903747"))
    }

    @Test
    fun `season and episode do not change the intent key`() {
        RewatchIntentRepository.armContent("tt0903747")

        assertTrue(RewatchIntentRepository.isArmed(show(episode = TrackingEpisode(season = 2, number = 5))))
    }

    @Test
    fun `consuming clears the intent for the recorded title`() {
        val media = show(episode = TrackingEpisode(season = 3, number = 2))
        RewatchIntentRepository.armContent("tt0903747")

        RewatchIntentRepository.consume(media)
        assertFalse(RewatchIntentRepository.isArmed(media))

        RewatchIntentRepository.armContent("tt0903747")
        RewatchIntentRepository.disarmContent("TT0903747")
        assertFalse(RewatchIntentRepository.isArmed(media))
    }

    @Test
    fun `toggling flips the armed state`() {
        RewatchIntentRepository.toggleContent("tt1375666")
        assertTrue(RewatchIntentRepository.isArmedContent("tt1375666"))

        RewatchIntentRepository.toggleContent("tt1375666")
        assertFalse(RewatchIntentRepository.isArmedContent("tt1375666"))
    }

    @Test
    fun `media without a catalog reference falls back to the external id`() {
        val movie = movie()
        assertEquals("movie:simkl:472214", rewatchIntentKey(movie))

        RewatchIntentRepository.armContent(rewatchIntentKey(movie))
        assertTrue(RewatchIntentRepository.isArmed(movie))

        val imdbOnly = movie.copy(ids = TrackingExternalIds(imdb = "tt1375666"))
        assertEquals("movie:imdb:tt1375666", rewatchIntentKey(imdbOnly))
    }

    private fun movie() = TrackingMediaReference(
        kind = TrackingMediaKind.MOVIE,
        title = "Inception",
        ids = TrackingExternalIds(simkl = 472214, imdb = "tt1375666"),
    )

    private fun show(episode: TrackingEpisode? = null) = TrackingMediaReference(
        kind = TrackingMediaKind.SHOW,
        title = "Breaking Bad",
        ids = TrackingExternalIds(simkl = 1234, imdb = "tt0903747"),
        episode = episode,
        catalog = TrackingCatalogReference(contentId = "tt0903747", contentType = "series"),
    )
}
