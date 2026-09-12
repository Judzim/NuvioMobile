package com.nuvio.app.features.home

import com.nuvio.app.features.details.SeriesPrimaryAction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeNextUpActionTest {
    private fun action(
        resumePositionMs: Long? = null,
        isWatchAgain: Boolean = false,
    ) = SeriesPrimaryAction(
        label = "label",
        videoId = "show:1:1",
        seasonNumber = 1,
        episodeNumber = 1,
        episodeTitle = null,
        episodeThumbnail = null,
        resumePositionMs = resumePositionMs,
        isWatchAgain = isWatchAgain,
    )

    @Test
    fun `a watch again restart is not a next episode`() {
        assertTrue(homeNextUpOffersNothing(action(isWatchAgain = true)))
    }

    @Test
    fun `a resume stays out of the next up row`() {
        assertTrue(homeNextUpOffersNothing(action(resumePositionMs = 12_000L)))
    }

    @Test
    fun `a plain next episode is offered`() {
        assertFalse(homeNextUpOffersNothing(action()))
    }

    @Test
    fun `a rewatch run is offered`() {
        assertFalse(
            homeNextUpOffersNothing(
                SeriesPrimaryAction(
                    label = "Next Up",
                    videoId = "show:1:2",
                    seasonNumber = 1,
                    episodeNumber = 2,
                    episodeTitle = null,
                    episodeThumbnail = null,
                    resumePositionMs = null,
                    isWatchAgain = false,
                ),
            ),
        )
    }
}
