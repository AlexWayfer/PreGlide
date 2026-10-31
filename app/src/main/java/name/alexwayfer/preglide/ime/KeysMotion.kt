package name.alexwayfer.preglide.ime

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page

/** How long the keys of a new language take to come in, and the old ones to leave. */
const val LANGUAGE_CHANGE_MS = 220

/** The share of their width the keys of a new language slide by as they come in behind a swipe. */
const val LANGUAGE_SLIDE_SHARE = 1f / 3

/**
 * What tells apart the keys that change with motion: each keypad and the letters with their symbols (see
 * [pageFadeKey]), and the letters of each language. A keypad types no language, so it stays as the language changes.
 */
data class KeysKey(val page: Page, val language: Language?)

/** What tells apart the keys of [layout] from those that change with motion: see [KeysKey]. */
fun keysKey(layout: KeyboardLayout): KeysKey =
    KeysKey(pageFadeKey(layout.page), layout.language.takeUnless { layout.page.keypad })

/**
 * The way the keys slide as [from] changes to [to], true for forward (see [languageEntryOffset]): the way the swipe
 * along the space bar went that brought the language, when [swiped]. Null when they fade instead: into a keypad or
 * out of it, or into a language that came otherwise.
 */
fun keysSlide(from: KeyboardLayout, to: KeyboardLayout, swiped: Boolean?): Boolean? =
    swiped.takeIf { pageFadeKey(from.page) == pageFadeKey(to.page) && from.language != to.language }

/**
 * How the keys change: they fade into each other without a [slide], and with one, the new ones slide in its way, a
 * share of their width, behind the finger, while the old ones leave the other way; both at once and the same pace.
 */
fun keysTransform(slide: Boolean?): ContentTransform {
    if (slide == null) return fadeIn() togetherWith fadeOut()
    val move = tween<IntOffset>(LANGUAGE_CHANGE_MS)
    val fade = tween<Float>(LANGUAGE_CHANGE_MS)
    val entry = { width: Int -> languageEntryOffset(slide, (width * LANGUAGE_SLIDE_SHARE).toInt()) }
    return (slideInHorizontally(move) { entry(it) } + fadeIn(fade)) togetherWith
        (slideOutHorizontally(move) { -entry(it) } + fadeOut(fade))
}
