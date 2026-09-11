package com.nuvio.app.features.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.rewatch_prompt_confirm
import nuvio.composeapp.generated.resources.rewatch_prompt_dismiss
import nuvio.composeapp.generated.resources.rewatch_prompt_title
import org.jetbrains.compose.resources.stringResource

/** How long the question stays on screen before it counts as a no. */
private const val REWATCH_PROMPT_TIMEOUT_MS = 8_000L

/**
 * Shows the rewatch question above the rest of the app. It is deliberately a popup instead of part
 * of a screen: the playback that triggered it can end on any screen, and an unanswered question
 * must never block navigation or playback.
 */
@Composable
fun RewatchPromptHost() {
    val prompt by RewatchPromptRepository.prompt.collectAsStateWithLifecycle()
    val active = prompt ?: return
    val promptKey = "${active.media.stableKey}:${active.watchedAtEpochMs}"
    val scope = rememberCoroutineScope()

    LaunchedEffect(promptKey) {
        delay(REWATCH_PROMPT_TIMEOUT_MS)
        RewatchPromptRepository.dismiss()
    }

    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = RewatchPromptRepository::dismiss,
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.rewatch_prompt_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = RewatchPromptRepository::dismiss) {
                    Text(stringResource(Res.string.rewatch_prompt_dismiss))
                }
                TextButton(onClick = { scope.launch { RewatchPromptRepository.confirm() } }) {
                    Text(stringResource(Res.string.rewatch_prompt_confirm))
                }
            }
        }
    }
}
