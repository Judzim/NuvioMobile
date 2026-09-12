package com.nuvio.app.features.simkl

import com.nuvio.app.core.time.parseZonedIsoDateTimeToEpochMs
import com.nuvio.app.features.tracking.RewatchContinueWatchingSeed
import kotlinx.serialization.Serializable

/**
 * A rewatch run the account is in the middle of: consecutive episodes of a series being rewatched.
 *
 * Simkl keeps a rewatch in its own session and never moves the canonical watch position, so a client
 * that reads only the canonical row offers the episode the user finished months ago, or nothing at
 * all. The sessions are the only place that knows where the run is, and they live on the account, so
 * a run read from Simkl shows up on every device the user signs in on - unlike a local record.
 */
@Serializable
data class SimklRewatchRun(
    /** The id form the catalogue knows the series by; see [SimklMedia.canonicalContentId]. */
    val contentId: String,
    /** Every id form the series appears under, used to match the row it belongs to. */
    val matchKeys: List<String> = emptyList(),
    /** The last episode of the run the user rewatched. */
    val seasonNumber: Int,
    val episodeNumber: Int,
    /** When that episode was rewatched, so a fresh run outranks an old watch position. */
    val markedAtEpochMs: Long,
)

/** Two episodes in a row make a run; one on its own is a rewatch of a random episode. */
private const val MINIMUM_RUN_EPISODES = 2

/**
 * Reads the rewatch sessions of an account and keeps the ones that look like a run.
 *
 * Sessions are merged per series first, because Simkl splits a running rewatch into a new session
 * once the same episode is rewatched 48 hours later; the run itself continues across the split.
 * Episodes then have to form a chain of consecutive numbers inside one season, and the chain holding
 * the most recently rewatched episode is the run. Anything shorter is left alone: the user asked for
 * a single episode, not for the series to follow along in Continue Watching.
 */
internal fun deriveSimklRewatchRuns(
    entries: List<SimklLibraryEntry>,
    animeIdPreference: SimklAnimeIdPreference,
): List<SimklRewatchRun> {
    val sessions = entries.filter { entry -> entry.isRewatch && entry.media != null }
    if (sessions.isEmpty()) return emptyList()
    return sessions
        .groupBy { entry -> entry.media?.canonicalContentId(animeIdPreference).orEmpty() }
        .filterKeys { contentId -> contentId.isNotEmpty() }
        .mapNotNull { (contentId, rows) -> buildRewatchRun(contentId, rows, animeIdPreference) }
        .sortedByDescending(SimklRewatchRun::markedAtEpochMs)
}

private fun buildRewatchRun(
    contentId: String,
    rows: List<SimklLibraryEntry>,
    animeIdPreference: SimklAnimeIdPreference,
): SimklRewatchRun? {
    val episodes = rows
        .flatMap { row -> row.rewatchedEpisodes() }
        .newestPerEpisode()
    if (episodes.isEmpty()) return null
    val newest = episodes.maxWith(
        compareBy(
            { episode -> episode.watchedAtEpochMs ?: Long.MIN_VALUE },
            { episode -> episode.seasonNumber },
            { episode -> episode.episodeNumber },
        ),
    )
    val chain = consecutiveChains(episodes)
        .firstOrNull { candidate -> candidate.any { it.isSameEpisodeAs(newest) } }
        ?: return null
    if (chain.size < MINIMUM_RUN_EPISODES) return null
    val position = chain.maxBy { episode -> episode.episodeNumber }
    return SimklRewatchRun(
        contentId = contentId,
        matchKeys = rows.firstNotNullOfOrNull(SimklLibraryEntry::media)?.rewatchMatchKeys(contentId).orEmpty(),
        seasonNumber = position.seasonNumber,
        episodeNumber = position.episodeNumber,
        markedAtEpochMs = newest.watchedAtEpochMs ?: position.watchedAtEpochMs ?: 0L,
    )
}

private data class RewatchedEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val watchedAtEpochMs: Long?,
) {
    fun isSameEpisodeAs(other: RewatchedEpisode): Boolean =
        seasonNumber == other.seasonNumber && episodeNumber == other.episodeNumber
}

/** Every episode the session rows carry, falling back to the row date when the episode has none. */
private fun SimklLibraryEntry.rewatchedEpisodes(): List<RewatchedEpisode> {
    val rowWatchedAt = lastWatchedAt?.let(::parseZonedIsoDateTimeToEpochMs)
    return seasons.flatMap { season ->
        val seasonNumber = season.number ?: 0
        season.episodes.mapNotNull { episode ->
            val episodeNumber = episode.number?.takeIf { number -> number > 0 } ?: return@mapNotNull null
            RewatchedEpisode(
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                watchedAtEpochMs = episode.watchedAt?.let(::parseZonedIsoDateTimeToEpochMs) ?: rowWatchedAt,
            )
        }
    }
}

/** Keeps one row per episode, dated with the latest time it was rewatched across all sessions. */
private fun List<RewatchedEpisode>.newestPerEpisode(): List<RewatchedEpisode> =
    groupBy { episode -> episode.seasonNumber to episode.episodeNumber }
        .values
        .map { sameEpisode -> sameEpisode.maxBy { episode -> episode.watchedAtEpochMs ?: Long.MIN_VALUE } }

/** Splits episodes into chains of consecutive numbers, per season. */
private fun consecutiveChains(episodes: List<RewatchedEpisode>): List<List<RewatchedEpisode>> =
    episodes
        .groupBy(RewatchedEpisode::seasonNumber)
        .values
        .flatMap { seasonEpisodes ->
            seasonEpisodes
                .sortedBy(RewatchedEpisode::episodeNumber)
                .fold(mutableListOf<MutableList<RewatchedEpisode>>()) { chains, episode ->
                    val running = chains.lastOrNull()
                    if (running != null && episode.episodeNumber == running.last().episodeNumber + 1) {
                        running.add(episode)
                    } else {
                        chains.add(mutableListOf(episode))
                    }
                    chains
                }
        }

internal fun SimklMedia.rewatchMatchKeys(contentId: String): List<String> = buildList {
    add(contentId)
    ids.idValue("imdb")?.let { imdb -> add(imdb); add("imdb:$imdb") }
    ids.idValue("tmdb")?.let { tmdb -> add("tmdb:$tmdb") }
    ids.idValue("tvdb")?.let { tvdb -> add("tvdb:$tvdb") }
    ids.simklIdValue()?.let { simkl -> add("simkl:$simkl") }
}.distinct()

/** Whether this run belongs to the given content id, in any of the id forms the series uses. */
internal fun SimklRewatchRun.matches(contentId: String?): Boolean {
    val candidate = contentId?.trim().orEmpty()
    if (candidate.isEmpty()) return false
    if (candidate.equals(this.contentId, ignoreCase = true)) return true
    return matchKeys.any { key -> key.equals(candidate, ignoreCase = true) }
}

/** The run in the shape Continue Watching and the detail screen already understand. */
internal fun SimklRewatchRun.toContinueWatchingSeed(): RewatchContinueWatchingSeed =
    RewatchContinueWatchingSeed(
        contentId = contentId,
        matchKeys = matchKeys,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        markedAtEpochMs = markedAtEpochMs,
    )
