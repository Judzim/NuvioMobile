package com.nuvio.app.features.tracking

import com.nuvio.app.features.simkl.SimklMutationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A finished playback the user can still choose to record as a rewatch. */
data class RewatchPrompt(
    val media: TrackingMediaReference,
    val watchedAtEpochMs: Long,
    /**
     * Where the series sits in the rewatch run. Present when the prompt can also put the series
     * into Continue Watching (see [RewatchContinueWatchingSeed]).
     */
    val continueWatchingSeed: RewatchContinueWatchingSeed? = null,
)

/** What an answer to the prompt actually did, shown briefly so the tap has visible feedback. */
enum class RewatchNoticeKind {
    /** The rewatch reached Simkl. */
    RECORDED,

    /** The rewatch reached Simkl and the series now follows the run in Continue Watching. */
    RECORDED_WITH_RUN,

    /** The user answered No, or the question timed out. */
    NOT_RECORDED,

    /** The write to Simkl failed. */
    FAILED,
}

data class RewatchNotice(val kind: RewatchNoticeKind)

/**
 * Holds the rewatch question Nuvio shows after a playback that Simkl accepted as a repeat viewing.
 *
 * Nothing is written until the user confirms, which is why the prompt is the only place that turns
 * a playback into a rewatch session in manual mode. The prompt is cleared on every answer and never
 * survives the session. The answer also leaves a [notice] behind, so the user sees what happened
 * instead of having to trust that a tap did something.
 */
object RewatchPromptRepository {
    private val _prompt = MutableStateFlow<RewatchPrompt?>(null)
    val prompt: StateFlow<RewatchPrompt?> = _prompt.asStateFlow()

    private val _notice = MutableStateFlow<RewatchNotice?>(null)
    val notice: StateFlow<RewatchNotice?> = _notice.asStateFlow()

    fun request(prompt: RewatchPrompt) {
        _prompt.value = prompt
    }

    /** Called when the prompt is dismissed, including by its own timeout. */
    fun dismiss() {
        _prompt.value = null
    }

    /** The user answered No: nothing is written, and the app says so. */
    fun decline() {
        _prompt.value = null
        _notice.value = RewatchNotice(RewatchNoticeKind.NOT_RECORDED)
    }

    /**
     * Records the pending rewatch and closes the prompt.
     *
     * [includeInContinueWatching] is the prompt's second answer: the rewatch is written either way,
     * and the extra flag additionally lets the series follow the run in Continue Watching.
     */
    suspend fun confirm(includeInContinueWatching: Boolean = false) {
        val active = _prompt.value ?: return
        _prompt.value = null
        val recorded = SimklMutationRepository.recordConfirmedRewatch(
            prompt = active,
            includeInContinueWatching = includeInContinueWatching,
        )
        _notice.value = RewatchNotice(
            when {
                !recorded -> RewatchNoticeKind.FAILED
                includeInContinueWatching -> RewatchNoticeKind.RECORDED_WITH_RUN
                else -> RewatchNoticeKind.RECORDED
            },
        )
    }

    /** Hides the feedback of the last answer. */
    fun dismissNotice() {
        _notice.value = null
    }

    fun clear() {
        _prompt.value = null
        _notice.value = null
    }
}
