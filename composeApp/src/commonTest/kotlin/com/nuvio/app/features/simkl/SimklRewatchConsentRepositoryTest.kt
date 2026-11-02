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

/**
 * The pre-play question: what the host has to do for the launch to continue, and what happens when
 * the question never reaches the UI.
 */
class SimklRewatchConsentRepositoryTest {

    @AfterTest
    fun clearAnswers() {
        SimklRewatchConsentRepository.clear()
    }

    @Test
    fun `the launch waits for the answer once the host has shown the question`() = runTest {
        val media = media()
        val answer = launch { SimklRewatchConsentRepository.askAndAwait(media) }
        runCurrent()
        assertNotNull(SimklRewatchConsentRepository.question.value)

        SimklRewatchConsentRepository.markQuestionShown()
        runCurrent()
        // A question the user can see has no timeout: it stays open until it is answered.
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
        SimklRewatchConsentRepository.markQuestionShown()
        runCurrent()

        SimklRewatchConsentRepository.dismiss()
        answer.join()

        assertNull(SimklRewatchConsentRepository.question.value)
    }

    @Test
    fun `a question that never reached the host is dropped instead of blocking the launch`() = runTest {
        val media = media()
        val answer = launch { SimklRewatchConsentRepository.askAndAwait(media) }
        runCurrent()

        advanceTimeBy(10_000)
        runCurrent()
        answer.join()

        assertNull(SimklRewatchConsentRepository.question.value)
        // The series is not asked again for this playback, but nothing was granted.
        assertTrue(!SimklRewatchConsentRepository.grantedFor(media))
    }

    private fun media() = TrackingMediaReference(
        kind = TrackingMediaKind.SHOW,
        title = "Dark",
        year = 2017,
        ids = TrackingExternalIds(imdb = "tt5753856"),
        episode = TrackingEpisode(season = 2, number = 7),
    )
}
