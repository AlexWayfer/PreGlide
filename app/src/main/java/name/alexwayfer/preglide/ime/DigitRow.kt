package name.alexwayfer.preglide.ime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.glide.Glide
import name.alexwayfer.preglide.engine.layout.Key
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import name.alexwayfer.preglide.engine.layout.digitRow

/** The keys of the digit row, once for every page that shows it. */
private val DigitKeys = digitRow()

/** A row of [keys] as the keyboard shows it, [height] tall; a [digits] row clips its keys while it grows or shrinks. */
internal data class ShownRow(val keys: List<Key>, val height: Dp, val digits: Boolean = false)

/**
 * How tall the keys are, [rows] rows of them and as much of the digit row as [look] shows: as tall on every page, so
 * the keyboard keeps its height as the pages switch, and the panels in their place take the same.
 */
internal fun keysHeight(look: KeyLook, rows: Int): Dp = look.height * (rows + look.digitRow)

/**
 * How tall the digit row above [page]'s keys is: as much of it as [look] shows on the letters, and none on the other
 * pages, which have digits of their own.
 */
internal fun digitRowHeight(look: KeyLook, page: Page): Dp =
    if (page == Page.Letters) look.height * look.digitRow else 0.dp

/**
 * How tall each of [page]'s [rows] rows is: the letters' as [look] has them, the other pages' stretched into the room
 * the digit row takes above the letters.
 */
internal fun pageRowHeight(look: KeyLook, page: Page, rows: Int): Dp =
    (keysHeight(look, rows) - digitRowHeight(look, page)) / rows

/**
 * The rows [layout] shows by [look]: its own, with the emoji key while any of it shows (see [emojiKeyRows]), under
 * the digit row on the letters while any of that shows.
 */
internal fun shownRows(layout: KeyboardLayout, look: KeyLook): List<ShownRow> {
    val digits = digitRowHeight(look, layout.page)
    val height = pageRowHeight(look, layout.page, layout.rows.size)
    val rows = emojiKeyRows(layout, look).map { ShownRow(it, height) }
    return if (digits > 0.dp) listOf(ShownRow(DigitKeys, digits, digits = true)) + rows else rows
}

/**
 * Where the letters lie in [keys], the keys' area in the window, their [rows] rows under as much of the digit row as
 * [look] shows: a glide is read on the letters alone, as its layout has no digit row.
 */
internal fun lettersBounds(keys: Rect, look: KeyLook, rows: Int): Rect =
    keys.copy(top = keys.top + keys.height * look.digitRow / (rows + look.digitRow))

/** The glide along [path], points in the window, over [language]'s letters, in [keys] as [lettersBounds] has them. */
internal fun lettersGlide(path: List<Offset>, keys: Rect, look: KeyLook, language: Language): Glide {
    val letters = KeyboardLayout.of(language, Page.Letters)
    return glideOf(path, lettersBounds(keys, look, letters.rows.size), letters)
}
