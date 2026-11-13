package com.nuvio.app.features.simkl

import com.nuvio.app.features.tracking.RewatchRunPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Runs a Continue Watching mark just advanced, kept over the account read until the read reports
 * the same or a later episode.
 *
 * A classic mark is held by the provider's optimistic progress; a rewatch has no such provider row,
 * so the run itself is advanced here. The hold is in memory only: it is a view of a write that is
 * already on its way, not a second source of truth.
 */
object SimklRewatchRunHold {
    private val _runs = MutableStateFlow<Map<String, RewatchRunPosition>>(emptyMap())
    val runs: StateFlow<Map<String, RewatchRunPosition>> = _runs.asStateFlow()

    fun hold(run: RewatchRunPosition) {
        _runs.update { current -> current + (run.contentId to run) }
    }

    fun releaseIfStill(run: RewatchRunPosition) {
        _runs.update { current ->
            if (current[run.contentId] == run) current - run.contentId else current
        }
    }

    fun clear() {
        _runs.value = emptyMap()
    }
}

/**
 * Replaces the account runs with the optimistic ones a Continue Watching mark just advanced.
 *
 * The optimistic entry is keyed by the run's own content id, which is what the row matches. It is
 * kept until the account read reports the same or a later episode, so a stale read cannot pull the
 * row back below the mark the user just made.
 */
internal fun mergeOptimisticRewatchRuns(
    accountRuns: List<RewatchRunPosition>,
    optimisticRuns: Map<String, RewatchRunPosition>,
): List<RewatchRunPosition> {
    if (optimisticRuns.isEmpty()) return accountRuns
    val accountIds = accountRuns.mapTo(mutableSetOf()) { run -> run.contentId }
    return (
        accountRuns.map { run -> optimisticRuns[run.contentId] ?: run } +
            optimisticRuns.values.filter { run -> run.contentId !in accountIds }
        ).sortedByDescending { run -> run.markedAtEpochMs }
}

/** The season-then-episode order two runs are compared by. */
internal fun compareRewatchRuns(left: RewatchRunPosition, right: RewatchRunPosition): Int {
    val season = left.seasonNumber.compareTo(right.seasonNumber)
    return if (season != 0) season else left.episodeNumber.compareTo(right.episodeNumber)
}

/**
 * Whether the account's run of the series has reached the marked episode or moved past it, in the
 * catalogue's order across seasons, or the account runs the series no more.
 *
 * Both happen when the mark completes a session: Simkl reports a session that covers every episode
 * of its item as completed, and the series then runs on another season's session or not at all.
 * Waiting for the same season only kept the finished season's card up until the hold timed out.
 */
internal fun accountCaughtUpWithOptimisticRun(
    accountRuns: List<RewatchRunPosition>,
    optimistic: RewatchRunPosition,
): Boolean {
    val run = accountRuns.firstOrNull { candidate -> candidate.matches(optimistic.contentId) } ?: return true
    return compareRewatchRuns(run, optimistic) >= 0
}

/**
 * Whether a Next Up card stands for the rewatch run of its series. The run moves the card's seed
 * onto its own position, so a card seeded anywhere else is the canonical card.
 */
internal fun isRewatchRunCard(
    runs: List<RewatchRunPosition>,
    contentId: String,
    seedSeason: Int?,
    seedEpisode: Int?,
): Boolean {
    val run = runs.firstOrNull { candidate -> candidate.matches(contentId) } ?: return false
    return run.seasonNumber == seedSeason && run.episodeNumber == seedEpisode
}

