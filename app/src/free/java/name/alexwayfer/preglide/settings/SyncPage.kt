package name.alexwayfer.preglide.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** The sync page, which the free edition does not have: it leads back at once, should anything open it. */
@Composable
fun SyncPage(exits: SyncPageExits) {
    LaunchedEffect(Unit) { exits.back() }
}
