package name.alexwayfer.preglide.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import name.alexwayfer.preglide.R

internal fun NavGraphBuilder.helpPages(
    settings: KeyboardSettings,
    open: (SettingsPage) -> Unit,
    back: (SettingsPage) -> Unit,
) {
    composable(SettingsPage.Help.name) { HelpPage(open, onBack = { back(SettingsPage.Help) }) }
    composable(SettingsPage.GestureHelp.name) {
        GestureHelpPage(settings, onBack = { back(SettingsPage.GestureHelp) })
    }
    composable(SettingsPage.Licenses.name) { LicensesPage(onBack = { back(SettingsPage.Licenses) }) }
}

@Composable
internal fun HelpPage(onOpen: (SettingsPage) -> Unit, onBack: () -> Unit) {
    HelpListPage(stringResource(R.string.settings_help), onBack) {
        PageEntry(R.drawable.ic_gesture, stringResource(R.string.settings_gestures),
            stringResource(R.string.gesture_help_show), { onOpen(SettingsPage.GestureHelp) })
        PageEntry(R.drawable.ic_license, stringResource(R.string.settings_licenses),
            stringResource(R.string.settings_licenses_hint), { onOpen(SettingsPage.Licenses) })
    }
}

@Composable
internal fun HelpListPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    TryScaffold { padding ->
        Column(
            Modifier.fillMaxSize().scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) { PageHeader(title, onBack) }
            Column(content = content)
        }
    }
}
