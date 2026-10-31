package name.alexwayfer.preglide.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import name.alexwayfer.preglide.R

/**
 * Keeps a page off the status bar and the screen's cutouts by its scaffold's [padding], above and at the sides; not
 * below, where the page runs on under the navigation bar and what floats over its bottom, which it scrolls out from
 * under by [scrollingPage] or a list's own padding.
 */
fun Modifier.pagePaddingAbove(padding: PaddingValues, direction: LayoutDirection): Modifier = padding(
    start = padding.calculateStartPadding(direction),
    top = padding.calculateTopPadding(),
    end = padding.calculateEndPadding(direction),
)

/**
 * A page that scrolls under its scaffold's [padding]: off the status bar, and with the padding below as room to
 * scroll into, so its rows go on under the navigation bar and the try button rather than end in a cut line above
 * them.
 */
fun Modifier.scrollingPage(padding: PaddingValues, scroll: ScrollState, direction: LayoutDirection): Modifier =
    pagePaddingAbove(padding, direction)
        .verticalScroll(scroll)
        .padding(bottom = padding.calculateBottomPadding())

/** The top of a page opened from the first one: a back button and the page's [title]. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

/**
 * A row that turns a setting on or off: its [icon], if any, [title], a [hint] of what it does, and a switch. The
 * whole row toggles, as list rows do. A row not [enabled], whose setting another one makes moot, grays out and does
 * not toggle.
 */
@Composable
fun SwitchEntry(
    @DrawableRes icon: Int?,
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        // The row toggles; the switch only shows it, so a tap is not read twice.
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .grayedOut(enabled)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

/**
 * A row of a choice among a few: a radio button, [selected] or not, its [title], and a [hint] of what it does. The
 * whole row selects, as list rows do; one not [enabled] grays out and does not, as [SwitchEntry].
 */
@Composable
fun RadioEntry(title: String, hint: String, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    ListItem(
        // The row selects; the button only shows it, so a tap is not read twice.
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .grayedOut(enabled)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
    )
}

/** Material's look of a disabled control's content. */
private const val DISABLED_ALPHA = 0.38f

/** A row grayed out as it stops being [enabled], fading, and back. */
@Composable
private fun Modifier.grayedOut(enabled: Boolean): Modifier {
    val alpha by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, label = "enabled")
    return graphicsLayer { this.alpha = alpha }
}

/**
 * A row of the first page that does something at once, opening no page: its [icon], [title] and [hint]. A hint of
 * what is set now changes in place, so it fades.
 */
@Composable
fun ActionEntry(@DrawableRes icon: Int, title: String, hint: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = {
            AnimatedContent(hint, transitionSpec = { fadeIn() togetherWith fadeOut() }) { Text(it) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    )
}

/**
 * A row of the first page that opens another page: its [icon], [title], a [hint] of what is there, and an arrow.
 * While the page wants the user's attention, the icon is [badged] with a dot, Material's small badge for a status;
 * the hint says why. The badge changes in place, so it fades rather than jumps.
 */
@Composable
fun PageEntry(@DrawableRes icon: Int, title: String, hint: String, onOpen: () -> Unit, badged: Boolean = false) {
    PageEntry(
        leading = {
            BadgedBox(badge = {
                AnimatedVisibility(badged, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) { Badge() }
            }) {
                Icon(painterResource(icon), contentDescription = null)
            }
        },
        title,
        hint,
        onOpen,
    )
}

/**
 * A row that opens another page: what it shows [leading], as an icon or a picture of what is chosen there, its
 * [title], a [hint] of what is there, and an arrow. The hint changes in place, so it fades rather than jumps.
 */
@Composable
fun PageEntry(leading: @Composable () -> Unit, title: String, hint: String, onOpen: () -> Unit) {
    ListItem(
        leadingContent = leading,
        headlineContent = { Text(title) },
        supportingContent = {
            AnimatedContent(hint, transitionSpec = { fadeIn() togetherWith fadeOut() }) { Text(it) }
        },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen),
    )
}
