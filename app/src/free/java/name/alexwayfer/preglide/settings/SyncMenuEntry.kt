package name.alexwayfer.preglide.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import name.alexwayfer.preglide.R

/**
 * The menu row of the sync, which comes with Premium: a lock where the arrow would be, and a tap opens the page that
 * offers Premium, by [onOpen].
 */
@Composable
fun SyncMenuEntry(onOpen: (SettingsPage) -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(R.drawable.ic_sync), contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.settings_sync)) },
        supportingContent = { Text(stringResource(R.string.premium_locked)) },
        // The line under the title already says it; TalkBack reads it once.
        trailingContent = {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, Modifier.clearAndSetSemantics {})
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(SettingsPage.Premium) },
    )
}
