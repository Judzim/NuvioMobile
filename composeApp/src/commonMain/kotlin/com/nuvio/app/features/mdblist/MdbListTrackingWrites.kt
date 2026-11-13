package com.nuvio.app.features.mdblist

import com.nuvio.app.features.tracking.TRACKING_COMPLETION_BAR_PERCENT
import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingHistoryWriter
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import com.nuvio.app.features.tracking.TrackingScrobbler
import com.nuvio.app.features.tracking.TrackingSeekScrobblePolicy
import kotlinx.coroutines.CancellationException

class MdbListTrackingWrites(
    private val sync: MdbListSyncRepository,
    private val history: MdbListHistoryService,
    private val scrobble: MdbListScrobbleService,
    private val episodeMappings: MdbListEpisodeMappingService,
) : TrackingHistoryWriter, TrackingScrobbler {
    override val providerId = TrackingProviderId.MDBLIST
    override val seekScrobblePolicy = TrackingSeekScrobblePolicy.STOP_AND_RESTART

    // MDBList reads episode numbers in TMDB's numbering, which can differ from the addon's.
    override suspend fun addToHistory(profileId: Int, items: Collection<TrackingHistoryItem>) =
        scope(profileId).let { scope -> history.add(scope, items.map { it.copy(media = it.media.inMdbListNumbering()) }) }

    override suspend fun removeFromHistory(profileId: Int, items: Collection<TrackingMediaReference>) =
        scope(profileId).let { scope -> history.remove(scope, items.map { it.inMdbListNumbering() }) }

    /*
     * MDBList records a watch on a stop at or above the bar. One playback can report more than one
     * stop when it is interrupted and resumed; the player's run id lets the repeat be dropped
     * without touching a later, separate viewing.
     */
    private var lastCompletedPlaybackId = 0L

    override suspend fun scrobble(profileId: Int, action: TrackingScrobbleAction, event: TrackingScrobbleEvent) {
        val completesPlayback = action == TrackingScrobbleAction.STOP &&
            event.progressPercent >= TRACKING_COMPLETION_BAR_PERCENT
        if (
            completesPlayback &&
            event.playbackId != 0L &&
            event.playbackId == lastCompletedPlaybackId
        ) {
            return
        }
        val written = scrobble.scrobble(
            scope(profileId),
            action,
            event.copy(media = event.media.inMdbListNumbering()),
        )
        // Latch only a completion the tracker accepted: a failed write must not suppress the stop
        // that really ends the playback.
        if (completesPlayback && written && event.playbackId != 0L) {
            lastCompletedPlaybackId = event.playbackId
        }
    }

    private suspend fun TrackingMediaReference.inMdbListNumbering() =
        episodeMappings.toMdbListNumbering(this, sync.currentProjection())

    private fun scope(profileId: Int): MdbListAuthScope = sync.currentScope().also {
        if (it.profileId != profileId) throw CancellationException("MDBList profile changed")
    }
}
