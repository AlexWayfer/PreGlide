package name.alexwayfer.preglide.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import name.alexwayfer.preglide.R

/** Only actions enabled by the current settings appear in the guide. */
@Composable
internal fun GestureHelpPage(settings: KeyboardSettings, onBack: () -> Unit) {
    val space by settings.spaceGestures.collectAsStateWithLifecycle()
    val strip by settings.strip.collectAsStateWithLifecycle()
    val autocorrect by settings.autocorrect.collectAsStateWithLifecycle()
    val emojiKey by settings.layout.emojiKey.collectAsStateWithLifecycle()
    val typing by settings.glide.typing.collectAsStateWithLifecycle()
    val erasesWord by settings.glide.erasesWord.collectAsStateWithLifecycle()
    val bringsBackWords by settings.glide.bringsBackWords.collectAsStateWithLifecycle()
    val period by settings.punctuation.doubleSpacePeriod.collectAsStateWithLifecycle()
    val state = GestureHelpState(
        space, strip, autocorrect, emojiKey, GlideHelp(typing, erasesWord, bringsBackWords), doubleSpacePeriod = period,
    )
    HelpListPage(stringResource(R.string.settings_gestures), onBack) {
        GestureHelp(gestureTips(state))
    }
}

@Composable
internal fun GestureHelp(tips: List<GestureTip>) {
    AnimatedContent(tips, label = "gesture help") { shown ->
        Column {
            shown.forEach { tip ->
                ListItem(headlineContent = { Text(stringResource(tip.title)) },
                    supportingContent = { Text(stringResource(tip.text)) })
            }
        }
    }
}
