package com.nuvio.app.features.tracking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.simkl.SimklRewatchConsentRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.rewatch_prompt_confirm
import nuvio.composeapp.generated.resources.rewatch_prompt_dismiss
import nuvio.composeapp.generated.resources.rewatch_prompt_title
import org.jetbrains.compose.resources.stringResource

/**
 * Shows the rewatch question above the rest of the app, in the same dialog the app uses for its
 * own consents. It is deliberately a host outside a screen: the playback that triggered it can
 * start on any screen.
 *
 * The answer is what the scrobble at the end of the playback writes from: a yes may open a run, a
 * no keeps the series quiet, and nothing is sent before the playback ends. The question does not
 * close itself: it waits until the user answers or dismisses it, because closing it on a timer
 * would start the playback without a decision.
 */
@Composable
fun RewatchPromptHost() {
    val question by SimklRewatchConsentRepository.question.collectAsStateWithLifecycle()
    if (question == null) return

    DialogSurface(
        onDismissRequest = SimklRewatchConsentRepository::dismiss,
        title = stringResource(Res.string.rewatch_prompt_title),
    ) {
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.rewatch_prompt_dismiss),
                onClick = SimklRewatchConsentRepository::decline,
            )
            DialogButton(
                text = stringResource(Res.string.rewatch_prompt_confirm),
                onClick = SimklRewatchConsentRepository::grant,
                style = DialogButtonStyle.Primary,
            )
        }
    }
}
