package com.nuvio.app.features.simkl

import com.nuvio.app.features.tracking.TrackingMediaReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull

/** An episode that is about to play, held while the user decides whether it opens a run. */
data class RewatchQuestion(val media: TrackingMediaReference)

/**
 * Holds what the user said about recording rewatches, asked before the playbacks they are about.
 *
 * The answer is what [SimklRewatchMode.SEMI_AUTOMATIC] writes from at the end of a playback, so it
 * has to outlive both the question and the playback: a yes for a series covers its episodes until
 * the account runs a session for it, and a no keeps the series quiet for the rest of the app run.
 * Only that series-level answer is kept, nothing reaches the disk, and the next app run asks
 * against what the account holds at that point.
 *
 * The state exists because a question is not a write: nothing is sent until the playback ends and
 * Simkl accepts the scrobble, and a question left unanswered records nothing.
 *
 * An `object` with its own lifetime, so a pending answer survives the popup that asked for it.
 */
object SimklRewatchConsentRepository {
    /**
     * The answers are written from the UI and read from the scrobbler on a background thread, so
     * every access goes through this lock and the series keys are kept as sets: a decision about
     * one series must not forget the previous one.
     */
    private val lock = Any()

    /**
     * The series the user said yes to, until the account holds a session for it.
     *
     * The yes opens the run the user was asked about, not every run of the series: once a session
     * is seen the answer is released, and a later run asks again.
     */
    private val grantedSeriesKeys = mutableSetOf<String>()

    /** The series the user said no to, for the rest of the app run. */
    private val declinedSeriesKeys = mutableSetOf<String>()

    /** The series the user dismissed the question for, until the playback that raised it ends. */
    private val dismissedSeriesKeys = mutableSetOf<String>()

    private val _question = MutableStateFlow<RewatchQuestion?>(null)
    val question: StateFlow<RewatchQuestion?> = _question.asStateFlow()

    /**
     * True once the host drew the current question. The pre-play wait uses it to tell a question
     * the user can see from one that never reached the UI.
     */
    private val _questionShown = MutableStateFlow(false)
    val questionShown: StateFlow<Boolean> = _questionShown.asStateFlow()

    fun markQuestionShown() {
        _questionShown.value = true
    }

    /**
     * Raises the question for a playback that is about to start, unless the user has already said
     * something about this series: an answer of either kind stands for the whole app run, so the
     * same series is never asked about twice.
     */
    fun ask(media: TrackingMediaReference) {
        val seriesKey = media.seriesKey()
        val answered = synchronized(lock) {
            seriesKey in grantedSeriesKeys || seriesKey in declinedSeriesKeys || seriesKey in dismissedSeriesKeys
        }
        if (answered) return
        if (_question.value?.media?.seriesKey() == seriesKey) return
        _questionShown.value = false
        _question.value = RewatchQuestion(media)
    }

    /**
     * Raises the question and suspends the caller until it is answered.
     *
     * The player launch waits here, so the video must not start while the user is still deciding.
     * The host answers through [grant], [decline] or [dismiss], and the question does not close
     * itself: an unanswered one keeps the playback waiting. The timeout only covers a question that
     * never reached the UI, so a broken host cannot block playback forever.
     */
    suspend fun askAndAwait(media: TrackingMediaReference, timeoutMs: Long = 10_000L) {
        ask(media)
        val seriesKey = media.seriesKey()
        val open = _question.value
        if (open == null || open.media.seriesKey() != seriesKey) return
        val shown = withTimeoutOrNull(timeoutMs) {
            merge(
                questionShown.filter { it }.map { true },
                question.filter { it == null || it.media.seriesKey() != seriesKey }.map { false },
            ).first()
        }
        when (shown) {
            true -> question.first { it == null || it.media.seriesKey() != seriesKey }
            false -> Unit
            null -> synchronized(lock) {
                if (_question.value?.media?.seriesKey() == seriesKey) {
                    _question.value = null
                    _questionShown.value = false
                    dismissedSeriesKeys += seriesKey
                }
            }
        }
    }

    /** The user said yes: the series may be written into a run until the account has one. */
    fun grant() {
        val question = _question.value ?: return
        _question.value = null
        synchronized(lock) { grantedSeriesKeys += question.media.seriesKey() }
    }

    /** The user said no: the series stays out of the account for the rest of this app run. */
    fun decline() {
        val question = _question.value ?: return
        _question.value = null
        synchronized(lock) { declinedSeriesKeys += question.media.seriesKey() }
    }

    /**
     * Drops a question that was never answered. The dismissal is remembered for the playback that
     * raised it, so a pause and its resume do not ask again; it is cleared when that playback stops.
     */
    fun dismiss() {
        val question = _question.value ?: return
        _question.value = null
        synchronized(lock) { dismissedSeriesKeys += question.media.seriesKey() }
    }

    /** The playback that raised the dismissed question has ended, so the series may ask again. */
    fun clearDismissal(media: TrackingMediaReference) {
        val seriesKey = media.seriesKey()
        synchronized(lock) { dismissedSeriesKeys -= seriesKey }
    }

    /** True when a yes of this app run covers the media's series and no session has replaced it. */
    fun grantedFor(media: TrackingMediaReference): Boolean {
        val seriesKey = media.seriesKey()
        return synchronized(lock) { seriesKey in grantedSeriesKeys }
    }

    /**
     * The account runs this series now, so the answer has done its job and is released: the next
     * run of the series asks again.
     */
    fun releaseGrant(media: TrackingMediaReference) {
        val seriesKey = media.seriesKey()
        synchronized(lock) { grantedSeriesKeys -= seriesKey }
    }

    /**
     * Forgets every answer and closes an open question, for the profile that gave them.
     *
     * The answers are per series and per app run, so they belong to the account that answered: a
     * profile change starts from nothing, the same way the next app run does.
     */
    fun clear() {
        _question.value = null
        _questionShown.value = false
        synchronized(lock) {
            grantedSeriesKeys.clear()
            declinedSeriesKeys.clear()
            dismissedSeriesKeys.clear()
        }
    }

    /**
     * The series a reference belongs to, whatever episode it carries. An answer is about the series,
     * because the run it opens covers the episodes that follow the one that was asked about.
     */
    private fun TrackingMediaReference.seriesKey(): String = copy(episode = null).stableKey
}
