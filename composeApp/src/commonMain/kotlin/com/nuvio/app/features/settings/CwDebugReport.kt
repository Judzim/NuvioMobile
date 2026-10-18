package com.nuvio.app.features.settings

import com.nuvio.app.features.home.buildHomeNextUpSeedCandidates
import com.nuvio.app.features.simkl.SimklAuthRepository
import com.nuvio.app.features.simkl.SimklSyncRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.watching.application.WatchingState

/**
 * Debug-only dump of what the Continue Watching pipeline compared on this device: the watched
 * items as stored (raw timestamps), the rewatch runs read from the account, and the completed
 * position before and after the runs were applied. Copied from Advanced settings; nothing in the
 * app reads this at runtime.
 */
internal fun buildCwDebugReport(): String {
    val watched = WatchedRepository.uiState.value
    val progress = WatchProgressRepository.uiState.value
    val runs = SimklSyncRepository.state.value.snapshot.rewatchRuns
    val prefs = ContinueWatchingPreferencesRepository.uiState.value
    val ownsHistory = WatchProgressRepository.activeProviderOwnsCompletedHistoryProjection()
    val now = WatchProgressClock.nowEpochMs()
    val watchedItems = if (ownsHistory) emptyList() else watched.items

    val before = WatchingState.latestCompletedBySeries(
        progressEntries = progress.entries,
        watchedItems = watchedItems,
        preferFurthestEpisode = prefs.upNextFromFurthestEpisode,
    )
    val after = buildHomeNextUpSeedCandidates(
        progressEntries = progress.entries,
        watchedItems = watchedItems,
        providerOwnsCompletedHistory = ownsHistory,
        preferFurthestEpisode = prefs.upNextFromFurthestEpisode,
        nowEpochMs = now,
        shouldUseProgressSeed = WatchProgressRepository::shouldUseAsNextUpSeed,
        isContentHidden = { contentId ->
            contentId in progress.hiddenContentIds || WatchProgressRepository.isDroppedShow(contentId)
        },
        simklRewatchRuns = runs,
    )

    val itemsBySeries = watchedItems
        .filter { item -> item.season != null && item.episode != null }
        .groupBy { item -> item.id }
    val progressBySeries = progress.entries
        .filter { entry -> entry.seasonNumber != null && entry.episodeNumber != null }
        .groupBy { entry -> entry.parentMetaId }
    val beforeBySeries = before.entries.associate { (content, completed) -> content.id to completed }
    val afterBySeries = after.associate { candidate -> candidate.content.id to candidate }

    val seriesIds = (runs.map { run -> run.contentId } + itemsBySeries.keys + afterBySeries.keys)
        .toSortedSet()

    val out = StringBuilder()
    out.appendLine("=== CW DEBUG REPORT ===")
    out.appendLine(
        "source=${progress.source} ownsHistory=$ownsHistory preferFurthest=${prefs.upNextFromFurthestEpisode} " +
            "mode=${TrackingSettingsRepository.uiState.value.simklRewatchMode} " +
            "runs=${runs.size} sessions=${SimklSyncRepository.state.value.snapshot.rewatchSessions.size} " +
            "accountType=${SimklAuthRepository.uiState.value.accountType} " +
            "watchedItems=${watchedItems.size} progressEntries=${progress.entries.size} now=$now",
    )
    out.appendLine()
    for ((index, seriesId) in seriesIds.withIndex()) {
        val items = itemsBySeries[seriesId].orEmpty().sortedWith(
            compareBy({ item -> item.season ?: 0 }, { item -> item.episode ?: 0 }),
        )
        val seriesRuns = runs.filter { run ->
            run.contentId == seriesId || run.matchKeys.any { key -> key.equals(seriesId, ignoreCase = true) }
        }
        val entry = beforeBySeries[seriesId]
        val candidate = afterBySeries[seriesId]
        val progressRows = progressBySeries[seriesId].orEmpty()
        if (items.isEmpty() && seriesRuns.isEmpty() && entry == null && candidate == null) continue

        val name = items.firstOrNull()?.name ?: seriesId
        out.appendLine("[${index + 1}] $name  <$seriesId>")
        if (items.isEmpty()) {
            out.appendLine("    items: none")
        } else {
            val shown = if (items.size <= 12) items else items.take(6) + items.takeLast(6)
            val text = shown.joinToString(" ") { item ->
                "S${item.season}E${item.episode}@${item.markedAtEpochMs}"
            }
            out.appendLine("    items(${items.size}): $text")
        }
        for (run in seriesRuns) {
            out.appendLine(
                "    run: S${run.seasonNumber}E${run.episodeNumber}@${run.markedAtEpochMs} keys=${run.matchKeys.size}",
            )
        }
        entry?.let { completed ->
            out.appendLine(
                "    before: S${completed.seasonNumber}E${completed.episodeNumber}@${completed.markedAtEpochMs}",
            )
        } ?: out.appendLine("    before: none")
        candidate?.let { c ->
            out.appendLine("    after: S${c.seasonNumber}E${c.episodeNumber}@${c.markedAtEpochMs}")
        } ?: out.appendLine("    after: none")
        if (progressRows.isNotEmpty()) {
            val rows = progressRows.joinToString(" ") { row ->
                "S${row.seasonNumber}E${row.episodeNumber}@${row.lastUpdatedEpochMs}"
            }
            out.appendLine("    progress(${progressRows.size}): $rows")
        }
    }
    return out.toString()
}
