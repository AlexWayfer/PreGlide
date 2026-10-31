package name.alexwayfer.preglide.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ui.theme.KeyFont
import name.alexwayfer.preglide.ui.theme.keyFontFamily

/**
 * The font of the keyboard's keys and suggestions: a row for each, its name and a sample in it, the chosen one
 * marked. The keyboard below takes a choice at once.
 */
@Composable
fun FontPage(settings: KeyboardSettings, onBack: () -> Unit) {
    val chosen by settings.keyFont.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    TryScaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The rows reach the screen edges, as list rows do; the title keeps off them.
            Column(Modifier.padding(horizontal = 16.dp)) {
                PageHeader(stringResource(R.string.settings_font), onBack)
            }
            Column(Modifier.selectableGroup()) {
                KeyFont.entries.forEach { font ->
                    FontRow(font, selected = font == chosen) { scope.launch { settings.setKeyFont(font) } }
                }
            }
        }
    }
}

/** A font's row: a radio button, which flows into its new state, the font's name and a sample, both in the font. */
@Composable
private fun FontRow(font: KeyFont, selected: Boolean, onClick: () -> Unit) {
    val family = remember(font) { keyFontFamily(font) }
    ListItem(
        // The row picks; the button only shows it, so a tap is not read twice.
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(fontTitle(font), fontFamily = family) },
        supportingContent = { Text(stringResource(R.string.font_sample), fontFamily = family) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
    )
}

/** The name of [font]: its own, or for the system's, words saying so. */
@Composable
fun fontTitle(font: KeyFont): String = font.title ?: stringResource(R.string.font_system)
