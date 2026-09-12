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

/**
 * Holds the rewatch question Nuvio shows after a playback that Simkl accepted as a repeat viewing.
 *
 * Nothing is written until the user confirms, which is why the prompt is the only place that turns
 * a playback into a rewatch session in manual mode. The prompt is cleared on every answer and never
 * survives the session.
 */
object RewatchPromptRepository {
    private val _prompt = MutableStateFlow<RewatchPrompt?>(null)
    val prompt: StateFlow<RewatchPrompt?> = _prompt.asStateFlow()

    fun request(prompt: RewatchPrompt) {
        _prompt.value = prompt
    }

    /** Called when the prompt is dismissed, including by its own timeout. */
    fun dismiss() {
        _prompt.value = null
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
        SimklMutationRepository.recordConfirmedRewatch(
            prompt = active,
            includeInContinueWatching = includeInContinueWatching,
        )
    }

    fun clear() {
        _prompt.value = null
    }
}
