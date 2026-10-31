package name.alexwayfer.preglide.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.R

/** Who the app owes its data, libraries, icons and fonts to, under what license. */
@Composable
fun LicensesPage(onBack: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageHeader(stringResource(R.string.settings_licenses), onBack)

            Text(stringResource(R.string.licenses_data), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.licenses_data_intro), style = MaterialTheme.typography.bodyMedium)
            dataCredits.forEach { CreditView(it) }

            HorizontalDivider()
            Text(stringResource(R.string.licenses_software), style = MaterialTheme.typography.titleMedium)
            softwareCredits.forEach { CreditView(it) }

            HorizontalDivider()
            Text(stringResource(R.string.licenses_fonts), style = MaterialTheme.typography.titleMedium)
            fontCredits.forEach { CreditView(it) }

            HorizontalDivider()
            Text(stringResource(R.string.licenses_texts), style = MaterialTheme.typography.titleMedium)
            licenseTexts(dataCredits + softwareCredits + fontCredits).forEach { LicenseText(it) }
        }
    }
}

@Composable
private fun CreditView(credit: Credit) {
    val links = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Link(credit.name, MaterialTheme.typography.titleSmall) { links.openUri(credit.url) }
        Text(stringResource(credit.role), style = MaterialTheme.typography.bodyMedium)
        credit.license?.let { license ->
            Link(license.title, MaterialTheme.typography.bodySmall) { links.openUri(license.url) }
        }
        credit.notice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic))
        }
    }
}

/** A link, underlined as well as colored: a palette's accent may be as gray as the text. */
@Composable
private fun Link(text: String, style: TextStyle, onClick: () -> Unit) {
    Text(
        text,
        style = style.copy(textDecoration = TextDecoration.Underline),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * The text of a [license] the app carries, as the license asks: a row with its title and an arrow, which unfolds the
 * text and turns over as it does, unlike a heading or a link.
 */
@Composable
private fun LicenseText(license: License) {
    val resources = LocalResources.current
    val text = remember(license) {
        license.text?.let { id -> resources.openRawResource(id).bufferedReader().use { it.readText() } }.orEmpty()
    }
    var shown by rememberSaveable(license) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (shown) HALF_TURN else 0f)
    val action = stringResource(if (shown) R.string.licenses_hide_text else R.string.licenses_show_text)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = action) { shown = !shown }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(license.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, Modifier.rotate(turn))
        }
        AnimatedVisibility(shown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

private const val HALF_TURN = 180f
