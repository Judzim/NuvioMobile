package com.nuvio.app.features.tracking

import com.nuvio.app.features.watchprogress.WatchProgressRepository

/**
 * Asks the connected trackers what they need before a playback opens.
 *
 * The media is built from the same launch fields the player builds its own scrobble from, so the
 * answer a tracker keeps is keyed by the series the playback will be written under. The resume
 * percentage comes from the launch when it carries one and from the progress store otherwise: a
 * playback that resumes above the completion bar must not be asked about, because its end is not
 * a repeat viewing.
 */
internal suspend fun prepareTrackingPlayback(
    contentType: String?,
    parentMetaId: String?,
    videoId: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    title: String?,
    releaseInfo: String? = null,
    episodeTitle: String? = null,
    progressPercent: Double? = null,
) {
    val contentId = parentMetaId?.trim()?.takeIf(String::isNotEmpty) ?: return
    val media = buildTrackingMediaReference(
        contentType = contentType?.trim()?.takeIf(String::isNotEmpty) ?: "movie",
        parentMetaId = contentId,
        videoId = videoId,
        title = title,
        releaseInfo = releaseInfo,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
    )
    if (!media.hasResolvableIdentity) return
    if (media.kind != TrackingMediaKind.MOVIE && media.episode == null) return
    val percent = progressPercent ?: WatchProgressRepository.progressForVideo(
        videoId = videoId?.trim()?.takeIf(String::isNotEmpty) ?: contentId,
        parentMetaId = contentId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )?.progressPercent?.toDouble()
    TrackingScrobbleCoordinator.preparePlayback(media, percent ?: 0.0)
}
