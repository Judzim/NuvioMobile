package com.nuvio.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.episodes_cd_rewatched
import nuvio.composeapp.generated.resources.episodes_cd_watched
import org.jetbrains.compose.resources.stringResource

@Composable
fun NuvioWatchedBadge(
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val palette = MaterialTheme.themePalette
    Box(
        modifier = modifier
            .size(NuvioTokens.Icon.md)
            .clip(tokens.shapes.avatar)
            .background(palette.accentBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(Res.string.episodes_cd_watched),
            tint = palette.onSecondary,
            // 14 dp matches the rewatch glyph's visual weight on the 20 dp circle.
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * The circular arrow badge beside the watched marker on an episode the running rewatch has
 * covered.
 *
 * It uses the same circle and accent brush as [NuvioWatchedBadge], so the two read as one status
 * pair. The refresh glyph is a vector, so it stays crisp at every badge size and does not depend on
 * the selected text font.
 */
@Composable
fun NuvioRewatchBadge(
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val palette = MaterialTheme.themePalette
    val description = stringResource(Res.string.episodes_cd_rewatched)
    Box(
        modifier = modifier
            .size(NuvioTokens.Icon.md)
            .clip(tokens.shapes.avatar)
            .background(palette.accentBrush())
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = palette.onSecondary,
            modifier = Modifier.size(NuvioTokens.Icon.sm),
        )
    }
}

@Composable
fun NuvioAnimatedWatchedBadge(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.alpha(alpha),
    ) {
        NuvioWatchedBadge()
    }
}

@Composable
fun BoxScope.NuvioPosterWatchedOverlay(
    isWatched: Boolean,
    modifier: Modifier = Modifier,
    padding: Dp = NuvioTokens.Space.s6,
) {
    NuvioAnimatedWatchedBadge(
        isVisible = isWatched,
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(padding),
    )
}
