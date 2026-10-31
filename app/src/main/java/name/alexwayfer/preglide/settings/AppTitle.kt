package name.alexwayfer.preglide.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.R

/** The launcher icon's layers are drawn on a canvas this big. */
private const val ICON_CANVAS = 108f

/** The part of that canvas in the middle a launcher's mask shows. */
private const val ICON_VIEWPORT = 72f

/** The tile's corners, as a share of its side: a rounded square, as many launchers mask icons. */
private const val TILE_CORNER_PERCENT = 25

/** The icon's whole canvas for a [tile] of that side, which shows only its middle, as a launcher's mask does. */
fun logoCanvas(tile: Dp): Dp = tile * ICON_CANVAS / ICON_VIEWPORT

/**
 * The app's name on the first page, after a tile of the launcher icon: its keys and glide on its blue, as the home
 * screen shows it.
 */
@Composable
fun AppTitle(modifier: Modifier = Modifier) {
    LogoTitle(
        R.drawable.ic_launcher_foreground,
        stringResource(R.string.product_name),
        MaterialTheme.typography.headlineMedium,
        modifier,
    )
}

/**
 * A [name] after a tile of a launcher icon's [foreground] on the icon's blue, in [style]: the app's title, and the
 * free edition's Premium page with premium's gold icon.
 */
@Composable
fun LogoTitle(@DrawableRes foreground: Int, name: String, style: TextStyle, modifier: Modifier = Modifier) {
    // As tall as the name's line, in sp turned to dp, so it grows with the system's font size as the name does.
    val tile = with(LocalDensity.current) { style.lineHeight.toDp() }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(tile)
                .clip(RoundedCornerShape(TILE_CORNER_PERCENT))
                .background(colorResource(R.color.launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(logoCanvas(tile)),
            )
        }
        Text(name, style = style)
    }
}
