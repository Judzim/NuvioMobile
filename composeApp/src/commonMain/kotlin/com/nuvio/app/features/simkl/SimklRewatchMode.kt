package com.nuvio.app.features.simkl

import com.nuvio.app.features.tracking.TrackingScrobbleAction

/**
 * How Nuvio records rewatches on Simkl.
 *
 * Simkl requires an explicit opt-in for rewatch bookkeeping, so the default is [OFF].
 * [AUTOMATIC] sends `allow_rewatch=yes` on every finished playback, [MANUAL] only after the
 * user asks for it (the "Watch again" action on a finished title).
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

/** Query Simkl expects on the scrobble calls that are allowed to record a rewatch session. */
internal val SIMKL_ALLOW_REWATCH_QUERY: Map<String, String> = mapOf("allow_rewatch" to "yes")

/**
 * Simkl Pro / VIP only. Unknown plans stay ineligible: sending the flag for a free account would
 * consume a rate-limit slot and be dropped server-side.
 */
internal fun isSimklRewatchPlanEligible(accountType: String?): Boolean {
    val normalized = accountType?.trim()?.lowercase().orEmpty()
    return normalized == "pro" || normalized == "vip"
}

/**
 * Whether the scrobble call should carry `allow_rewatch=yes`.
 *
 * The flag is never sent on start/pause, because those endpoints mark nothing watched and Simkl
 * documents that the flag on `/scrobble/start` can open a session for a different title. Below
 * [SIMKL_REWATCH_MIN_PROGRESS_PERCENT] a stop resolves to a pause and returns no rewatch fields.
 */
internal fun shouldRecordSimklRewatch(
    mode: SimklRewatchMode,
    accountType: String?,
    action: TrackingScrobbleAction,
    progressPercent: Double,
    manualIntentArmed: Boolean,
): Boolean = when {
    !mode.isEnabled -> false
    !isSimklRewatchPlanEligible(accountType) -> false
    action != TrackingScrobbleAction.STOP -> false
    progressPercent < SIMKL_REWATCH_MIN_PROGRESS_PERCENT -> false
    mode == SimklRewatchMode.AUTOMATIC -> true
    else -> manualIntentArmed
}

/** Free-tier accounts cannot record rewatches, so the picker keeps them off and offers an upgrade. */
internal fun isSimklRewatchModeSelectable(
    mode: SimklRewatchMode,
    accountType: String?,
): Boolean = mode == SimklRewatchMode.OFF || isSimklRewatchPlanEligible(accountType)
