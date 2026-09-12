package com.nuvio.app.features.simkl

import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingSettingsRepository

/**
 * How Nuvio records rewatches on Simkl.
 *
 * Simkl requires an explicit opt-in for rewatch bookkeeping, so the default is [OFF].
 * [AUTOMATIC] sends `allow_rewatch=yes` on every finished playback, [MANUAL] asks after the
 * playback instead and only writes when the user confirms.
 */
enum class SimklRewatchMode {
    OFF,
    MANUAL,
    AUTOMATIC,
    ;

    val isEnabled: Boolean
        get() = this != OFF

    companion object {
        val Default: SimklRewatchMode = OFF

        fun fromStorage(value: String?): SimklRewatchMode {
            val normalized = value?.trim().orEmpty()
            return entries.firstOrNull { mode -> mode.name.equals(normalized, ignoreCase = true) } ?: Default
        }
    }
}

/** Simkl marks a title watched at this progress, and rewatches can only exist where a watch does. */
internal const val SIMKL_REWATCH_MIN_PROGRESS_PERCENT = 80.0

/**
 * The window the user can pick the completion threshold from.
 *
 * Simkl itself marks a playback watched at 80%, so the slider cannot start lower; above that the
 * user decides when Nuvio reports a finished playback, and therefore when Simkl is told about it.
 */
internal const val SIMKL_WATCHED_THRESHOLD_MIN_PERCENT = 80
internal const val SIMKL_WATCHED_THRESHOLD_MAX_PERCENT = 95
internal const val SIMKL_WATCHED_THRESHOLD_DEFAULT_PERCENT = 80

internal val SimklWatchedThresholdRange: IntRange =
    SIMKL_WATCHED_THRESHOLD_MIN_PERCENT..SIMKL_WATCHED_THRESHOLD_MAX_PERCENT

/**
 * Where a playback counts as finished, as the user set it.
 *
 * Read in one place so the reporting side (what Nuvio tells Simkl) and the reading side (what the app
 * shows for the account) cannot drift apart: a playback paused below the value stays in progress
 * everywhere instead of being reported as a pause and shown as a finished watch.
 */
internal val simklWatchedThresholdPercent: Double
    get() = TrackingSettingsRepository.uiState.value.simklWatchedThresholdPercent.toDouble()

internal fun coerceSimklWatchedThresholdPercent(percent: Int): Int = percent.coerceIn(
    minimumValue = SIMKL_WATCHED_THRESHOLD_MIN_PERCENT,
    maximumValue = SIMKL_WATCHED_THRESHOLD_MAX_PERCENT,
)

/** Simkl merges two watches of the same item this close together, so asking would be pointless. */
internal const val SIMKL_REWATCH_MIN_GAP_MS = 48L * 60L * 60L * 1_000L

/** Query Simkl expects on the calls that are allowed to record a rewatch session. */
internal val SIMKL_ALLOW_REWATCH_QUERY: Map<String, String> = mapOf("allow_rewatch" to "yes")

/**
 * Whether the scrobble call itself should carry `allow_rewatch=yes`. Only automatic mode does that,
 * because a confirmed rewatch is written after the fact through `/sync/history`.
 */
internal fun shouldRecordSimklRewatchOnStop(
    mode: SimklRewatchMode,
    accountType: String?,
    action: TrackingScrobbleAction,
    progressPercent: Double,
    completionThresholdPercent: Double = SIMKL_REWATCH_MIN_PROGRESS_PERCENT,
): Boolean = when {
    mode != SimklRewatchMode.AUTOMATIC -> false
    !isSimklRewatchPlanEligible(accountType) -> false
    action != TrackingScrobbleAction.STOP -> false
    progressPercent < completionThresholdPercent -> false
    else -> true
}

/**
 * Whether the user should be asked to record the playback Simkl just accepted as a repeat viewing.
 *
 * The question is only worth asking when Simkl would create a session for it: the plan has to allow
 * rewatches and the item has to be in the user's history already. A watch from the last 48 hours is
 * skipped, because Simkl folds those into the existing session and a confirmation would do nothing.
 */
internal fun shouldPromptSimklRewatch(
    mode: SimklRewatchMode,
    accountType: String?,
    action: TrackingScrobbleAction,
    outcome: SimklScrobbleOutcome,
    progressPercent: Double,
    priorWatch: SimklPriorWatch,
    nowEpochMs: Long,
    completionThresholdPercent: Double = SIMKL_REWATCH_MIN_PROGRESS_PERCENT,
): Boolean {
    if (mode != SimklRewatchMode.MANUAL) return false
    if (!isSimklRewatchPlanEligible(accountType)) return false
    if (action != TrackingScrobbleAction.STOP) return false
    if (outcome != SimklScrobbleOutcome.SCROBBLE) return false
    if (progressPercent < completionThresholdPercent) return false
    if (!priorWatch.wasWatched) return false
    val watchedAt = priorWatch.watchedAtEpochMs ?: return true
    return nowEpochMs - watchedAt >= SIMKL_REWATCH_MIN_GAP_MS
}

/** What the local Simkl snapshot knew about the scrobbled item before this playback. */
internal data class SimklPriorWatch(
    val wasWatched: Boolean,
    val watchedAtEpochMs: Long?,
) {
    companion object {
        val None = SimklPriorWatch(wasWatched = false, watchedAtEpochMs = null)
    }
}

/**
 * Simkl Pro / VIP only. Unknown plans stay ineligible: sending the flag for a free account would
 * consume a rate-limit slot and be dropped server-side.
 */
internal fun isSimklRewatchPlanEligible(accountType: String?): Boolean {
    val normalized = accountType?.trim()?.lowercase().orEmpty()
    return normalized == "pro" || normalized == "vip"
}

/** Free-tier accounts cannot record rewatches, so the picker keeps them off and offers an upgrade. */
internal fun isSimklRewatchModeSelectable(
    mode: SimklRewatchMode,
    accountType: String?,
): Boolean = mode == SimklRewatchMode.OFF || isSimklRewatchPlanEligible(accountType)
