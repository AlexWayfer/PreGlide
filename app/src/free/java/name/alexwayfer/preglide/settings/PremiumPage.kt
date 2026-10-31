package name.alexwayfer.preglide.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.premium.premiumPurchaseUrl
import name.alexwayfer.preglide.premium.simCountry

/**
 * The page that offers Premium, as CustomTV's Premium banner does, over the whole screen: premium's gold icon, what
 * Premium adds, and the purchase in the currency for the SIM's country, which leaves the page as Not now and back do.
 * The locked sync row opens it.
 */
@Composable
fun PremiumPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val corners = rememberScreenCorners()
    val angle by rememberPremiumGlowAngle()
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(colors.surface, colors.primary)
            .premiumGlow(corners, colors.primary) { angle },
        color = Color.Transparent,
        contentColor = colors.onSurface,
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            BoxWithConstraints(Modifier.weight(1f)) {
                // The title on top, the list in between: the room left splits between the gap under the title and the
                // gap above the buttons, which the empty last child keeps.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(top = 58.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LogoTitle(
                        R.drawable.ic_premium_foreground,
                        stringResource(R.string.premium_name),
                        MaterialTheme.typography.displaySmall,
                        Modifier
                            .padding(bottom = 24.dp)
                            .semantics(mergeDescendants = true) { heading() },
                    )
                    PremiumFeatures(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                    )
                    Spacer(Modifier)
                }
            }
            PremiumButtons(
                onUpgrade = {
                    links.openUri(premiumPurchaseUrl(simCountry(context)))
                    onBack()
                },
                onNotNow = onBack,
            )
        }
    }
}

/** PreType, the word completion on the computer that the same Premium syncs with. */
const val PRETYPE_URL = "https://pretype.alexwayfer.name"

/** The gap between a feature's icon and its text. */
private val FEATURE_ICON_GAP = 12.dp

/**
 * What Premium adds, each with a gold icon in place of a bullet, PreType's name leading to its site; then a line under
 * the texts that more is coming.
 */
@Composable
private fun PremiumFeatures(modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleMedium
    val iconSize = with(LocalDensity.current) { style.lineHeight.toDp() }
    val links = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val pretype = stringResource(R.string.premium_pretype)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PremiumFeature(
            R.drawable.ic_sync,
            linkedText(stringResource(R.string.premium_feature_sync, pretype), pretype, PRETYPE_URL, links),
            style,
            iconSize,
        )
        PremiumFeature(
            R.drawable.ic_lock,
            AnnotatedString(stringResource(R.string.premium_feature_private)),
            style,
            iconSize,
        )
        Text(
            stringResource(R.string.premium_more),
            Modifier.padding(start = iconSize + FEATURE_ICON_GAP),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = style,
        )
    }
}

/**
 * A feature after its [icon], [iconSize] as tall as a line of [style], so it stands by the first line of a wrapped
 * text.
 */
@Composable
private fun PremiumFeature(@DrawableRes icon: Int, text: AnnotatedString, style: TextStyle, iconSize: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(FEATURE_ICON_GAP)) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(iconSize), tint = PremiumGoldDeep)
        Text(text, style = style)
    }
}

/** [text] with its first [link] leading to [url] in [styles]; plain where the text does not name it. */
fun linkedText(text: String, link: String, url: String, styles: TextLinkStyles?): AnnotatedString {
    val at = text.indexOf(link)
    if (at < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, at))
        withLink(LinkAnnotation.Url(url, styles)) { append(link) }
        append(text.substring(at + link.length))
    }
}

/** The gold purchase button, [onUpgrade], and Not now, [onNotNow], under it. */
@Composable
private fun PremiumButtons(onUpgrade: () -> Unit, onNotNow: () -> Unit) {
    Column(
        // No top padding: the list above already keeps its gap from the buttons.
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onUpgrade,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .goldButtonFill(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = GoldButtonText),
            contentPadding = PaddingValues(horizontal = 40.dp),
        ) {
            Text(stringResource(R.string.premium_upgrade), style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onNotNow) {
            Text(stringResource(R.string.premium_not_now))
        }
    }
}
