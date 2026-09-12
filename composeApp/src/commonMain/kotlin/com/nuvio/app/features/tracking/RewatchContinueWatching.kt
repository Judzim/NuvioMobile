package com.nuvio.app.features.tracking

import com.nuvio.app.features.simkl.SimklRewatchMode
import com.nuvio.app.features.simkl.SimklRewatchStatus
import kotlinx.serialization.Serializable

/**
 * A rewatch the user wants to see in Continue Watching.
 *
 * Simkl keeps a rewatch in its own session and never moves the canonical watch position, so the
 * regular Continue Watching row would keep pointing at the episode the user finished months ago.
 * This record is the local answer to "where is the rewatch run now": while it exists, the next-up
 * card for that series is resolved from [seasonNumber]/[episodeNumber] instead of the canonical
 * position, so a run started at S01E01 offers S01E02 next.
 *
 * [matchKeys] holds every ID form of the series (imdb, tmdb, tvdb, simkl, the catalogue id), because
 * the Continue Watching pipeline builds its series key from whichever id the playback carried.
 */
@Serializable
data class RewatchContinueWatchingSeed(
    val contentId: String,
    val matchKeys: List<String> = emptyList(),
    val seasonNumber: Int,
    val episodeNumber: Int,
    val markedAtEpochMs: Long,
) {
    fun matches(contentId: String?): Boolean {
        val candidate = contentId?.trim().orEmpty()
        if (candidate.isEmpty()) return false
        if (candidate.equals(this.contentId, ignoreCase = true)) return true
        return matchKeys.any { key -> key.equals(candidate, ignoreCase = true) }
    }
}

/** What a finished rewatch should do to the Continue Watching row of its series. */
internal enum class RewatchContinueWatchingAction {
    /** Leave Continue Watching on the canonical position. */
    NONE,

    /** Start (or restart) the run: the series follows this episode from now on. */
    START,

    /** The run continued with an episode that comes after the stored one. */
    ADVANCE,
}

/**
 * Manual mode asks in the prompt, so only automatic mode decides here. Only a playback Simkl
 * actually stored on a rewatch session counts: `too_soon`, `first_watch` and `pro_required` all
 * leave the canonical position alone.
 */
internal fun rewatchContinueWatchingAction(
    mode: SimklRewatchMode,
    rewatchStatus: SimklRewatchStatus?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    hasExistingSeed: Boolean,
    existingSeedSeason: Int? = null,
    existingSeedEpisode: Int? = null,
): RewatchContinueWatchingAction {
    if (mode != SimklRewatchMode.AUTOMATIC) return RewatchContinueWatchingAction.NONE
    if (rewatchStatus?.isRecorded != true) return RewatchContinueWatchingAction.NONE
    val season = seasonNumber ?: return RewatchContinueWatchingAction.NONE
    val episode = episodeNumber ?: return RewatchContinueWatchingAction.NONE
    if (season == 0) return RewatchContinueWatchingAction.NONE
    if (hasExistingSeed) {
        return if (continuesRewatchRun(existingSeedSeason, existingSeedEpisode, season, episode)) {
            RewatchContinueWatchingAction.ADVANCE
        } else {
            RewatchContinueWatchingAction.NONE
        }
    }
    // Matt's rule: a rewatch that starts a season (S01E01, S02E01, ...) is a run, a random single
    // episode is not, so only the season opener adds the series to Continue Watching by itself.
    return if (episode == 1) RewatchContinueWatchingAction.START else RewatchContinueWatchingAction.NONE
}

/**
 * Whether [season]/[episode] is the next step of a run that sits at [seedSeason]/[seedEpisode]:
 * the following episode of the same season, or the opener of a later one. A jump to an unrelated
 * episode must not drag Continue Watching with it.
 */
internal fun continuesRewatchRun(
    seedSeason: Int?,
    seedEpisode: Int?,
    season: Int?,
    episode: Int?,
): Boolean {
    if (seedSeason == null || seedEpisode == null || season == null || episode == null) return false
    if (season == seedSeason) return episode > seedEpisode
    if (season > seedSeason) return episode == 1
    return false
}

/**
 * Builds the Continue Watching record for a rewatch that just happened. Movies have no next
 * episode, and an episode without coordinates cannot seed a run, so both return null.
 */
internal fun buildRewatchContinueWatchingSeed(
    media: TrackingMediaReference,
    watchedAtEpochMs: Long,
): RewatchContinueWatchingSeed? {
    if (media.kind == TrackingMediaKind.MOVIE) return null
    val season = media.episode?.season ?: return null
    if (season == 0) return null
    val episode = media.episode?.number ?: return null
    if (episode <= 0) return null
    return RewatchContinueWatchingSeed(
        contentId = media.rewatchContinueWatchingContentId(),
        matchKeys = media.rewatchContinueWatchingMatchKeys(),
        seasonNumber = season,
        episodeNumber = episode,
        markedAtEpochMs = watchedAtEpochMs,
    )
}

/** The id the Continue Watching pipeline uses for the playing item, falling back to its ID forms. */
internal fun TrackingMediaReference.rewatchContinueWatchingContentId(): String {
    val catalogId = catalog?.contentId?.trim().orEmpty()
    if (catalogId.isNotEmpty()) return catalogId
    val ids = rewatchContinueWatchingMatchKeys()
    return ids.firstOrNull().orEmpty()
}

/** Every id form the same series can appear under in the progress and watched stores. */
internal fun TrackingMediaReference.rewatchContinueWatchingMatchKeys(): List<String> {
    val keys = buildList {
        ids.imdb?.trim()?.takeIf(String::isNotEmpty)?.let { add("imdb:$it") }
        ids.tmdb?.let { add("tmdb:$it") }
        ids.tvdb?.trim()?.takeIf(String::isNotEmpty)?.let { add("tvdb:$it") }
        ids.simkl?.let { add("simkl:$it") }
        ids.mal?.let { add("mal:$it") }
        ids.anidb?.let { add("anidb:$it") }
        ids.anilist?.let { add("anilist:$it") }
        ids.kitsu?.let { add("kitsu:$it") }
        catalog?.contentId?.trim()?.takeIf(String::isNotEmpty)?.let { add(it) }
    }
    return keys.distinct()
}
