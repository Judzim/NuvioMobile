package com.nuvio.app.features.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Remembers titles the user explicitly asked to rewatch from the details screen.
 *
 * Rewatch bookkeeping is opt-in on Simkl, so in manual mode the flag is only sent for the item the
 * user armed here. The intent is consumed by the next recorded watch of that title, is dropped when
 * the user disarms it, and never leaves the current session.
 */
object RewatchIntentRepository {
    private val _armedKeys = MutableStateFlow<Set<String>>(emptySet())
    val armedKeys: StateFlow<Set<String>> = _armedKeys.asStateFlow()

    fun isArmed(media: TrackingMediaReference): Boolean =
        rewatchIntentKey(media) in _armedKeys.value

    fun isArmedContent(contentId: String): Boolean =
        normalizeRewatchIntentKey(contentId) in _armedKeys.value

    fun armContent(contentId: String) {
        val key = normalizeRewatchIntentKey(contentId)
        if (key.isEmpty()) return
        _armedKeys.update { armed -> armed + key }
    }

    fun disarmContent(contentId: String) {
        val key = normalizeRewatchIntentKey(contentId)
        if (key.isEmpty()) return
        _armedKeys.update { armed -> armed - key }
    }

    fun toggleContent(contentId: String) {
        if (isArmedContent(contentId)) disarmContent(contentId) else armContent(contentId)
    }

    fun consume(media: TrackingMediaReference) {
        val key = rewatchIntentKey(media)
        if (key.isEmpty()) return
        _armedKeys.update { armed -> armed - key }
    }

    fun clear() {
        _armedKeys.value = emptySet()
    }
}

internal fun normalizeRewatchIntentKey(value: String): String = value.trim().lowercase()

/**
 * The details screen arms a title with its catalog id and the player reports the same catalog id
 * back, so one key covers both. The external-id key is only a fallback for media without a catalog
 * reference.
 *
 * Season and episode numbers are deliberately ignored: a rewatch intent applies to the whole title.
 */
internal fun rewatchIntentKey(media: TrackingMediaReference): String =
    normalizeRewatchIntentKey(
        media.catalog
            ?.contentId
            ?.takeIf { contentId -> contentId.isNotBlank() }
            ?: media.stableKey,
    )
