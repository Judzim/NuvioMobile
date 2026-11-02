package com.nuvio.app.features.player

import com.nuvio.app.features.tracking.prepareTrackingPlayback

/**
 * Asks the connected trackers before this launch opens the player.
 *
 * The resume percentage rides on the launch when the caller knew it, and the tracking helper falls
 * back to the progress store otherwise. Every navigation into the player goes through this shape,
 * so a tracker question cannot be skipped by one of the ways in.
 */
internal suspend fun prepareTrackingPlayback(launch: PlayerLaunch) {
    prepareTrackingPlayback(
        contentType = launch.contentType ?: launch.parentMetaType,
        parentMetaId = launch.parentMetaId,
        videoId = launch.videoId,
        seasonNumber = launch.seasonNumber,
        episodeNumber = launch.episodeNumber,
        title = launch.title,
        episodeTitle = launch.episodeTitle,
        progressPercent = launch.initialProgressFraction?.let { it * 100.0 },
    )
}
