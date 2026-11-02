package com.nuvio.app.features.tracking

import co.touchlab.kermit.Logger
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

data class TrackingScrobbleFailure(
    val providerId: TrackingProviderId,
    val cause: Throwable,
)

object TrackingScrobbleCoordinator {
    private val log = Logger.withTag("TrackingScrobble")

    suspend fun scrobble(
        profileId: Int,
        action: TrackingScrobbleAction,
        event: TrackingScrobbleEvent,
    ): List<TrackingScrobbleFailure> {
        if (profileId != ProfileRepository.activeProfileId) return emptyList()
        TrackingProviderRegistry.ensureLoaded()
        val failures = dispatchTrackingScrobble(
            scrobblers = TrackingProviderRegistry.connectedScrobblers(),
            profileId = profileId,
            action = action,
            event = event,
        )
        failures.forEach { failure ->
            log.w(failure.cause) {
                "${failure.providerId.storageId} scrobble ${action.wireValue} failed"
            }
        }
        return failures
    }

    suspend fun scrobbleSeek(
        profileId: Int,
        action: TrackingScrobbleAction,
        event: TrackingScrobbleEvent,
    ): List<TrackingScrobbleFailure> {
        if (profileId != ProfileRepository.activeProfileId) return emptyList()
        TrackingProviderRegistry.ensureLoaded()
        val failures = dispatchTrackingSeekScrobble(
            scrobblers = TrackingProviderRegistry.connectedScrobblers(),
            profileId = profileId,
            action = action,
            event = event,
        )
        failures.forEach { failure ->
            log.w(failure.cause) {
                "${failure.providerId.storageId} seek scrobble ${action.wireValue} failed"
            }
        }
        return failures
    }

    /**
     * Gives the connected trackers a chance to prepare the user before the player starts.
     *
     * A tracker that fails here must not stop the playback, so each one is isolated. The call
     * suspends until the tracker is done, which is what keeps the player closed while a question
     * is still open.
     */
    suspend fun preparePlayback(media: TrackingMediaReference, progressPercent: Double = 0.0) {
        TrackingProviderRegistry.ensureLoaded()
        for (scrobbler in TrackingProviderRegistry.connectedScrobblers()) {
            try {
                scrobbler.preparePlayback(media, progressPercent)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) {
                    "${scrobbler.providerId.storageId} preparePlayback failed"
                }
            }
        }
    }
}

internal suspend fun dispatchTrackingSeekScrobble(
    scrobblers: Collection<TrackingScrobbler>,
    profileId: Int,
    action: TrackingScrobbleAction,
    event: TrackingScrobbleEvent,
): List<TrackingScrobbleFailure> = dispatchTrackingScrobble(
    scrobblers = scrobblers.filter { scrobbler ->
        scrobbler.seekScrobblePolicy == TrackingSeekScrobblePolicy.STOP_AND_RESTART
    },
    profileId = profileId,
    action = action,
    event = event,
)

internal suspend fun dispatchTrackingScrobble(
    scrobblers: Collection<TrackingScrobbler>,
    profileId: Int,
    action: TrackingScrobbleAction,
    event: TrackingScrobbleEvent,
): List<TrackingScrobbleFailure> = supervisorScope {
    scrobblers
        // A playback no session owns goes only to the trackers that keep a watching state of their
        // own: for Trakt a pause is a stop and a stop above the bar records a watch, so such an event
        // would write a viewing that never happened.
        .filter { scrobbler -> !event.sessionless || scrobbler.acceptsSessionlessScrobbles }
        .map { scrobbler ->
        async {
            try {
                scrobbler.scrobble(profileId = profileId, action = action, event = event)
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                TrackingScrobbleFailure(providerId = scrobbler.providerId, cause = error)
            }
        }
    }.awaitAll().filterNotNull()
}
