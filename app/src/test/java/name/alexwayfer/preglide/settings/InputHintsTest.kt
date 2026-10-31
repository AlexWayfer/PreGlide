package name.alexwayfer.preglide.settings

import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ime.GlideStrip
import name.alexwayfer.preglide.ime.SpaceGestures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputHintsTest {
    @Test
    fun aHintNamesWhatIsOnFromACapital() {
        val hint = switchesHint(listOf("свайп-набор", "курсор пробелом"), "Всё выключено")
        assertEquals("Свайп-набор, курсор пробелом", hint)
    }

    @Test
    fun aHintWithAllOffSaysSo() {
        assertEquals("All off", switchesHint(emptyList(), "All off"))
    }

    @Test
    fun theSuggestionsNameEachSwitchOnInOrder() {
        assertEquals(
            listOf(
                R.string.input_hint_strip,
                R.string.input_hint_prediction,
                R.string.input_hint_autocorrect,
                R.string.input_hint_screenshots,
            ),
            suggestionsOn(strip = true, prediction = true, autocorrect = true, screenshots = true),
        )
        assertEquals(
            listOf(R.string.input_hint_autocorrect),
            suggestionsOn(strip = false, prediction = false, autocorrect = true, screenshots = false),
        )
    }

    @Test
    fun theModelMattersForTheStripOrForGlides() {
        assertTrue(predictionMatters(strip = true, glides = false))
        assertTrue(predictionMatters(strip = false, glides = true))
        assertFalse(predictionMatters(strip = false, glides = false))
    }

    @Test
    fun theGesturesNameEachSwitchOnInOrder() {
        assertEquals(
            listOf(R.string.input_hint_glide, R.string.input_hint_space_language),
            gesturesOn(glide = true, SpaceGestures.SwipeLanguage),
        )
        assertEquals(listOf(R.string.input_hint_space_cursor), gesturesOn(glide = false, SpaceGestures.SwipeCursor))
        assertEquals(emptyList<Int>(), gesturesOn(glide = false, SpaceGestures.Off))
    }

    @Test
    fun eachSpaceBarChoiceHasANameAndAHintOfItsOwn() {
        val texts = SpaceGestures.entries.map(::spaceGesturesText)
        assertEquals(R.string.settings_space_language to R.string.settings_space_language_hint, texts.first())
        assertEquals(texts.size, texts.flatMap { listOf(it.first, it.second) }.toSet().size / 2)
    }

    @Test
    fun eachGlideStripChoiceHasANameAndAHintOfItsOwn() {
        val texts = GlideStrip.entries.map(::glideStripText)
        assertEquals(
            R.string.settings_glide_strip_glide_words to R.string.settings_glide_strip_glide_words_hint,
            texts.first(),
        )
        assertEquals(texts.size, texts.flatMap { listOf(it.first, it.second) }.toSet().size / 2)
    }
}
