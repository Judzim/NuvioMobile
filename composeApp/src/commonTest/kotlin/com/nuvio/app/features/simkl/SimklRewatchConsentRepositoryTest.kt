package com.nuvio.app.features.simkl

import com.nuvio.app.features.tracking.TrackingEpisode
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pre-play question: what the host has to do for the launch to continue. */
class SimklRewatchConsentRepositoryTest {

    @AfterTest
    fun clearAnswers() {
        SimklRewatchConsentRepository.clear()
    }

    @Test
    fun `the launch waits for the answer without a deadline`() = runTest {
        val media = media()
        val answer = launch { SimklRewatchConsentRepository.askAndAwait(media) }
        runCurrent()
        assertNotNull(SimklRewatchConsentRepository.question.value)

        advanceTimeBy(30_000)
        runCurrent()
        assertNotNull(SimklRewatchConsentRepository.question.value)

        SimklRewatchConsentRepository.grant()
        answer.join()
        assertTrue(SimklRewatchConsentRepository.grantedFor(media))
    }

    @Test
    fun `the launch continues when the question is dismissed`() = runTest {
        val media = media()
        val answer = launch { SimklRewatchConsentRepository.askAndAwait(media) }
        runCurrent()

        SimklRewatchConsentRepository.dismiss()
        answer.join()

        assertNull(SimklRewatchConsentRepository.question.value)
    }

    private fun media() = TrackingMediaReference(
        kind = TrackingMediaKind.SHOW,
        title = "Dark",
        year = 2017,
        ids = TrackingExternalIds(imdb = "tt5753856"),
        episode = TrackingEpisode(season = 2, number = 7),
    )
}
